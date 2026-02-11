package com.robinllm.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceMetricsTest {

    private PerformanceMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new PerformanceMetrics("test-model-id");
    }

    @Test
    void testConstructor() {
        assertEquals("test-model-id", metrics.getModelId());
        assertEquals(0, metrics.getTotalRequests());
        assertEquals(0.0, metrics.getSuccessRate());
        assertEquals(0.0, metrics.getErrorRate());
        assertEquals(0.0, metrics.getAverageLatency());
        assertEquals(0.0, metrics.getP95Latency());
        assertEquals(0.0, metrics.getP99Latency());
    }

    @Test
    void testRecordRequest() {
        // Record successful requests
        metrics.recordRequest(100, true);
        metrics.recordRequest(200, true);
        metrics.recordRequest(150, true);
        
        assertEquals(3, metrics.getTotalRequests());
        assertEquals(1.0, metrics.getSuccessRate());
        assertEquals(0.0, metrics.getErrorRate());
        assertEquals(150.0, metrics.getAverageLatency(), 0.01);
        
        // Record failed requests
        metrics.recordRequest(50, false);
        metrics.recordRequest(75, false);
        
        assertEquals(5, metrics.getTotalRequests());
        assertEquals(0.6, metrics.getSuccessRate(), 0.01);
        assertEquals(0.4, metrics.getErrorRate(), 0.01);
        assertEquals(115.0, metrics.getAverageLatency(), 0.01);
    }

    @Test
    void testPercentileCalculations() {
        // Record latencies in a known order
        long[] latencies = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
        for (long latency : latencies) {
            metrics.recordRequest(latency, true);
        }
        
        // For 10 values, P95 should be the 10th value (index 9)
        assertEquals(1000.0, metrics.getP95Latency());
        // For 10 values, P99 should also be the 10th value
        assertEquals(1000.0, metrics.getP99Latency());
    }

    @Test
    void testPercentileCalculationsWithPartialSet() {
        // Record only 5 values
        long[] latencies = {100, 200, 300, 400, 500};
        for (long latency : latencies) {
            metrics.recordRequest(latency, true);
        }
        
        // For 5 values, P95 should be the 5th value (index 4)
        assertEquals(500.0, metrics.getP95Latency());
        // For 5 values, P99 should also be the 5th value
        assertEquals(500.0, metrics.getP99Latency());
    }

    @Test
    void testPercentileCalculationsWithSingleValue() {
        metrics.recordRequest(250, true);
        
        assertEquals(250.0, metrics.getP95Latency());
        assertEquals(250.0, metrics.getP99Latency());
    }

    @Test
    void testEmptyMetrics() {
        // Test with no recorded requests
        assertEquals(0.0, metrics.getAverageLatency());
        assertEquals(0.0, metrics.getP95Latency());
        assertEquals(0.0, metrics.getP99Latency());
        assertEquals(0.0, metrics.getSuccessRate());
        assertEquals(0.0, metrics.getErrorRate());
        assertEquals(0, metrics.getTotalRequests());
    }

    @Test
    void testToModelMetrics() {
        // Record some test data
        metrics.recordRequest(100, true);
        metrics.recordRequest(200, true);
        metrics.recordRequest(300, false);
        
        ModelMetrics modelMetrics = metrics.toModelMetrics();
        
        assertEquals("test-model-id", modelMetrics.getModelId());
        assertEquals(200.0, modelMetrics.getAvgLatencyMs(), 0.01); // Average of 100, 200, 300
        assertEquals(2.0/3.0, modelMetrics.getSuccessRate(), 0.01);
        assertEquals(1.0/3.0, modelMetrics.getErrorRate(), 0.01);
    }

    @Test
    void testReset() {
        // Record some data
        metrics.recordRequest(100, true);
        metrics.recordRequest(200, false);
        assertEquals(2, metrics.getTotalRequests());
        
        // Reset
        metrics.reset();
        
        // Verify reset worked
        assertEquals(0, metrics.getTotalRequests());
        assertEquals(0.0, metrics.getSuccessRate());
        assertEquals(0.0, metrics.getErrorRate());
        assertEquals(0.0, metrics.getAverageLatency());
    }

    @Test
    void testConcurrentAccess() {
        // Test that the synchronized list works correctly
        // This is a basic test; in a real scenario, you might want to use multiple threads
        metrics.recordRequest(100, true);
        metrics.recordRequest(200, true);
        metrics.recordRequest(300, false);
        
        assertEquals(3, metrics.getTotalRequests());
    }
}