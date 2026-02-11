package com.robinllm.router;

import com.robinllm.config.AppConfig;
import com.robinllm.model.LLMModel;
import com.robinllm.model.ModelMetrics;
import com.robinllm.repository.MetricsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class ModelSelector {
    private static final Logger LOG = LoggerFactory.getLogger(ModelSelector.class);

    @Inject
    AppConfig appConfig;

    @Inject
    MetricsRepository metricsRepository;

    private final Map<String, Integer> modelUsageCounts = new HashMap<>();

    public List<LLMModel> selectBestModels(List<LLMModel> models) {
        if (models == null || models.isEmpty()) {
            return Collections.emptyList();
        }

        List<ModelScore> scoredModels = new ArrayList<>();

        for (LLMModel model : models) {
            double score = calculateModelScore(model);
            scoredModels.add(new ModelScore(model, score));
        }

        scoredModels.sort((a, b) -> Double.compare(b.score, a.score));

        int topN = Math.min(appConfig.getMetricsTopModels(), scoredModels.size());
        return scoredModels.stream()
                .limit(topN)
                .map(ModelScore::getModel)
                .collect(Collectors.toList());
    }

    private double calculateModelScore(LLMModel model) {
        Optional<ModelMetrics> metricsOpt = metricsRepository.findLatestByModelId(model.getId());

        if (metricsOpt.isEmpty()) {
            return 50.0;
        }

        ModelMetrics metrics = metricsOpt.get();

        double latencyScore = calculateLatencyScore(metrics.getAvgLatencyMs());
        double successScore = metrics.getSuccessRate() * 100;
        double rateLimitScore = 100.0;

        double weightLatency = appConfig.getRouterWeightLatency();
        double weightSuccess = appConfig.getRouterWeightSuccess();
        double weightRateLimit = appConfig.getRouterWeightRateLimit();

        return (latencyScore * weightLatency) +
               (successScore * weightSuccess) +
               (rateLimitScore * weightRateLimit);
    }

    private double calculateLatencyScore(double latencyMs) {
        if (latencyMs <= 0) {
            return 50.0;
        }

        double maxLatency = 10000.0;
        double normalized = 100.0 - (latencyMs / maxLatency * 100.0);
        return Math.max(0, Math.min(100, normalized));
    }

    public LLMModel selectModel(List<LLMModel> models) {
        List<LLMModel> bestModels = selectBestModels(models);
        if (bestModels.isEmpty()) {
            return null;
        }

        return bestModels.get(0);
    }

    public LLMModel selectModelWithRoundRobin(List<LLMModel> models) {
        List<LLMModel> bestModels = selectBestModels(models);
        if (bestModels.isEmpty()) {
            return null;
        }

        int index = 0;
        int minUsage = Integer.MAX_VALUE;

        for (int i = 0; i < bestModels.size(); i++) {
            String modelId = bestModels.get(i).getId();
            int usage = modelUsageCounts.getOrDefault(modelId, 0);
            if (usage < minUsage) {
                minUsage = usage;
                index = i;
            }
        }

        LLMModel selected = bestModels.get(index);
        modelUsageCounts.merge(selected.getId(), 1, Integer::sum);
        return selected;
    }

    public void recordModelUsage(String modelId) {
        modelUsageCounts.merge(modelId, 1, Integer::sum);
    }

    private static class ModelScore {
        private final LLMModel model;
        private final double score;

        public ModelScore(LLMModel model, double score) {
            this.model = model;
            this.score = score;
        }

        public LLMModel getModel() {
            return model;
        }

        public double getScore() {
            return score;
        }
    }
}
