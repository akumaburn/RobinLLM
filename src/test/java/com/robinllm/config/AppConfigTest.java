package com.robinllm.config;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class AppConfigTest {

    @Inject
    AppConfig appConfig;

    @Test
    void testDefaultConfigValues() {
        assertTrue(appConfig.isScraperEnabled());
        assertEquals("1h", appConfig.getScraperInterval());
        assertEquals("https://openrouter.ai/models", appConfig.getScraperOpenrouterUrl());
        assertEquals("free", appConfig.getScraperFilter());
        
        assertTrue(appConfig.isMetricsEnabled());
        assertEquals("1h", appConfig.getMetricsInterval());
        assertEquals("What is 2+2?,Explain photosynthesis", appConfig.getMetricsTestPrompts());
        assertEquals(3, appConfig.getMetricsTopModels());
        
        assertEquals(0.6, appConfig.getRouterWeightLatency());
        assertEquals(0.3, appConfig.getRouterWeightSuccess());
        assertEquals(0.1, appConfig.getRouterWeightRateLimit());
        assertEquals(0.5, appConfig.getRouterCircuitBreakerThreshold());
        assertEquals(3, appConfig.getRouterRetryMax());
        assertEquals(1000, appConfig.getRouterRetryBackoff());
        
        assertEquals("openai", appConfig.getApiCompatibility());
        assertEquals(4096, appConfig.getApiMaxTokens());
        assertEquals(30000, appConfig.getApiTimeout());
        
        assertEquals("https://openrouter.ai/api/v1", appConfig.getOpenrouterBaseUrl());
    }

    @Test
    void testConfigGetters() {
        // Test that all getters return the expected field values
        assertEquals(appConfig.scraperEnabled, appConfig.isScraperEnabled());
        assertEquals(appConfig.scraperInterval, appConfig.getScraperInterval());
        assertEquals(appConfig.scraperOpenrouterUrl, appConfig.getScraperOpenrouterUrl());
        assertEquals(appConfig.scraperFilter, appConfig.getScraperFilter());
        
        assertEquals(appConfig.metricsEnabled, appConfig.isMetricsEnabled());
        assertEquals(appConfig.metricsInterval, appConfig.getMetricsInterval());
        assertEquals(appConfig.metricsTestPrompts, appConfig.getMetricsTestPrompts());
        assertEquals(appConfig.metricsTopModels, appConfig.getMetricsTopModels());
        
        assertEquals(appConfig.routerWeightLatency, appConfig.getRouterWeightLatency());
        assertEquals(appConfig.routerWeightSuccess, appConfig.getRouterWeightSuccess());
        assertEquals(appConfig.routerWeightRateLimit, appConfig.getRouterWeightRateLimit());
        assertEquals(appConfig.routerCircuitBreakerThreshold, appConfig.getRouterCircuitBreakerThreshold());
        assertEquals(appConfig.routerRetryMax, appConfig.getRouterRetryMax());
        assertEquals(appConfig.routerRetryBackoff, appConfig.getRouterRetryBackoff());
        
        assertEquals(appConfig.apiCompatibility, appConfig.getApiCompatibility());
        assertEquals(appConfig.apiMaxTokens, appConfig.getApiMaxTokens());
        assertEquals(appConfig.apiTimeout, appConfig.getApiTimeout());
        
        assertEquals(appConfig.openrouterApiKey, appConfig.getOpenrouterApiKey());
        assertEquals(appConfig.openrouterBaseUrl, appConfig.getOpenrouterBaseUrl());
    }
}