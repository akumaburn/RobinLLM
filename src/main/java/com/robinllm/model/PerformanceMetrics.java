package com.robinllm.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PerformanceMetrics {
    private final String modelId;
    private final List<Long> latencies;
    private int successCount;
    private int failureCount;

    public PerformanceMetrics(String modelId) {
        this.modelId = modelId;
        this.latencies = Collections.synchronizedList(new ArrayList<>());
        this.successCount = 0;
        this.failureCount = 0;
    }

    public void recordRequest(long latencyMs, boolean success) {
        latencies.add(latencyMs);
        if (success) {
            successCount++;
        } else {
            failureCount++;
        }
    }

    public double getAverageLatency() {
        if (latencies.isEmpty()) {
            return 0;
        }
        return latencies.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
    }

    public double getP95Latency() {
        if (latencies.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        int index = (int) Math.ceil(sorted.size() * 0.95) - 1;
        return sorted.get(Math.max(0, index));
    }

    public double getP99Latency() {
        if (latencies.isEmpty()) {
            return 0;
        }
        List<Long> sorted = new ArrayList<>(latencies);
        Collections.sort(sorted);
        int index = (int) Math.ceil(sorted.size() * 0.99) - 1;
        return sorted.get(Math.max(0, index));
    }

    public double getSuccessRate() {
        int total = successCount + failureCount;
        return total == 0 ? 0 : (double) successCount / total;
    }

    public double getErrorRate() {
        int total = successCount + failureCount;
        return total == 0 ? 0 : (double) failureCount / total;
    }

    public int getTotalRequests() {
        return successCount + failureCount;
    }

    public String getModelId() {
        return modelId;
    }

    public ModelMetrics toModelMetrics() {
        ModelMetrics metrics = new ModelMetrics();
        metrics.setModelId(modelId);
        metrics.setAvgLatencyMs(getAverageLatency());
        metrics.setSuccessRate(getSuccessRate());
        metrics.setErrorRate(getErrorRate());
        metrics.setP95LatencyMs(getP95Latency());
        metrics.setP99LatencyMs(getP99Latency());
        return metrics;
    }

    public void reset() {
        latencies.clear();
        successCount = 0;
        failureCount = 0;
    }
}
