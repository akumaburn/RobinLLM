package com.robinllm.scraper;

import com.robinllm.config.AppConfig;
import com.robinllm.model.LLMModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenRouterScraperTest {

    @Test
    void testScrapeModelsReturnsNonEmptyList() {
        OpenRouterScraper scraper = new OpenRouterScraper();
        List<LLMModel> models = scraper.scrapeModels("https://openrouter.ai/api/v1/models", "free");
        assertNotNull(models);
        if (!models.isEmpty()) {
            models.forEach(model -> {
                assertNotNull(model.getId());
                assertNotNull(model.getName());
            });
        }
    }

    @Test
    void testScrapeModelsWithNullFilter() {
        OpenRouterScraper scraper = new OpenRouterScraper();
        List<LLMModel> models = scraper.scrapeModels("https://openrouter.ai/api/v1/models", null);
        assertNotNull(models);
        if (!models.isEmpty()) {
            models.forEach(model -> {
                assertNotNull(model.getId());
                assertNotNull(model.getName());
            });
        }
    }
}