package com.robinllm.metrics;

import com.robinllm.client.OpenRouterClient;
import com.robinllm.config.AppConfig;
import com.robinllm.dto.OpenAIChatRequest;
import com.robinllm.dto.OpenAIChatResponse;
import com.robinllm.model.LLMModel;
import com.robinllm.model.ModelMetrics;
import com.robinllm.model.PerformanceMetrics;
import com.robinllm.repository.MetricsRepository;
import com.robinllm.repository.ModelRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@ApplicationScoped
public class PerformanceTester {
    private static final Logger LOG = LoggerFactory.getLogger(PerformanceTester.class);

    @Inject
    AppConfig appConfig;

    @Inject
    ModelRepository modelRepository;

    @Inject
    MetricsRepository metricsRepository;

    @Inject
    OpenRouterClient openRouterClient;

    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().factory()
    );

    public Map<String, PerformanceMetrics> testModels(List<LLMModel> models) {
        Map<String, PerformanceMetrics> results = new ConcurrentHashMap<>();
        String[] prompts = getTestPrompts();

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (LLMModel model : models) {
            if (!"active".equals(model.getStatus())) {
                continue;
            }

            PerformanceMetrics perfMetrics = new PerformanceMetrics(model.getId());
            results.put(model.getId(), perfMetrics);

            for (String prompt : prompts) {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        testModel(model, prompt, perfMetrics);
                    } catch (Exception e) {
                        LOG.warn("Failed to test model {}: {}", model.getId(), e.getMessage());
                        perfMetrics.recordRequest(0, false);
                    }
                }, executor));
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return results;
    }

    private void testModel(LLMModel model, String prompt, PerformanceMetrics perfMetrics) {
        long startTime = System.currentTimeMillis();
        boolean success = sendTestRequest(model, prompt);
        long latency = System.currentTimeMillis() - startTime;

        perfMetrics.recordRequest(latency, success);
        LOG.info("Tested model {}: {}ms, success={}", model.getId(), latency, success);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean sendTestRequest(LLMModel model, String prompt) {
        try {
            OpenAIChatRequest request = new OpenAIChatRequest();
            request.setModel(model.getId());

            List<OpenAIChatRequest.Message> messages = new ArrayList<>();
            messages.add(new OpenAIChatRequest.Message("user", prompt));
            request.setMessages(messages);

            request.setMaxTokens(50);

            OpenAIChatResponse response = openRouterClient.sendChatRequest(request);
            return response != null && response.getChoices() != null && !response.getChoices().isEmpty();

        } catch (Exception e) {
            LOG.debug("Test request failed for model {}: {}", model.getId(), e.getMessage());
            return false;
        }
    }

    private String[] getTestPrompts() {
        String prompts = appConfig.getMetricsTestPrompts();
        if (prompts == null || prompts.isEmpty()) {
            return new String[]{"What is 2+2?"};
        }
        return prompts.split(",");
    }

    public void saveMetrics(Map<String, PerformanceMetrics> metrics) {
        for (PerformanceMetrics perf : metrics.values()) {
            ModelMetrics modelMetrics = perf.toModelMetrics();
            int totalRequests = perf.getTotalRequests();
            if (totalRequests > 0) {
                modelMetrics.setRequestsPerSecond((double) totalRequests / 60.0);
            }
            metricsRepository.save(modelMetrics);
        }
        LOG.info("Saved metrics for {} models", metrics.size());
    }
}
