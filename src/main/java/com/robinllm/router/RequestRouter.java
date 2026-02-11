package com.robinllm.router;

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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

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
            Stream<OpenAIChatStreamResponse> responseStream = sendStreamRequestWithRetry(selectedModel, request);
            
            // Record success after the first chunk is received
            responseStream = responseStream.onClose(() -> {
                long latency = System.currentTimeMillis() - startTime;
                loadBalancer.recordSuccess(selectedModel, latency);
                LOG.info("Stream request completed via model {} in {}ms", selectedModel.getId(), latency);
            });

            // Set the model ID for each chunk
            responseStream = responseStream.peek(chunk -> {
                chunk.setModel(selectedModel.getId());
            });

            return responseStream;

        } catch (Exception e) {
            totalFailures.incrementAndGet();
            LOG.error("Failed to route stream request: {}", e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }
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

    private Stream<OpenAIChatStreamResponse> sendStreamRequestWithRetry(LLMModel model, OpenAIChatRequest request) {
        int maxRetries = appConfig.getRouterRetryMax();
        long backoffMs = appConfig.getRouterRetryBackoff();
        Exception lastException = null;

        OpenAIChatRequest translatedRequest = translateToModelFormat(request, model);

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                Stream<OpenAIChatStreamResponse> responseStream = sendStreamRequest(model, translatedRequest);
                return responseStream;

            } catch (Exception e) {
                lastException = e;
                loadBalancer.recordFailure(model);
                LOG.warn("Stream request failed (attempt {}/{}): {}", attempt, maxRetries, e.getMessage());

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
        throw new RuntimeException("Failed to route stream request after " + maxRetries + " retries", lastException);
    }

    private Stream<OpenAIChatStreamResponse> sendStreamRequest(LLMModel model, OpenAIChatRequest request) throws Exception {
        // For now, we only support OpenRouter, so we can directly use the OpenRouter client
        // In the future, we can extend this to support multiple providers through the client factory
        var client = (OpenRouterClient) clientFactory.getClient(model);
        return client.sendChatRequestStream(request);
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
