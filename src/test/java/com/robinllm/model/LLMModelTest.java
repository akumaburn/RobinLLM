package com.robinllm.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class LLMModelTest {

    private LLMModel model;

    @BeforeEach
    void setUp() {
        model = new LLMModel();
    }

    @Test
    void testDefaultConstructor() {
        assertNotNull(model.getCreatedAt());
        assertEquals("active", model.getStatus());
    }

    @Test
    void testGettersAndSetters() {
        // Test ID
        model.setId("test-id");
        assertEquals("test-id", model.getId());

        // Test Name
        model.setName("Test Model");
        assertEquals("Test Model", model.getName());

        // Test Provider
        model.setProvider("Test Provider");
        assertEquals("Test Provider", model.getProvider());

        // Test Endpoint
        model.setEndpoint("https://api.test.com");
        assertEquals("https://api.test.com", model.getEndpoint());

        // Test Free flag
        model.setFree(true);
        assertTrue(model.isFree());

        // Test Pricing
        model.setPricingPer1kTokens(0.001);
        assertEquals(0.001, model.getPricingPer1kTokens());

        // Test Max Tokens
        model.setMaxTokens(4096);
        assertEquals(4096, model.getMaxTokens());

        // Test Context Window
        model.setContextWindow(8192);
        assertEquals(8192, model.getContextWindow());

        // Test Capabilities
        model.setCapabilities("text,vision");
        assertEquals("text,vision", model.getCapabilities());

        // Test Status
        model.setStatus("degraded");
        assertEquals("degraded", model.getStatus());

        // Test Created At
        LocalDateTime testTime = LocalDateTime.now().minusDays(1);
        model.setCreatedAt(testTime);
        assertEquals(testTime, model.getCreatedAt());

        // Test Last Scraped
        LocalDateTime scrapedTime = LocalDateTime.now();
        model.setLastScraped(scrapedTime);
        assertEquals(scrapedTime, model.getLastScraped());
    }

    @Test
    void testModelWithAllFields() {
        LLMModel fullModel = new LLMModel();
        fullModel.setId("openrouter/test-model");
        fullModel.setName("Test Model");
        fullModel.setProvider("Test Provider");
        fullModel.setEndpoint("https://openrouter.ai/api/v1");
        fullModel.setFree(true);
        fullModel.setPricingPer1kTokens(0.0);
        fullModel.setMaxTokens(4096);
        fullModel.setContextWindow(8192);
        fullModel.setCapabilities("text");
        fullModel.setStatus("active");
        
        assertEquals("openrouter/test-model", fullModel.getId());
        assertEquals("Test Model", fullModel.getName());
        assertEquals("Test Provider", fullModel.getProvider());
        assertEquals("https://openrouter.ai/api/v1", fullModel.getEndpoint());
        assertTrue(fullModel.isFree());
        assertEquals(0.0, fullModel.getPricingPer1kTokens());
        assertEquals(4096, fullModel.getMaxTokens());
        assertEquals(8192, fullModel.getContextWindow());
        assertEquals("text", fullModel.getCapabilities());
        assertEquals("active", fullModel.getStatus());
    }

    @Test
    void testModelWithNullFields() {
        LLMModel model = new LLMModel();
        // Test that null values are handled properly
        assertNull(model.getId());
        assertNull(model.getName());
        assertNull(model.getProvider());
        assertNull(model.getEndpoint());
        assertNull(model.getCapabilities());
        assertNull(model.getLastScraped());
        
        // Default values should still be set
        assertNotNull(model.getCreatedAt());
        assertEquals("active", model.getStatus());
    }
}