package com.robinllm.startup;

import com.robinllm.config.DatabaseInitializer;
import com.robinllm.metrics.MetricsScheduler;
import com.robinllm.model.ModelPool;
import com.robinllm.repository.ModelRepository;
import com.robinllm.scraper.ScraperScheduler;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class StartupInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(StartupInitializer.class);

    @Inject
    MetricsScheduler metricsScheduler;

    @Inject
    ScraperScheduler scraperScheduler;

    @Inject
    ModelRepository modelRepository;

    @Inject
    ModelPool modelPool;
    
    @Inject
    DatabaseInitializer databaseInitializer;

    void onStart(@Observes StartupEvent event) {
        LOG.info("Initializing database");
        databaseInitializer.initialize();
        
        LOG.info("Application started, triggering initial data collection");
        
        try {
            LOG.info("Triggering initial metrics collection");
            metricsScheduler.collectMetrics();
            
            Thread.sleep(5000);
            
            LOG.info("Triggering initial model scraping");
            scraperScheduler.scrapeModelsNow();
            
            int modelCount = modelRepository.countByStatus("active");
            LOG.info("Initial collection completed. Active models in database: {}", modelCount);
            
        } catch (Exception e) {
            LOG.error("Error during initial collection: {}", e.getMessage(), e);
        }
    }
}
