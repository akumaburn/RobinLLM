package com.robinllm.metrics;

import com.robinllm.config.AppConfig;
import com.robinllm.model.LLMModel;
import com.robinllm.model.ModelMetrics;
import com.robinllm.model.PerformanceMetrics;
import com.robinllm.repository.ModelRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class MetricsScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(MetricsScheduler.class);

    @Inject
    AppConfig appConfig;

    @Inject
    PerformanceTester performanceTester;

    @Inject
    ModelRepository modelRepository;

    @Scheduled(every = "{metrics.interval}")
    public void collectMetrics() {
        if (!appConfig.isMetricsEnabled()) {
            return;
        }

        LOG.info("Starting scheduled metrics collection");

        try {
            List<LLMModel> models = modelRepository.findByStatus("active");
            LOG.info("Testing {} active models", models.size());

            Map<String, PerformanceMetrics> results = performanceTester.testModels(models);
            performanceTester.saveMetrics(results);

            LOG.info("Metrics collection completed");

        } catch (Exception e) {
            LOG.error("Error during metrics collection: {}", e.getMessage(), e);
        }
    }
}
