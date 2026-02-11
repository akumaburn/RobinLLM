package com.robinllm.model;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ModelPoolTest {

    @Inject
    ModelPool modelPool;

    @BeforeEach
    void setUp() {
        modelPool.clear();
    }

    @Test
    void testEmptyPool() {
        assertEquals(0, modelPool.size());
        assertTrue(modelPool.getAvailableModels().isEmpty());
        assertTrue(modelPool.getActiveModels().isEmpty());
        assertTrue(modelPool.getFreeModels().isEmpty());
        assertTrue(modelPool.getDegradedModels().isEmpty());
        assertTrue(modelPool.getUnavailableModels().isEmpty());
    }

    @Test
    void testAddAndGetModel() {
        LLMModel model = createTestModel("test-model", "active", true);
        modelPool.addModel(model);
        
        assertEquals(1, modelPool.size());
        assertEquals(model, modelPool.getModel("test-model"));
        assertEquals("active", modelPool.getModelStatus("test-model"));
    }

    @Test
    void testAddActiveModel() {
        LLMModel model = createTestModel("active-model", "active", true);
        modelPool.addModel(model);
        
        assertEquals(1, modelPool.getAvailableModels().size());
        assertEquals(1, modelPool.getActiveModels().size());
        assertEquals(1, modelPool.getFreeModels().size());
        assertEquals(0, modelPool.getDegradedModels().size());
    }

    @Test
    void testAddDegradedModel() {
        LLMModel model = createTestModel("degraded-model", "degraded", true);
        modelPool.addModel(model);
        
        assertEquals(0, modelPool.getAvailableModels().size());
        assertEquals(0, modelPool.getActiveModels().size());
        assertEquals(0, modelPool.getFreeModels().size());
        assertEquals(0, modelPool.getDegradedModels().size());
    }

    @Test
    void testAddUnavailableModel() {
        LLMModel model = createTestModel("unavailable-model", "unavailable", true);
        modelPool.addModel(model);
        
        assertEquals(0, modelPool.getAvailableModels().size());
        assertEquals(0, modelPool.getActiveModels().size());
        assertEquals(0, modelPool.getFreeModels().size());
        assertEquals(0, modelPool.getDegradedModels().size());
        assertEquals(1, modelPool.getUnavailableModels().size());
    }

    @Test
    void testAddPaidModel() {
        LLMModel model = createTestModel("paid-model", "active", false);
        modelPool.addModel(model);
        
        assertEquals(1, modelPool.getAvailableModels().size());
        assertEquals(1, modelPool.getActiveModels().size());
        assertEquals(0, modelPool.getFreeModels().size());
    }

    @Test
    void testRemoveModel() {
        LLMModel model = createTestModel("test-model", "active", true);
        modelPool.addModel(model);
        assertEquals(1, modelPool.size());
        
        modelPool.removeModel("test-model");
        assertEquals(0, modelPool.size());
        assertNull(modelPool.getModel("test-model"));
        assertNull(modelPool.getModelStatus("test-model"));
    }

    @Test
    void testRemoveNonExistentModel() {
        // Should not throw exception
        modelPool.removeModel("non-existent-model");
        assertEquals(0, modelPool.size());
    }

    @Test
    void testMultipleModels() {
        LLMModel freeModel = createTestModel("free-model", "active", true);
        LLMModel paidModel = createTestModel("paid-model", "active", false);
        LLMModel degradedModel = createTestModel("degraded-model", "degraded", true);
        LLMModel unavailableModel = createTestModel("unavailable-model", "unavailable", true);
        
        modelPool.addModel(freeModel);
        modelPool.addModel(paidModel);
        modelPool.addModel(degradedModel);
        modelPool.addModel(unavailableModel);
        
        assertEquals(4, modelPool.size());
        assertEquals(2, modelPool.getAvailableModels().size()); // active models only
        assertEquals(2, modelPool.getActiveModels().size()); // free + paid active
        assertEquals(1, modelPool.getFreeModels().size()); // only free active
        assertEquals(0, modelPool.getDegradedModels().size()); // degraded not in available
        assertEquals(1, modelPool.getUnavailableModels().size());
    }

    @Test
    void testUpdateModelStatusToActive() {
        LLMModel model = createTestModel("test-model", "degraded", true);
        modelPool.addModel(model);
        assertEquals(0, modelPool.getAvailableModels().size());
        
        // Update to active
        modelPool.updateModelStatus("test-model", "active");
        
        assertEquals(1, modelPool.getAvailableModels().size());
        assertEquals(1, modelPool.getActiveModels().size());
        assertEquals(1, modelPool.getFreeModels().size());
        assertEquals("active", modelPool.getModelStatus("test-model"));
    }

    @Test
    void testUpdateModelStatusToDegraded() {
        LLMModel model = createTestModel("test-model", "active", true);
        modelPool.addModel(model);
        assertEquals(1, modelPool.getAvailableModels().size());
        
        // Update to degraded
        modelPool.updateModelStatus("test-model", "degraded");
        
        assertEquals(0, modelPool.getAvailableModels().size());
        assertEquals(0, modelPool.getActiveModels().size());
        assertEquals(0, modelPool.getFreeModels().size());
        assertEquals("degraded", modelPool.getModelStatus("test-model"));
    }

    @Test
    void testUpdateModelStatusToUnavailable() {
        LLMModel model = createTestModel("test-model", "active", true);
        modelPool.addModel(model);
        assertEquals(1, modelPool.getAvailableModels().size());
        
        // Update to unavailable
        modelPool.updateModelStatus("test-model", "unavailable");
        
        assertEquals(0, modelPool.getAvailableModels().size());
        assertEquals(0, modelPool.getActiveModels().size());
        assertEquals(0, modelPool.getFreeModels().size());
        assertEquals(1, modelPool.getUnavailableModels().size());
        assertEquals("unavailable", modelPool.getModelStatus("test-model"));
    }

    @Test
    void testUpdateNonExistentModel() {
        // Should not throw exception
        modelPool.updateModelStatus("non-existent", "active");
        assertEquals(0, modelPool.size());
    }

    @Test
    void testGetAllStatuses() {
        LLMModel model1 = createTestModel("model-1", "active", true);
        LLMModel model2 = createTestModel("model-2", "degraded", true);
        LLMModel model3 = createTestModel("model-3", "unavailable", false);
        
        modelPool.addModel(model1);
        modelPool.addModel(model2);
        modelPool.addModel(model3);
        
        Map<String, String> statuses = modelPool.getAllStatuses();
        assertEquals(3, statuses.size());
        assertEquals("active", statuses.get("model-1"));
        assertEquals("degraded", statuses.get("model-2"));
        assertEquals("unavailable", statuses.get("model-3"));
        
        // Should return a copy, modifications shouldn't affect the original
        statuses.put("model-4", "active");
        assertEquals(3, modelPool.getAllStatuses().size());
    }

    @Test
    void testClear() {
        modelPool.addModel(createTestModel("model-1", "active", true));
        modelPool.addModel(createTestModel("model-2", "degraded", true));
        assertEquals(2, modelPool.size());
        
        modelPool.clear();
        assertEquals(0, modelPool.size());
        assertTrue(modelPool.getAvailableModels().isEmpty());
        assertTrue(modelPool.getAllStatuses().isEmpty());
    }

    @Test
    void testThreadSafety() {
        // This is a basic test - in a real scenario you'd want multiple threads
        LLMModel model = createTestModel("thread-test", "active", true);
        modelPool.addModel(model);
        
        // Test that getAvailableModels returns a copy
        List<LLMModel> available1 = modelPool.getAvailableModels();
        List<LLMModel> available2 = modelPool.getAvailableModels();
        assertNotSame(available1, available2); // Should be different instances
        assertEquals(available1, available2); // But same content
        
        // Modifying the returned list shouldn't affect the pool
        available1.clear();
        assertEquals(1, modelPool.getAvailableModels().size());
    }

    private LLMModel createTestModel(String id, String status, boolean isFree) {
        LLMModel model = new LLMModel();
        model.setId(id);
        model.setName("Test Model " + id);
        model.setProvider("Test Provider");
        model.setEndpoint("https://api.test.com");
        model.setStatus(status);
        model.setFree(isFree);
        return model;
    }
}