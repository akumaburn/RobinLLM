package com.robinllm.scraper;

import com.robinllm.config.AppConfig;
import com.robinllm.model.ModelPool;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@ApplicationScoped
public class ScraperScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(ScraperScheduler.class);

    @Inject
    AppConfig appConfig;

    @Inject
    OpenRouterScraper scraper;

    @Inject
    ModelPool modelPool;

    private final ExecutorService executor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().factory()
    );

    @Scheduled(every = "{scraper.interval}")
    public void scrapeModels() {
        if (!appConfig.isScraperEnabled()) {
            return;
        }

        LOG.info("Starting scheduled model scraping");

        try {
            var models = scraper.scrapeModels(
                    appConfig.getScraperOpenrouterUrl(),
                    appConfig.getScraperFilter()
            );

            List<String> previousModels = modelPool.getAvailableModels()
                    .stream()
                    .map(m -> m.getId())
                    .toList();

            scraper.saveModels(models);

            modelPool.clear();
            models.forEach(modelPool::addModel);

            List<String> currentModels = models.stream()
                    .map(m -> m.getId())
                    .toList();

            for (String modelId : previousModels) {
                if (!currentModels.contains(modelId)) {
                    LOG.info("Model {} is no longer available", modelId);
                }
            }

            for (String modelId : currentModels) {
                if (!previousModels.contains(modelId)) {
                    LOG.info("New model {} discovered", modelId);
                }
            }

            LOG.info("Scraping completed. Added {} models to pool", models.size());

        } catch (Exception e) {
            LOG.error("Error during scheduled scraping: {}", e.getMessage(), e);
        }
    }

    public void scrapeModelsNow() {
        CompletableFuture.runAsync(() -> scrapeModels(), executor).join();
    }
}
