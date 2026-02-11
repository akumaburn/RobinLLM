package com.robinllm.client;

import com.robinllm.config.AppConfig;
import com.robinllm.dto.OpenAIChatRequest;
import com.robinllm.dto.OpenAIChatResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class OpenRouterClient {
    private static final Logger LOG = LoggerFactory.getLogger(OpenRouterClient.class);

    @Inject
    AppConfig appConfig;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OkHttpClient httpClient;
    private final ConcurrentHashMap<String, AtomicInteger> rateLimitCounters = new ConcurrentHashMap<>();

    public OpenAIChatResponse sendChatRequest(OpenAIChatRequest request) throws Exception {
        return sendChatRequestWithRetry(request, 0);
    }

    private OpenAIChatResponse sendChatRequestWithRetry(OpenAIChatRequest request, int attempt) throws Exception {
        String apiKey = appConfig.getOpenrouterApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new Exception("OpenRouter API key not configured");
        }

        String baseUrl = appConfig.getOpenrouterBaseUrl();
        String url = baseUrl + "/chat/completions";

        String jsonBody = objectMapper.writeValueAsString(request);
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
                String errorBody = response.body() != null ? response.body().string() : "No error details";
                throw new Exception("Request failed with code " + responseCode + ": " + errorBody);
            }

            String responseBody = response.body().string();
            return objectMapper.readValue(responseBody, OpenAIChatResponse.class);

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

    private void handleRateLimit(String modelId) {
        rateLimitCounters.merge(modelId, new AtomicInteger(0), (old, val) -> {
            old.incrementAndGet();
            return old;
        });
    }

    public int getRateLimitCount(String modelId) {
        AtomicInteger counter = rateLimitCounters.get(modelId);
        return counter != null ? counter.get() : 0;
    }

    public void resetRateLimitCounters() {
        rateLimitCounters.clear();
        LOG.info("Reset all rate limit counters");
    }

    private synchronized OkHttpClient getHttpClient() {
        if (httpClient == null) {
            long timeout = appConfig.getApiTimeout();
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                    .readTimeout(timeout, TimeUnit.MILLISECONDS)
                    .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                    .connectionPool(new okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
                    .retryOnConnectionFailure(true)
                    .build();
        }
        return httpClient;
    }
}
