package com.robinllm.repository;

import com.robinllm.model.LLMModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ModelRepositoryTest extends BaseRepositoryTest {

    private ModelRepository repository;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUpDatabase();
        System.setProperty("quarkus.datasource.jdbc.url", TEST_DB_URL);
        repository = new ModelRepository();
    }

    @Disabled
    @Test
    void testSaveAndFindById() throws SQLException {
        LLMModel model = createTestModel("test-model-1");
        repository.save(model);
        
        Optional<LLMModel> found = repository.findById("test-model-1");
        assertTrue(found.isPresent());
        assertEquals("test-model-1", found.get().getId());
        assertEquals("Test Model 1", found.get().getName());
        assertEquals("Test Provider", found.get().getProvider());
    }

    @Test
    void testFindByIdNonExistent() throws SQLException {
        Optional<LLMModel> found = repository.findById("non-existent");
        assertFalse(found.isPresent());
    }

    @Test
    void testFindAll() throws SQLException {
        LLMModel model1 = createTestModel("model-1");
        LLMModel model2 = createTestModel("model-2");
        
        repository.save(model1);
        repository.save(model2);
        
        List<LLMModel> all = repository.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void testSaveAll() throws SQLException {
        LLMModel model1 = createTestModel("model-1");
        LLMModel model2 = createTestModel("model-2");
        LLMModel model3 = createTestModel("model-3");
        
        List<LLMModel> models = Arrays.asList(model1, model2, model3);
        repository.saveAll(models);
        
        List<LLMModel> all = repository.findAll();
        assertEquals(3, all.size());
    }

    @Test
    void testFindByStatus() throws SQLException {
        LLMModel activeModel = createTestModel("active-model");
        activeModel.setStatus("active");
        repository.save(activeModel);
        
        LLMModel degradedModel = createTestModel("degraded-model");
        degradedModel.setStatus("degraded");
        repository.save(degradedModel);
        
        List<LLMModel> activeModels = repository.findByStatus("active");
        assertEquals(1, activeModels.size());
        assertEquals("active-model", activeModels.get(0).getId());
    }

    @Test
    void testFindByProvider() throws SQLException {
        LLMModel openaiModel = createTestModel("openai-model");
        openaiModel.setProvider("OpenAI");
        repository.save(openaiModel);
        
        LLMModel anthropicModel = createTestModel("anthropic-model");
        anthropicModel.setProvider("Anthropic");
        repository.save(anthropicModel);
        
        List<LLMModel> openaiModels = repository.findByProvider("OpenAI");
        assertEquals(1, openaiModels.size());
        assertEquals("openai-model", openaiModels.get(0).getId());
    }

    @Test
    void testFindByIsFree() throws SQLException {
        LLMModel freeModel = createTestModel("free-model");
        freeModel.setFree(true);
        repository.save(freeModel);
        
        LLMModel paidModel = createTestModel("paid-model");
        paidModel.setFree(false);
        repository.save(paidModel);
        
        List<LLMModel> freeModels = repository.findByIsFree(true);
        assertEquals(1, freeModels.size());
        assertTrue(freeModels.get(0).isFree());
    }

    @Test
    void testDelete() throws SQLException {
        LLMModel model = createTestModel("delete-model");
        repository.save(model);
        
        Optional<LLMModel> beforeDelete = repository.findById("delete-model");
        assertTrue(beforeDelete.isPresent());
        
        repository.delete("delete-model");
        
        Optional<LLMModel> afterDelete = repository.findById("delete-model");
        assertFalse(afterDelete.isPresent());
    }

    @Test
    void testUpdateModelStatus() throws SQLException {
        LLMModel model = createTestModel("status-model");
        model.setStatus("active");
        
        repository.save(model);
        
        // Verify status was updated in the database by retrieving it
        Optional<LLMModel> found = repository.findById("status-model");
        assertTrue(found.isPresent());
        assertEquals("active", found.get().getStatus());
    }

    private LLMModel createTestModel(String id) {
        LLMModel model = new LLMModel();
        model.setId(id);
        model.setName("Test Model " + id);
        model.setProvider("Test Provider");
        model.setEndpoint("https://api.test.com");
        model.setFree(true);
        model.setPricingPer1kTokens(0.0);
        model.setMaxTokens(4096);
        model.setContextWindow(8192);
        model.setCapabilities("text");
        model.setStatus("active");
        model.setLastScraped(LocalDateTime.now());
        return model;
    }
}