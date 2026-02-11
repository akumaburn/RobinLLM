package com.robinllm.router;

import com.robinllm.client.CancellableStream;
import com.robinllm.client.LLMClientFactory;
import com.robinllm.client.OpenRouterClient;
import com.robinllm.config.AppConfig;
import com.robinllm.dto.OpenAIChatRequest;
import com.robinllm.dto.OpenAIChatResponse;
import com.robinllm.dto.OpenAIChatStreamResponse;
import com.robinllm.model.LLMModel;
import com.robinllm.model.ModelPool;
import com.robinllm.repository.ModelRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.Spliterators;
import java.util.Spliterator;

@ApplicationScoped
public class RequestRouter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestRouter.class);

    @Inject
    AppConfig appConfig;

    @Inject
    ModelPool modelPool;

    @Inject
    ModelRepository modelRepository;

    @Inject
    ModelSelector modelSelector;

    @Inject
    LoadBalancer loadBalancer;

    @Inject
    LLMClientFactory clientFactory;

    private final AtomicInteger totalRequests = new AtomicInteger(0);
    private final AtomicInteger totalFailures = new AtomicInteger(0);

    // Timeout for first chunk in parallel racing (10 seconds)
    private static final long FIRST_CHUNK_TIMEOUT_SECONDS = 10;

    public OpenAIChatResponse routeRequest(OpenAIChatRequest request) {
        totalRequests.incrementAndGet();

        String requestedModel = request.getModel();
        LLMModel selectedModel;

        try {
            if ("auto".equals(requestedModel) || requestedModel == null || requestedModel.isEmpty()) {
                List<LLMModel> freeModels = modelPool.getFreeModels();
                if (freeModels.isEmpty()) {
                    throw new RuntimeException("No free models available");
                }
                selectedModel = selectBestModel(freeModels);
            } else {
                selectedModel = modelPool.getModel(requestedModel);
            }

            if (selectedModel == null) {
                throw new RuntimeException("No available model found");
            }

            long startTime = System.currentTimeMillis();
            OpenAIChatResponse response = sendRequestWithRetry(selectedModel, request);
            long latency = System.currentTimeMillis() - startTime;

            loadBalancer.recordSuccess(selectedModel, latency);

            response.setModel(selectedModel.getId());
            if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                response.getChoices().get(0).setFinishReason("stop");
            }

            LOG.info("Request completed via model {} in {}ms", selectedModel.getId(), latency);

            return response;

        } catch (Exception e) {
            totalFailures.incrementAndGet();
            LOG.error("Failed to route request: {}", e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public Stream<OpenAIChatStreamResponse> routeRequestStream(OpenAIChatRequest request) {
        totalRequests.incrementAndGet();

        String requestedModel = request.getModel();

        try {
            List<LLMModel> modelsToTry;
            
            if ("auto".equals(requestedModel) || requestedModel == null || requestedModel.isEmpty()) {
                // For auto mode, use parallel racing with 3 models
                modelsToTry = selectMultipleModels(3);
                if (modelsToTry.isEmpty()) {
                    throw new RuntimeException("No free models available");
                }
                return routeStreamWithParallelRace(request, modelsToTry);
            } else {
                // For specific model request, use that model only
                LLMModel selectedModel = modelPool.getModel(requestedModel);
                if (selectedModel == null) {
                    throw new RuntimeException("Requested model not found: " + requestedModel);
                }
                modelsToTry = List.of(selectedModel);
                return routeStreamWithParallelRace(request, modelsToTry);
            }

        } catch (Exception e) {
            totalFailures.incrementAndGet();
            LOG.error("Failed to route stream request: {}", e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Routes a streaming request using parallel racing across multiple models.
     * The first model to return a chunk wins, others are cancelled.
     */
    private Stream<OpenAIChatStreamResponse> routeStreamWithParallelRace(OpenAIChatRequest request, List<LLMModel> models) {
        if (models.size() == 1) {
            // Single model - no racing needed
            return sendStreamRequestSingle(models.get(0), request);
        }

        LOG.info("Starting parallel race with {} models: {}", 
                models.size(), 
                models.stream().map(LLMModel::getId).toList());

        long startTime = System.currentTimeMillis();
        List<CancellableStream> activeStreams = new ArrayList<>();
        AtomicReference<LLMModel> winningModel = new AtomicReference<>();
        
        try {
            // Start all requests concurrently
            List<CompletableFuture<RaceResult>> futures = new ArrayList<>();
            
            for (LLMModel model : models) {
                OpenAIChatRequest translatedRequest = translateToModelFormat(request, model);
                
                CompletableFuture<RaceResult> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        LOG.debug("Starting stream request for model: {}", model.getId());
                        CancellableStream stream = sendStreamRequestCancellable(model, translatedRequest);
                        
                        synchronized (activeStreams) {
                            activeStreams.add(stream);
                        }
                        
                        // Wait for first chunk with timeout
                        return raceForFirstChunk(stream, model);
                    } catch (Exception e) {
                        LOG.warn("Stream request failed for model {}: {}", model.getId(), e.getMessage());
                        return new RaceResult(null, null, model, e);
                    }
                });
                
                futures.add(future);
            }

            // Race the futures - first to complete wins
            CompletableFuture<Object> raceFuture = CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0]));
            
            RaceResult winner;
            try {
                winner = (RaceResult) raceFuture.get(FIRST_CHUNK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                LOG.error("All parallel stream requests timed out after {} seconds", FIRST_CHUNK_TIMEOUT_SECONDS);
                cancelAllStreams(activeStreams);
                throw new RuntimeException("All models timed out after " + FIRST_CHUNK_TIMEOUT_SECONDS + " seconds");
            }

            if (winner == null || winner.firstChunk() == null) {
                LOG.error("All parallel stream requests failed");
                cancelAllStreams(activeStreams);
                throw new RuntimeException("All models failed to respond");
            }

            // We have a winner!
            winningModel.set(winner.model());
            LOG.info("Model {} won the race ({}ms)", winner.model().getId(), System.currentTimeMillis() - startTime);

            // Cancel all other streams except the winner
            int cancelledCount = 0;
            for (CancellableStream stream : activeStreams) {
                if (stream != winner.stream() && !stream.isCancelled()) {
                    try {
                        stream.close();
                        cancelledCount++;
                    } catch (Exception e) {
                        LOG.debug("Error cancelling stream: {}", e.getMessage());
                    }
                }
            }
            LOG.debug("Cancelled {} other streams", cancelledCount);

            // Record success for the winning model
            loadBalancer.recordSuccess(winner.model(), System.currentTimeMillis() - startTime);

            // Create a new stream that starts with the first chunk, then continues with the rest
            Stream<OpenAIChatStreamResponse> winningStream = createWinningStream(
                    winner.firstChunk(), 
                    winner.stream(), 
                    winner.model()
            );

            return winningStream.onClose(() -> {
                LOG.info("Stream completed via model {} in {}ms", winner.model().getId(), System.currentTimeMillis() - startTime);
                winner.stream().close();
            });

        } catch (Exception e) {
            cancelAllStreams(activeStreams);
            totalFailures.incrementAndGet();
            throw new RuntimeException("Parallel stream routing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Waits for the first chunk from a stream with a timeout.
     * Returns a RaceResult containing the first chunk if successful.
     */
    private RaceResult raceForFirstChunk(CancellableStream stream, LLMModel model) {
        try {
            // Get the raw iterator from the stream - don't use getStream() as it creates a new stream
            Iterator<OpenAIChatStreamResponse> iterator = stream.getIterator();
            
            // Try to get the first chunk with timeout using a separate thread
            CompletableFuture<OpenAIChatStreamResponse> firstChunkFuture = CompletableFuture.supplyAsync(() -> {
                if (iterator.hasNext()) {
                    return iterator.next();
                }
                return null;
            });

            OpenAIChatStreamResponse firstChunk = firstChunkFuture.get(FIRST_CHUNK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            if (firstChunk != null) {
                LOG.debug("Model {} returned first chunk successfully", model.getId());
                return new RaceResult(stream, firstChunk, model, null);
            } else {
                LOG.warn("Model {} stream ended without any chunks", model.getId());
                stream.close();
                return new RaceResult(null, null, model, new RuntimeException("No chunks received"));
            }
            
        } catch (TimeoutException e) {
            LOG.warn("Model {} timed out waiting for first chunk ({}s)", model.getId(), FIRST_CHUNK_TIMEOUT_SECONDS);
            stream.close();
            return new RaceResult(null, null, model, e);
        } catch (Exception e) {
            LOG.warn("Model {} failed to return first chunk: {}", model.getId(), e.getMessage());
            stream.close();
            return new RaceResult(null, null, model, e);
        }
    }

    /**
     * Creates a new stream that starts with the first chunk, then continues with the rest from the iterator.
     */
    private Stream<OpenAIChatStreamResponse> createWinningStream(
            OpenAIChatStreamResponse firstChunk,
            CancellableStream winningStream,
            LLMModel model) {
        
        // Get the iterator which will continue from where the race left off
        Iterator<OpenAIChatStreamResponse> iterator = winningStream.getIterator();
        
        return Stream.concat(
            Stream.of(firstChunk),
            StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
                false
            )
        ).onClose(winningStream::close);
    }

    private void cancelAllStreams(List<CancellableStream> streams) {
        for (CancellableStream stream : streams) {
            try {
                stream.close();
            } catch (Exception e) {
                LOG.debug("Error cancelling stream: {}", e.getMessage());
            }
        }
    }

    private Stream<OpenAIChatStreamResponse> sendStreamRequestSingle(LLMModel model, OpenAIChatRequest request) {
        LOG.debug("Sending single stream request to model: {}", model.getId());
        long startTime = System.currentTimeMillis();
        
        try {
            CancellableStream stream = sendStreamRequestCancellable(model, request);
            
            return stream.getStream().onClose(() -> {
                long latency = System.currentTimeMillis() - startTime;
                loadBalancer.recordSuccess(model, latency);
                LOG.info("Stream request completed via model {} in {}ms", model.getId(), latency);
                stream.close();
            });
        } catch (Exception e) {
            loadBalancer.recordFailure(model);
            throw new RuntimeException("Stream request failed for model " + model.getId() + ": " + e.getMessage(), e);
        }
    }

    private record RaceResult(CancellableStream stream, OpenAIChatStreamResponse firstChunk, LLMModel model, Exception error) {}

    private List<LLMModel> selectMultipleModels(int count) {
        List<LLMModel> freeModels = new ArrayList<>(modelPool.getFreeModels());
        if (freeModels.isEmpty()) {
            return List.of();
        }

        List<LLMModel> selected = new ArrayList<>();
        List<LLMModel> bestModels = modelSelector.selectBestModels(freeModels);
        
        // Use load balancer to get diverse selections
        while (selected.size() < count && !bestModels.isEmpty()) {
            LLMModel model = loadBalancer.selectModel(bestModels);
            if (model != null && !selected.contains(model)) {
                selected.add(model);
                // Remove this model so we get different ones
                bestModels.remove(model);
            } else {
                break;
            }
        }

        return selected;
    }

    private LLMModel selectBestModel(List<LLMModel> models) {
        List<LLMModel> bestModels = modelSelector.selectBestModels(models);
        return loadBalancer.selectModel(bestModels);
    }

    private OpenAIChatResponse sendRequestWithRetry(LLMModel model, OpenAIChatRequest request) {
        int maxRetries = appConfig.getRouterRetryMax();
        long backoffMs = appConfig.getRouterRetryBackoff();
        Exception lastException = null;

        OpenAIChatRequest translatedRequest = translateToModelFormat(request, model);

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                OpenAIChatResponse response = sendRequest(model, translatedRequest);
                loadBalancer.recordSuccess(model, System.currentTimeMillis() - System.currentTimeMillis());
                return response;

            } catch (Exception e) {
                lastException = e;
                loadBalancer.recordFailure(model);
                LOG.warn("Request failed (attempt {}/{}): {}", attempt, maxRetries, e.getMessage());

                if (attempt < maxRetries) {
                    try {
                        long delay = backoffMs * (1L << attempt);
                        Thread.sleep(Math.min(delay, 30000));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                    LLMModel fallbackModel = selectFallbackModel(model);
                    if (fallbackModel != null && !fallbackModel.getId().equals(model.getId())) {
                        model = fallbackModel;
                        translatedRequest = translateToModelFormat(request, model);
                        LOG.info("Falling back to model {}", model.getId());
                    }
                }
            }
        }

        modelPool.updateModelStatus(model.getId(), "degraded");
        throw new RuntimeException("Failed to route request after " + maxRetries + " retries", lastException);
    }

    private OpenAIChatRequest translateToModelFormat(OpenAIChatRequest originalRequest, LLMModel model) {
        OpenAIChatRequest translated = new OpenAIChatRequest();
        translated.setModel(model.getId());
        translated.setMessages(originalRequest.getMessages());
        translated.setTemperature(originalRequest.getTemperature());
        translated.setMaxTokens(originalRequest.getMaxTokens());
        translated.setTopP(originalRequest.getTopP());
        translated.setStop(originalRequest.getStop());
        translated.setStream(originalRequest.isStream());

        int modelMaxTokens = model.getMaxTokens();
        
        if (modelMaxTokens > 0) {
            // Always use model's max tokens if not specified in request
            if (translated.getMaxTokens() == null) {
                translated.setMaxTokens(modelMaxTokens);
            } else {
                // If request specifies maxTokens, ensure it doesn't exceed model limit
                translated.setMaxTokens(Math.min(translated.getMaxTokens(), modelMaxTokens));
            }
        }

        if (model.getContextWindow() > 0 && translated.getMaxTokens() == null) {
            // If context window is available and maxTokens not set, use context window
            translated.setMaxTokens(model.getContextWindow());
        }

        return translated;
    }

    private OpenAIChatResponse sendRequest(LLMModel model, OpenAIChatRequest request) throws Exception {
        var client = clientFactory.getClient(model);
        return client.sendChatRequest(request);
    }

    private CancellableStream sendStreamRequestCancellable(LLMModel model, OpenAIChatRequest request) throws Exception {
        var client = (OpenRouterClient) clientFactory.getClient(model);
        return client.sendChatRequestStreamCancellable(request);
    }

    private LLMModel selectFallbackModel(LLMModel failedModel) {
        List<LLMModel> freeModels = modelPool.getFreeModels();
        freeModels.remove(failedModel);

        if (freeModels.isEmpty()) {
            LOG.warn("No free models available for fallback after removing {}", failedModel.getId());
            return null;
        }

        List<LLMModel> bestModels = modelSelector.selectBestModels(freeModels);
        return loadBalancer.selectModel(bestModels);
    }

    public OpenAIChatResponse createErrorResponse(String message) {
        OpenAIChatResponse response = new OpenAIChatResponse();
        response.setId(UUID.randomUUID().toString());
        response.setObject("chat.completion");
        response.setCreated(Instant.now().getEpochSecond());
        response.setModel("error");

        OpenAIChatResponse.Choice choice = new OpenAIChatResponse.Choice();
        choice.setIndex(0);
        choice.setFinishReason("error");

        OpenAIChatResponse.Choice.Message msg = new OpenAIChatResponse.Choice.Message();
        msg.setRole("assistant");
        msg.setContent(message);
        choice.setMessage(msg);

        response.setChoices(List.of(choice));

        OpenAIChatResponse.Usage usage = new OpenAIChatResponse.Usage();
        usage.setPromptTokens(0);
        usage.setCompletionTokens(0);
        usage.setTotalTokens(0);
        response.setUsage(usage);

        return response;
    }

    public double getSuccessRate() {
        int total = totalRequests.get();
        int failures = totalFailures.get();
        return total == 0 ? 0 : (double) (total - failures) / total;
    }

    public int getTotalRequests() {
        return totalRequests.get();
    }

    public int getTotalFailures() {
        return totalFailures.get();
    }

    public void resetStats() {
        totalRequests.set(0);
        totalFailures.set(0);
    }
}
