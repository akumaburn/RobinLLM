package com.robinllm.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ModelMetricsTest {

    private ModelMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new ModelMetrics();
    }

    @Test
    void testDefaultConstructor() {
        assertNotNull(metrics.getMeasuredAt());
    }

    @Test
    void testGettersAndSetters() {
        // Test ID
        metrics.setId(12345L);
        assertEquals(12345L, metrics.getId());

        // Test Model ID
        metrics.setModelId("test-model-id");
        assertEquals("test-model-id", metrics.getModelId());

        // Test Average Latency
        metrics.setAvgLatencyMs(150.5);
        assertEquals(150.5, metrics.getAvgLatencyMs());

        // Test Success Rate
        metrics.setSuccessRate(0.95);
        assertEquals(0.95, metrics.getSuccessRate());

        // Test Error Rate
        metrics.setErrorRate(0.05);
        assertEquals(0.05, metrics.getErrorRate());

        // Test P95 Latency
        metrics.setP95LatencyMs(300.0);
        assertEquals(300.0, metrics.getP95LatencyMs());

        // Test P99 Latency
        metrics.setP99LatencyMs(500.0);
        assertEquals(500.0, metrics.getP99LatencyMs());

        // Test Requests Per Second
        metrics.setRequestsPerSecond(10.5);
        assertEquals(10.5, metrics.getRequestsPerSecond());

        // Test Measured At
        LocalDateTime testTime = LocalDateTime.now().minusHours(1);
        metrics.setMeasuredAt(testTime);
        assertEquals(testTime, metrics.getMeasuredAt());
    }

    @Test
    void testMetricsWithAllFields() {
        ModelMetrics fullMetrics = new ModelMetrics();
        fullMetrics.setId(1L);
        fullMetrics.setModelId("openrouter/test-model");
        fullMetrics.setAvgLatencyMs(100.0);
        fullMetrics.setSuccessRate(0.98);
        fullMetrics.setErrorRate(0.02);
        fullMetrics.setP95LatencyMs(200.0);
        fullMetrics.setP99LatencyMs(400.0);
        fullMetrics.setRequestsPerSecond(15.0);
        
        assertEquals(1L, fullMetrics.getId());
        assertEquals("openrouter/test-model", fullMetrics.getModelId());
        assertEquals(100.0, fullMetrics.getAvgLatencyMs());
        assertEquals(0.98, fullMetrics.getSuccessRate());
        assertEquals(0.02, fullMetrics.getErrorRate());
        assertEquals(200.0, fullMetrics.getP95LatencyMs());
        assertEquals(400.0, fullMetrics.getP99LatencyMs());
        assertEquals(15.0, fullMetrics.getRequestsPerSecond());
    }

    @Test
    void testMetricsWithNullFields() {
        ModelMetrics metrics = new ModelMetrics();
        // Test that null values are handled properly
        assertNull(metrics.getModelId());
        
        // Default value should be set
        assertNotNull(metrics.getMeasuredAt());
    }

    @Test
    void testRatesSumToValidValue() {
        // Test that success rate and error rate can be set independently
        metrics.setSuccessRate(0.9);
        metrics.setErrorRate(0.1);
        assertEquals(0.9, metrics.getSuccessRate());
        assertEquals(0.1, metrics.getErrorRate());
        
        // Test edge case where both are 0
        metrics.setSuccessRate(0.0);
        metrics.setErrorRate(0.0);
        assertEquals(0.0, metrics.getSuccessRate());
        assertEquals(0.0, metrics.getErrorRate());
    }

    @Test
    void testLatencyValues() {
        // Test various latency values
        metrics.setAvgLatencyMs(0.0);
        assertEquals(0.0, metrics.getAvgLatencyMs());
        
        metrics.setP95LatencyMs(1000.5);
        assertEquals(1000.5, metrics.getP95LatencyMs());
        
        metrics.setP99LatencyMs(Double.MAX_VALUE);
        assertEquals(Double.MAX_VALUE, metrics.getP99LatencyMs());
    }
}