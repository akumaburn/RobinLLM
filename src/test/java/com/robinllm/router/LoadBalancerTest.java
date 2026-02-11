package com.robinllm.router;

import com.robinllm.client.OpenRouterClient;
import com.robinllm.config.AppConfig;
import com.robinllm.model.LLMModel;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@QuarkusTest
class LoadBalancerTest {

    @InjectMock
    AppConfig appConfig;
    
    @InjectMock
    OpenRouterClient openRouterClient;
    
    @Inject
    LoadBalancer loadBalancer;

    @BeforeEach
    void setUp() {
        when(openRouterClient.getRateLimitCount(Mockito.anyString())).thenReturn(0);
    }

    @Test
    void testSelectModelEmptyList() {
        LLMModel result = loadBalancer.selectModel(Collections.emptyList());
        assertNull(result);
    }

    @Test
    void testSelectModelNullList() {
        LLMModel result = loadBalancer.selectModel(null);
        assertNull(result);
    }

    @Test
    void testSelectModelSingleAvailable() {
        LLMModel model = createTestModel("test-model");
        
        LLMModel result = loadBalancer.selectModel(Arrays.asList(model));
        assertEquals("test-model", result.getId());
    }

    @Test
    void testSelectModelRoundRobin() {
        LLMModel model1 = createTestModel("model-1");
        LLMModel model2 = createTestModel("model-2");
        
        // First call should return model1 (index 0)
        LLMModel result1 = loadBalancer.selectModel(Arrays.asList(model1, model2));
        assertEquals("model-1", result1.getId());
        
        // Second call should return model2 (index 1)
        LLMModel result2 = loadBalancer.selectModel(Arrays.asList(model1, model2));
        assertEquals("model-2", result2.getId());
        
        // Third call should return model1 again (index wraps to 0)
        LLMModel result3 = loadBalancer.selectModel(Arrays.asList(model1, model2));
        assertEquals("model-1", result3.getId());
    }

    @Test
    void testSelectRandomModelEmptyList() {
        LLMModel result = loadBalancer.selectRandomModel(Collections.emptyList());
        assertNull(result);
    }

    @Test
    void testSelectRandomModelNullList() {
        LLMModel result = loadBalancer.selectRandomModel(null);
        assertNull(result);
    }

    @Test
    void testSelectRandomModel() {
        LLMModel model1 = createTestModel("model-1");
        LLMModel model2 = createTestModel("model-2");
        LLMModel model3 = createTestModel("model-3");
        
        // With 3 models, we should get a valid result
        LLMModel result = loadBalancer.selectRandomModel(Arrays.asList(model1, model2, model3));
        assertNotNull(result);
        assertTrue(Arrays.asList("model-1", "model-2", "model-3").contains(result.getId()));
    }

    @Test
    void testRecordSuccess() {
        LLMModel model = createTestModel("test-model");
        
        // Initially model should be available
        assertTrue(loadBalancer.isModelAvailable(model));
        
        // Record success
        loadBalancer.recordSuccess(model, 150);
        
        // Model should still be available
        assertTrue(loadBalancer.isModelAvailable(model));
        
        // Health score should be good
        double healthScore = loadBalancer.getModelHealthScore("test-model");
        assertTrue(healthScore > 0.7); // Should be high due to success
    }

    @Test
    void testRecordFailure() {
        LLMModel model = createTestModel("test-model");
        
        // Initially model should be available
        assertTrue(loadBalancer.isModelAvailable(model));
        
        // Record a few failures (but not enough to trigger circuit breaker)
        for (int i = 0; i < 3; i++) {
            loadBalancer.recordFailure(model);
        }
        
        // Model should still be available (threshold is 5)
        assertTrue(loadBalancer.isModelAvailable(model));
        
        // Health score should be degraded
        double healthScore = loadBalancer.getModelHealthScore("test-model");
        assertTrue(healthScore < 0.7);
    }

    @Test
    void testCircuitBreakerTrips() {
        LLMModel model = createTestModel("test-model");
        
        // Record 5 failures to trigger circuit breaker
        for (int i = 0; i < 5; i++) {
            loadBalancer.recordFailure(model);
        }
        
        // Model should now be unavailable
        assertFalse(loadBalancer.isModelAvailable(model));
        
        // Health score should be 0 (circuit breaker open)
        assertEquals(0.0, loadBalancer.getModelHealthScore("test-model"), 0.01);
    }

