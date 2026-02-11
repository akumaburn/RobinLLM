package com.robinllm.router;

import com.robinllm.config.AppConfig;
import com.robinllm.client.OpenRouterClient;
import com.robinllm.model.LLMModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class LoadBalancer {
    private static final Logger LOG = LoggerFactory.getLogger(LoadBalancer.class);

    @Inject
    AppConfig appConfig;

    @Inject
    OpenRouterClient openRouterClient;

    private final Map<String, CircuitBreakerState> circuitBreakers = new ConcurrentHashMap<>();
    private final Map<String, ModelHealth> modelHealth = new ConcurrentHashMap<>();
    private final Map<String, Integer> requestCounts = new ConcurrentHashMap<>();

    private final Random random = new Random();

    public LLMModel selectModel(List<LLMModel> models) {
        if (models == null || models.isEmpty()) {
            return null;
        }

        List<LLMModel> availableModels = new ArrayList<>();
        for (LLMModel model : models) {
            if (isModelAvailable(model)) {
                availableModels.add(model);
            }
        }

        if (availableModels.isEmpty()) {
            LOG.warn("No available models after circuit breaker check, falling back to any model");
            resetAllCircuitBreakers();
            return models.get(0);
        }

        LLMModel selected = selectWithRoundRobin(availableModels);
        LOG.debug("Selected model {} for request (health: {}, rate limit hits: {})",
                selected.getId(),
                getModelHealthStatus(selected.getId()),
                openRouterClient.getRateLimitCount(selected.getId()));

        return selected;
    }

    private LLMModel selectWithRoundRobin(List<LLMModel> models) {
        int index = requestCounts.getOrDefault("index", 0) % models.size();
        requestCounts.put("index", index + 1);
        return models.get(index);
    }

    public LLMModel selectRandomModel(List<LLMModel> models) {
        if (models == null || models.isEmpty()) {
            return null;
        }

        List<LLMModel> availableModels = new ArrayList<>();
        for (LLMModel model : models) {
            if (isModelAvailable(model)) {
                availableModels.add(model);
            }
        }

        if (availableModels.isEmpty()) {
            LOG.warn("No available models, resetting circuit breakers");
            resetAllCircuitBreakers();
            return models.get(random.nextInt(models.size()));
        }

        return availableModels.get(random.nextInt(availableModels.size()));
    }

    public void recordSuccess(LLMModel model, long latencyMs) {
        CircuitBreakerState state = circuitBreakers.computeIfAbsent(
                model.getId(),
                k -> new CircuitBreakerState()
        );
        state.recordSuccess();

        ModelHealth health = modelHealth.computeIfAbsent(
                model.getId(),
                k -> new ModelHealth()
        );
        health.recordSuccess(latencyMs);

        LOG.debug("Recorded success for model {} ({}ms)", model.getId(), latencyMs);
    }

    public void recordFailure(LLMModel model) {
        CircuitBreakerState state = circuitBreakers.computeIfAbsent(
                model.getId(),
                k -> new CircuitBreakerState()
        );
        state.recordFailure();

        ModelHealth health = modelHealth.computeIfAbsent(
                model.getId(),
                k -> new ModelHealth()
        );
        health.recordFailure();

        LOG.debug("Recorded failure for model {}", model.getId());
    }

    public boolean isModelAvailable(LLMModel model) {
        CircuitBreakerState state = circuitBreakers.get(model.getId());
        if (state == null) {
            return true;
        }
        return !state.isOpen();
    }

    public void resetCircuitBreaker(String modelId) {
        circuitBreakers.remove(modelId);
        modelHealth.remove(modelId);
        LOG.info("Reset circuit breaker for model {}", modelId);
    }

    public void resetAllCircuitBreakers() {
        circuitBreakers.clear();
        modelHealth.clear();
        LOG.info("Reset all circuit breakers");
    }

    public double getModelHealthScore(String modelId) {
        CircuitBreakerState state = circuitBreakers.get(modelId);
        ModelHealth health = modelHealth.get(modelId);

        double circuitScore = (state == null || !state.isOpen()) ? 1.0 : 0.0;
        double healthScore = health == null ? 0.5 : health.getHealthScore();

        return (circuitScore * 0.7) + (healthScore * 0.3);
    }

    private String getModelHealthStatus(String modelId) {
        ModelHealth health = modelHealth.get(modelId);
        if (health == null) {
            return "unknown";
        }
        double score = health.getHealthScore();
        if (score >= 0.8) return "excellent";
        if (score >= 0.6) return "good";
        if (score >= 0.4) return "fair";
        return "poor";
    }

    private static class CircuitBreakerState {
        private final int failureThreshold;
        private final long retryDelayMs;
        private int failureCount = 0;
        private long lastFailureTime = 0;

        public CircuitBreakerState() {
            this.failureThreshold = 5;
            this.retryDelayMs = 60000;
        }

        public void recordSuccess() {
            failureCount = 0;
        }

        public void recordFailure() {
            failureCount++;
            lastFailureTime = System.currentTimeMillis();
        }

        public boolean isOpen() {
            if (failureCount < failureThreshold) {
                return false;
            }

            long timeSinceLastFailure = System.currentTimeMillis() - lastFailureTime;
            if (timeSinceLastFailure > retryDelayMs) {
                failureCount = 0;
                return false;
            }

            return true;
        }
    }

    private static class ModelHealth {
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final List<Long> recentLatencies = new ArrayList<>();

        public void recordSuccess(long latencyMs) {
            successCount.incrementAndGet();
            synchronized (recentLatencies) {
                recentLatencies.add(latencyMs);
                if (recentLatencies.size() > 10) {
                    recentLatencies.remove(0);
                }
            }
        }

        public void recordFailure() {
            failureCount.incrementAndGet();
        }

        public double getSuccessRate() {
            int total = successCount.get() + failureCount.get();
            return total == 0 ? 0 : (double) successCount.get() / total;
        }

        public double getAvgLatency() {
            synchronized (recentLatencies) {
                if (recentLatencies.isEmpty()) {
                    return 0;
                }
                return recentLatencies.stream()
                        .mapToLong(Long::longValue)
                        .average()
                        .orElse(0);
            }
        }

        public double getHealthScore() {
            double successRate = getSuccessRate();
            double latencyScore = getLatencyScore();
            return (successRate * 0.6) + (latencyScore * 0.4);
        }

        private double getLatencyScore() {
            double avgLatency = getAvgLatency();
            if (avgLatency == 0) return 0.5;

            if (avgLatency < 1000) return 1.0;
            if (avgLatency < 2000) return 0.8;
            if (avgLatency < 5000) return 0.6;
            if (avgLatency < 10000) return 0.4;
            return 0.2;
        }
    }
}
