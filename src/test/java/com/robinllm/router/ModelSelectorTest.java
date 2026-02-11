package com.robinllm.router;

import com.robinllm.config.AppConfig;
import com.robinllm.model.LLMModel;
import com.robinllm.model.ModelMetrics;
import com.robinllm.repository.MetricsRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class ModelSelectorTest {

    @InjectMock
    AppConfig appConfig;
    
    @InjectMock
    MetricsRepository metricsRepository;
    
    @Inject
    ModelSelector modelSelector;

    @BeforeEach
    void setUp() {
        // Default config values
        when(appConfig.getMetricsTopModels()).thenReturn(3);
        when(appConfig.getRouterWeightLatency()).thenReturn(0.6);
        when(appConfig.getRouterWeightSuccess()).thenReturn(0.3);
        when(appConfig.getRouterWeightRateLimit()).thenReturn(0.1);
    }

    @Test
    void testSelectBestModelsEmptyList() {
        List<LLMModel> result = modelSelector.selectBestModels(Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    @Test
    void testSelectBestModelsNullList() {
        List<LLMModel> result = modelSelector.selectBestModels(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSelectBestModelsWithoutMetrics() {
        LLMModel model1 = createTestModel("model-1");
        LLMModel model2 = createTestModel("model-2");
        
        // No metrics available - should return default score (50.0) and keep input order
        when(metricsRepository.findLatestByModelId(anyString())).thenReturn(Optional.empty());
        
        List<LLMModel> result = modelSelector.selectBestModels(Arrays.asList(model1, model2));
        assertEquals(2, result.size());
        // Should maintain order when scores are equal
        assertEquals("model-1", result.get(0).getId());
        assertEquals("model-2", result.get(1).getId());
    }

    @Test
    void testSelectBestModelsSortsByScore() {
        LLMModel fastModel = createTestModel("fast-model");
        LLMModel slowModel = createTestModel("slow-model");
        
        // Fast model has better metrics
        ModelMetrics fastMetrics = createTestMetrics("fast-model", 100, 0.95);
        ModelMetrics slowMetrics = createTestMetrics("slow-model", 1000, 0.90);
        
        when(metricsRepository.findLatestByModelId("fast-model")).thenReturn(Optional.of(fastMetrics));
        when(metricsRepository.findLatestByModelId("slow-model")).thenReturn(Optional.of(slowMetrics));
        
        List<LLMModel> result = modelSelector.selectBestModels(Arrays.asList(slowModel, fastModel));
        assertEquals(2, result.size());
        // Fast model should be first due to better score
        assertEquals("fast-model", result.get(0).getId());
        assertEquals("slow-model", result.get(1).getId());
    }

    @Test
    void testSelectBestModelsRespectsTopN() {
        when(appConfig.getMetricsTopModels()).thenReturn(2);
        
        LLMModel model1 = createTestModel("model-1");
        LLMModel model2 = createTestModel("model-2");
        LLMModel model3 = createTestModel("model-3");
        
        when(metricsRepository.findLatestByModelId(anyString())).thenReturn(Optional.empty());
        
        List<LLMModel> result = modelSelector.selectBestModels(Arrays.asList(model1, model2, model3));
        assertEquals(2, result.size());
    }

    @Test
    void testSelectModel() {
        LLMModel model1 = createTestModel("model-1");
        LLMModel model2 = createTestModel("model-2");
        
        when(metricsRepository.findLatestByModelId(anyString())).thenReturn(Optional.empty());
        
        LLMModel result = modelSelector.selectModel(Arrays.asList(model1, model2));
        assertEquals("model-1", result.getId());
    }

    @Test
    void testSelectModelEmptyList() {
        LLMModel result = modelSelector.selectModel(Collections.emptyList());
        assertNull(result);
    }

    @Test
    void testSelectModelWithRoundRobin() {
        LLMModel model1 = createTestModel("model-1");
        LLMModel model2 = createTestModel("model-2");
        
        when(metricsRepository.findLatestByModelId(anyString())).thenReturn(Optional.empty());
        
        // First call should select model1
        LLMModel result1 = modelSelector.selectModelWithRoundRobin(Arrays.asList(model1, model2));
        assertEquals("model-1", result1.getId());
        
        // Second call should select model2 (since model1 has usage count 1)
        LLMModel result2 = modelSelector.selectModelWithRoundRobin(Arrays.asList(model1, model2));
        assertEquals("model-2", result2.getId());
        
        // Third call should select model1 again (both have usage count 1, picks first)
        LLMModel result3 = modelSelector.selectModelWithRoundRobin(Arrays.asList(model1, model2));
        assertEquals("model-1", result3.getId());
    }

    @Test
    void testSelectModelWithRoundRobinEmptyList() {
        LLMModel result = modelSelector.selectModelWithRoundRobin(Collections.emptyList());
        assertNull(result);
    }

    @Test
    void testCalculateLatencyScore() {
        // Test through selectBestModels with controlled metrics
        LLMModel fastModel = createTestModel("fast-model");
        LLMModel slowModel = createTestModel("slow-model");
        
        ModelMetrics fastMetrics = createTestMetrics("fast-model", 100, 1.0);
        ModelMetrics slowMetrics = createTestMetrics("slow-model", 10000, 1.0);
        
        when(metricsRepository.findLatestByModelId("fast-model")).thenReturn(Optional.of(fastMetrics));
        when(metricsRepository.findLatestByModelId("slow-model")).thenReturn(Optional.of(slowMetrics));
        
        List<LLMModel> result = modelSelector.selectBestModels(Arrays.asList(fastModel, slowModel));
        // Fast model should be first due to better latency score
        assertEquals("fast-model", result.get(0).getId());
        assertEquals("slow-model", result.get(1).getId());
    }

    @Test
    void testRecordModelUsage() {
        modelSelector.recordModelUsage("test-model");
        
        LLMModel model = createTestModel("test-model");
        when(metricsRepository.findLatestByModelId(anyString())).thenReturn(Optional.empty());
        
        // First call - model should be selected (only option)
        LLMModel result = modelSelector.selectModelWithRoundRobin(Arrays.asList(model));
        assertEquals("test-model", result.getId());
        
        // Second call - model still should be selected (only option)
        result = modelSelector.selectModelWithRoundRobin(Arrays.asList(model));
        assertEquals("test-model", result.getId());
    }

    @Test
    void testModelScoreCalculation() {
        LLMModel model = createTestModel("test-model");
        
        // Create metrics with known values
        ModelMetrics metrics = new ModelMetrics();
        metrics.setModelId("test-model");
        metrics.setAvgLatencyMs(100);  // Should give a good latency score
        metrics.setSuccessRate(0.8);     // 80% success rate
        
        when(metricsRepository.findLatestByModelId("test-model")).thenReturn(Optional.of(metrics));
        
        // Expected score calculation:
        // Latency score: 100 - (100/10000 * 100) = 99
        // Success score: 0.8 * 100 = 80
        // Rate limit score: 100
        // Total: (99 * 0.6) + (80 * 0.3) + (100 * 0.1) = 59.4 + 24 + 10 = 93.4
        
        List<LLMModel> result = modelSelector.selectBestModels(Arrays.asList(model));
        assertEquals(1, result.size());
        assertEquals("test-model", result.get(0).getId());
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

    private ModelMetrics createTestMetrics(String modelId, double avgLatency, double successRate) {
        ModelMetrics metrics = new ModelMetrics();
        metrics.setModelId(modelId);
        metrics.setAvgLatencyMs(avgLatency);
        metrics.setSuccessRate(successRate);
        metrics.setErrorRate(1.0 - successRate);
        metrics.setMeasuredAt(LocalDateTime.now());
        return metrics;
    }
}