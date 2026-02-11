package com.robinllm.metrics;

import com.robinllm.model.LLMModel;
import com.robinllm.model.ModelMetrics;
import com.robinllm.repository.MetricsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class MetricsCollector {
    private static final Logger LOG = LoggerFactory.getLogger(MetricsCollector.class);

    @Inject
    MetricsRepository metricsRepository;

    public Optional<ModelMetrics> getLatestMetrics(String modelId) {
        return metricsRepository.findLatestByModelId(modelId);
    }

    public List<ModelMetrics> getAllLatestMetrics() {
        return metricsRepository.findLatestForAllModels();
    }

    public void recordMetrics(ModelMetrics metrics) {
        metricsRepository.save(metrics);
    }

    public List<ModelMetrics> getMetricsHistory(String modelId, int limit) {
        List<ModelMetrics> all = metricsRepository.findByModelId(modelId);
        if (all.size() > limit) {
            return all.subList(0, limit);
        }
        return all;
    }

    public void cleanupOldMetrics(int daysToKeep) {
        var cutoff = java.time.LocalDateTime.now().minusDays(daysToKeep);
        metricsRepository.deleteOlderThan(cutoff);
        LOG.info("Cleaned up metrics older than {} days", daysToKeep);
    }
}
