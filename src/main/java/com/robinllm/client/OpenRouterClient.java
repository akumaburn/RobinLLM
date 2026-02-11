package com.robinllm.client;

import com.robinllm.config.AppConfig;
import com.robinllm.dto.OpenAIChatRequest;
import com.robinllm.dto.OpenAIChatResponse;
import com.robinllm.dto.OpenAIChatStreamResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.Iterator;

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
    public Stream<OpenAIChatStreamResponse> sendChatRequestStream(OpenAIChatRequest request) throws Exception {
        return sendChatRequestStreamWithRetry(request, 0);
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

    private Stream<OpenAIChatStreamResponse> sendChatRequestStreamWithRetry(OpenAIChatRequest request, int attempt) throws Exception {
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

        Response response = getHttpClient().newCall(httpRequest).execute();
        
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

                Thread.sleep(backoffMs);

                if (attempt < 5) {
                    response.close();
                    return sendChatRequestStreamWithRetry(request, attempt + 1);
                } else {
                    response.close();
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
            Iterator<OpenAIChatStreamResponse> iterator = new Iterator<>() {
                private OpenAIChatStreamResponse nextChunk = null;
                private boolean done = false;
                private boolean closed = false;

                @Override
                public boolean hasNext() {
                    if (done || closed) return false;
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

            // Convert iterator to stream with proper cleanup
            return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED),
                false
            ).onClose(() -> {
                LOG.debug("Closing stream response");
                try {
                    reader.close();
                } catch (IOException e) {
                    LOG.warn("Error closing reader", e);
                }
                response.close();
            });

        } catch (Exception e) {
            response.close();
            if (e instanceof IOException && attempt < 3) {
                long backoffMs = (long) (1000 * Math.pow(2, attempt));
                LOG.warn("Stream request failed for model {}, retrying in {}ms (attempt {}): {}",
                        request.getModel(), backoffMs, attempt + 1, e.getMessage());
                Thread.sleep(backoffMs);
                return sendChatRequestStreamWithRetry(request, attempt + 1);
            }
            throw e;
        }
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
