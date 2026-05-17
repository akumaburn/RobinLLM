package com.robinllm.client;

import com.robinllm.config.AppConfig;
import com.robinllm.dto.OpenAIChatRequest;
import com.robinllm.dto.OpenAIChatResponse;
import com.robinllm.dto.OpenAIChatStreamResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Iterator;
import java.util.function.Consumer;

@ApplicationScoped
public class OpenRouterClient implements LLMClient {
    private static final Logger LOG = LoggerFactory.getLogger(OpenRouterClient.class);

    @Inject
    AppConfig appConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OkHttpClient httpClient;
    private final ConcurrentHashMap<String, AtomicInteger> rateLimitCounters = new ConcurrentHashMap<>();

    @Override
    public OpenAIChatResponse sendChatRequest(OpenAIChatRequest request) throws Exception {
        return sendChatRequestWithRetry(request, 0);
    }

    @Override
    public java.util.stream.Stream<OpenAIChatStreamResponse> sendChatRequestStream(OpenAIChatRequest request) throws Exception {
        CancellableStream cancellable = sendChatRequestStreamCancellable(request);
        return cancellable.getStream();
    }

    /**
     * Send a streaming request and return a CancellableStream that can be used to
     * cancel the request and close resources.
     */
    public CancellableStream sendChatRequestStreamCancellable(OpenAIChatRequest request) throws Exception {
        return sendChatRequestStreamCancellableWithRetry(request, 0, null);
    }

    /**
     * Variant that lets a caller observe each underlying okhttp Call the instant
     * it's constructed, before execute() blocks. The registrar is invoked once
     * per attempt (including retries), so a race coordinator can cancel the Call
     * mid-execute() to abort an in-flight HTTP connection. If the registrar
     * cancels the Call, retries are skipped.
     */
    public CancellableStream sendChatRequestStreamCancellable(OpenAIChatRequest request,
                                                              Consumer<Call> callRegistrar) throws Exception {
        return sendChatRequestStreamCancellableWithRetry(request, 0, callRegistrar);
    }

    private OpenAIChatResponse sendChatRequestWithRetry(OpenAIChatRequest request, int attempt) throws Exception {
        String apiKey = appConfig.getOpenrouterApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new Exception("OpenRouter API key not configured");
        }

        String baseUrl = appConfig.getOpenrouterBaseUrl();
        String url = baseUrl + "/chat/completions";

        String jsonBody = objectMapper.writeValueAsString(request);
        LOG.debug("Sending non-streaming request to {} with body: {}", url, jsonBody);
        RequestBody body = RequestBody.create(okhttp3.MediaType.parse("application/json"), jsonBody);

        Request httpRequest = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://openrouter.ai")
                .post(body)
                .build();