    @Test
    void testCircuitBreakerResetsAfterRetryDelay() {
        LLMModel model = createTestModel("test-model");
        
        // Trigger circuit breaker
        for (int i = 0; i < 5; i++) {
            loadBalancer.recordFailure(model);
        }
        assertFalse(loadBalancer.isModelAvailable(model));
        
        // Record success (which normally would reset circuit breaker)
        loadBalancer.recordSuccess(model, 100);
        
        // Model should be available again
        assertTrue(loadBalancer.isModelAvailable(model));
    }

    @Test
    void testResetCircuitBreaker() {
        LLMModel model = createTestModel("test-model");
        
        // Trigger circuit breaker
        for (int i = 0; i < 5; i++) {
            loadBalancer.recordFailure(model);
        }
        assertFalse(loadBalancer.isModelAvailable(model));
        
        // Reset circuit breaker
        loadBalancer.resetCircuitBreaker("test-model");
        
        // Model should be available again
        assertTrue(loadBalancer.isModelAvailable(model));
    }

    @Test
    void testResetAllCircuitBreakers() {
        LLMModel model1 = createTestModel("model-1");
        LLMModel model2 = createTestModel("model-2");
        
        // Trigger circuit breakers for both models
        for (int i = 0; i < 5; i++) {
            loadBalancer.recordFailure(model1);
            loadBalancer.recordFailure(model2);
        }
        assertFalse(loadBalancer.isModelAvailable(model1));
        assertFalse(loadBalancer.isModelAvailable(model2));
        
        // Reset all circuit breakers
        loadBalancer.resetAllCircuitBreakers();
        
        // Both models should be available again
        assertTrue(loadBalancer.isModelAvailable(model1));
        assertTrue(loadBalancer.isModelAvailable(model2));
    }

    @Test
    void testModelHealthScore() {
        LLMModel model = createTestModel("test-model");
        
        // Initially no health data
        assertEquals(0.5, loadBalancer.getModelHealthScore("test-model"), 0.01);
        
        // Record good performance
        for (int i = 0; i < 10; i++) {
            loadBalancer.recordSuccess(model, 100); // Fast latency
        }
        
        double healthScore = loadBalancer.getModelHealthScore("test-model");
        assertTrue(healthScore > 0.8); // Should be high
    }

    @Test
    void testSelectModelFallsBackWhenCircuitBreakersOpen() {
        LLMModel model1 = createTestModel("model-1");
        LLMModel model2 = createTestModel("model-2");
        LLMModel model3 = createTestModel("model-3");
        
        // Trigger circuit breaker for all models
        for (int i = 0; i < 5; i++) {
            loadBalancer.recordFailure(model1);
            loadBalancer.recordFailure(model2);
            loadBalancer.recordFailure(model3);
        }
        
        // Should still return a model when all are tripped (fallback behavior)
        LLMModel result = loadBalancer.selectModel(Arrays.asList(model1, model2, model3));
        assertNotNull(result);
        assertTrue(Arrays.asList("model-1", "model-2", "model-3").contains(result.getId()));
    }

    @Test
    void testMultipleSuccessAndFailureRecords() {
        LLMModel model = createTestModel("test-model");
        
        // Record mixed performance
        loadBalancer.recordSuccess(model, 100);
        loadBalancer.recordSuccess(model, 200);
        loadBalancer.recordSuccess(model, 150);
        loadBalancer.recordFailure(model);
        loadBalancer.recordFailure(model);
        
        // Health score should be decent but not perfect
        double healthScore = loadBalancer.getModelHealthScore("test-model");
        assertTrue(healthScore > 0.5); // 60% success rate
        assertTrue(healthScore < 0.9); // Not perfect due to failures
    }

    private LLMModel createTestModel(String id) {
        LLMModel model = new LLMModel();
        model.setId(id);
        model.setName("Test Model " + id);
        model.setProvider("Test Provider");
        model.setEndpoint("https://api.test.com");
        model.setFree(true);
        return model;
    }
}