        try (Response response = getHttpClient().newCall(httpRequest).execute()) {
            int responseCode = response.code();
            String responseBodyStr = response.body() != null ? response.body().string() : null;
            
            // Handle rate limiting
            if (responseCode == 429) {
                handleRateLimit(request.getModel());
                long backoffMs = (long) (1000 * Math.pow(2, attempt));
                long maxBackoff = 30000;
                backoffMs = Math.min(backoffMs, maxBackoff);

                LOG.warn("Rate limit hit for model {}, backing off for {}ms (attempt {})",
                        request.getModel(), backoffMs, attempt + 1);

                Thread.sleep(backoffMs);

                if (attempt < 5) {
                    return sendChatRequestWithRetry(request, attempt + 1);
                } else {
                    throw new Exception("Rate limit exceeded after " + (attempt + 1) + " retries");
                }
            }

            if (!response.isSuccessful()) {
                String errorBody = responseBodyStr != null ? responseBodyStr : "No error details";
                throw new Exception("Request failed with code " + responseCode + ": " + errorBody);
            }

            if (responseBodyStr == null || responseBodyStr.isEmpty()) {
                throw new Exception("Empty response from OpenRouter");
            }

            return objectMapper.readValue(responseBodyStr, OpenAIChatResponse.class);

        } catch (IOException e) {
            if (attempt < 3) {
                long backoffMs = (long) (1000 * Math.pow(2, attempt));
                LOG.warn("Request failed for model {}, retrying in {}ms (attempt {}): {}",
                        request.getModel(), backoffMs, attempt + 1, e.getMessage());
                Thread.sleep(backoffMs);
                return sendChatRequestWithRetry(request, attempt + 1);
            }
            throw new Exception("Failed to send request after " + (attempt + 1) + " retries: " + e.getMessage(), e);
        }
    }

    private CancellableStream sendChatRequestStreamCancellableWithRetry(OpenAIChatRequest request,
                                                                        int attempt,
                                                                        Consumer<Call> callRegistrar) throws Exception {
        String apiKey = appConfig.getOpenrouterApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new Exception("OpenRouter API key not configured");
        }

        String baseUrl = appConfig.getOpenrouterBaseUrl();
        String url = baseUrl + "/chat/completions";

        String jsonBody = objectMapper.writeValueAsString(request);
        LOG.debug("Sending streaming request to {} with body: {}", url, jsonBody);
        RequestBody body = RequestBody.create(okhttp3.MediaType.parse("application/json"), jsonBody);

        Request httpRequest = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .addHeader("HTTP-Referer", "https://openrouter.ai")
                .post(body)
                .build();

        Call call = getHttpClient().newCall(httpRequest);

        // Hand the Call to the registrar BEFORE we block on execute(), so an
        // external race coordinator can cancel it mid-flight.
        if (callRegistrar != null) {
            callRegistrar.accept(call);
        }

        // Belt-and-suspenders: if the registrar pre-cancelled us (because
        // another model has already won the race), abort immediately without
        // touching the network. Don't rely on okhttp honoring cancel() before
        // execute() - older 3.x versions don't always check the cancel flag
        // when reusing a pooled connection.
        if (call.isCanceled()) {
            LOG.info("Aborting stream request for model {} - cancelled before execute", request.getModel());
            throw new IOException("Cancelled by race coordinator");
        }

        Response response;
        try {
            response = call.execute();
        } catch (IOException e) {
            // If our Call was externally cancelled (lost the race), do not retry.
            if (call.isCanceled()) {
                throw new IOException("Cancelled by race coordinator", e);
            }
            if (attempt < 3) {
                long backoffMs = (long) (1000 * Math.pow(2, attempt));
                LOG.warn("Stream request failed for model {}, retrying in {}ms (attempt {}): {}",
                        request.getModel(), backoffMs, attempt + 1, e.getMessage());
                cancellableSleep(call, backoffMs);
                return sendChatRequestStreamCancellableWithRetry(request, attempt + 1, callRegistrar);
            }
            throw e;
        }

        try {
            int responseCode = response.code();

            // Handle rate limiting
            if (responseCode == 429) {
                handleRateLimit(request.getModel());
                long backoffMs = (long) (1000 * Math.pow(2, attempt));
                long maxBackoff = 30000;
                backoffMs = Math.min(backoffMs, maxBackoff);

                LOG.warn("Rate limit hit for model {}, backing off for {}ms (attempt {})",
                        request.getModel(), backoffMs, attempt + 1);

                // Release the connection BEFORE sleeping so we don't pin a
                // pooled connection during the backoff.
                response.close();

                // Sleep cancellably so a losing worker stops waiting the
                // moment the race coordinator cancels its Call, instead of
                // burning through the full backoff and another rate-limit
                // round-trip.
                cancellableSleep(call, backoffMs);

                if (attempt < 5) {
                    return sendChatRequestStreamCancellableWithRetry(request, attempt + 1, callRegistrar);
                } else {
                    throw new Exception("Rate limit exceeded after " + (attempt + 1) + " retries");
                }
            }

            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error details";
                response.close();
                throw new Exception("Request failed with code " + responseCode + ": " + errorBody);
            }

            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                response.close();
                throw new Exception("Empty response body from OpenRouter");
            }

            InputStream inputStream = responseBody.byteStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            // Create an iterator for SSE events
            Iterator<OpenAIChatStreamResponse> iterator = createIterator(reader);

            // Return a cancellable stream that wraps everything
            return new CancellableStream(call, response, reader, iterator, request.getModel());

        } catch (Exception e) {
            response.close();
            if (e instanceof IOException && !call.isCanceled() && attempt < 3) {
                long backoffMs = (long) (1000 * Math.pow(2, attempt));
                LOG.warn("Stream request failed for model {}, retrying in {}ms (attempt {}): {}",
                        request.getModel(), backoffMs, attempt + 1, e.getMessage());
                cancellableSleep(call, backoffMs);
                return sendChatRequestStreamCancellableWithRetry(request, attempt + 1, callRegistrar);
            }
            throw e;
        }
    }

    /**
     * Sleep for {@code totalMs} but wake up within ~100ms if the given Call
     * gets cancelled externally. Throws IOException("Cancelled by race
     * coordinator") if the Call was cancelled during (or before) the sleep.
     */
    private void cancellableSleep(Call call, long totalMs) throws InterruptedException, IOException {
        final long pollIntervalMs = 100;
        long deadline = System.currentTimeMillis() + totalMs;
        while (true) {
            if (call.isCanceled()) {
                throw new IOException("Cancelled by race coordinator");
            }
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                break;
            }
            Thread.sleep(Math.min(pollIntervalMs, remaining));
        }
        if (call.isCanceled()) {
            throw new IOException("Cancelled by race coordinator");
        }
    }

    private Iterator<OpenAIChatStreamResponse> createIterator(BufferedReader reader) {
        return new Iterator<>() {
            private OpenAIChatStreamResponse nextChunk = null;
            private boolean done = false;

            @Override
            public boolean hasNext() {
                if (done) return false;
                if (nextChunk != null) return true;
                
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        LOG.debug("Received SSE line: {}", line);
                        
                        // Handle empty lines (keep-alive)
                        if (line.isEmpty()) continue;
                        
                        // Handle SSE data lines
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            
                            // Check for stream end
                            if (data.equals("[DONE]")) {
                                done = true;
                                return false;
                            }
                            
                            try {
                                nextChunk = objectMapper.readValue(data, OpenAIChatStreamResponse.class);
                                return true;
                            } catch (Exception e) {
                                LOG.error("Error parsing stream data: {}", data, e);
                                continue;
                            }
                        }
                    }
                    
                    // End of stream
                    done = true;
                    return false;
                } catch (IOException e) {
                    LOG.error("Error reading stream", e);
                    done = true;
                    return false;
                }
            }

            @Override
            public OpenAIChatStreamResponse next() {
                if (nextChunk == null && !hasNext()) {
                    throw new java.util.NoSuchElementException();
                }
                OpenAIChatStreamResponse chunk = nextChunk;
                nextChunk = null;
                return chunk;
            }
        };
    }

    private void handleRateLimit(String modelId) {
        rateLimitCounters.computeIfAbsent(modelId, k -> new AtomicInteger(0)).incrementAndGet();
        LOG.warn("Rate limit hit for model: {}", modelId);
    }

    public int getRateLimitCount(String modelId) {
        AtomicInteger counter = rateLimitCounters.get(modelId);
        return counter != null ? counter.get() : 0;
    }

    public void resetRateLimiters() {
        rateLimitCounters.clear();
        LOG.info("Rate limit counters reset");
    }

    private synchronized OkHttpClient getHttpClient() {
        if (httpClient == null) {
            long timeout = appConfig.getApiTimeout();
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for streaming
                    .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                    .connectionPool(new okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
                    .retryOnConnectionFailure(true)
                    .build();
        }
        return httpClient;
    }
}
