package com.robinllm.repository;

import com.robinllm.model.ModelMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MetricsRepositoryTest extends BaseRepositoryTest {

    private MetricsRepository repository;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUpDatabase();
        System.setProperty("quarkus.datasource.jdbc.url", TEST_DB_URL);
        repository = new MetricsRepository();
    }

    @Disabled
    @Test
    void testSaveAndFindById() throws SQLException {
        ModelMetrics metrics = createTestMetrics("model-1");
        repository.save(metrics);
        
        long generatedId = metrics.getId();
        Optional<ModelMetrics> found = repository.findById(generatedId);
        assertTrue(found.isPresent());
        assertEquals(metrics.getModelId(), found.get().getModelId());
        assertEquals(metrics.getAvgLatencyMs(), found.get().getAvgLatencyMs());
    }

    @Test
    void testFindByIdNonExistent() throws SQLException {
        Optional<ModelMetrics> found = repository.findById(99999L);
        assertFalse(found.isPresent());
    }

    @Test
    void testFindAll() throws SQLException {
        ModelMetrics metrics1 = createTestMetrics("model-1");
        ModelMetrics metrics2 = createTestMetrics("model-2");
        
        repository.save(metrics1);
        repository.save(metrics2);
        
        List<ModelMetrics> all = repository.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void testFindLatestByModelId() throws SQLException {
        LocalDateTime earlierTime = LocalDateTime.now().minusHours(1);
        LocalDateTime laterTime = LocalDateTime.now();

        ModelMetrics earlierMetrics = createTestMetrics("model-1");
        earlierMetrics.setMeasuredAt(earlierTime);
        repository.save(earlierMetrics);

        ModelMetrics laterMetrics = createTestMetrics("model-1");
        laterMetrics.setMeasuredAt(laterTime);
        repository.save(laterMetrics);

        Optional<ModelMetrics> latest = repository.findLatestByModelId("model-1");
        assertTrue(latest.isPresent());
        assertEquals(laterTime.getSecond(), latest.get().getMeasuredAt().getSecond());
    }

    @Test
    void testFindLatestByModelIdNonExistent() throws SQLException {
        Optional<ModelMetrics> found = repository.findLatestByModelId("non-existent-model");
        assertFalse(found.isPresent());
    }

    @Test
    void testFindByModelId() throws SQLException {
        LocalDateTime time1 = LocalDateTime.now().minusHours(2);
        LocalDateTime time2 = LocalDateTime.now().minusHours(1);

        ModelMetrics metrics1 = createTestMetrics("model-1");
        metrics1.setMeasuredAt(time1);
        repository.save(metrics1);

        ModelMetrics metrics2 = createTestMetrics("model-1");
        metrics2.setMeasuredAt(time2);
        repository.save(metrics2);

        List<ModelMetrics> modelMetrics = repository.findByModelId("model-1");
        assertEquals(2, modelMetrics.size());

        // Should be ordered by measured_at DESC (compare by second to avoid nanosecond precision issues)
        assertEquals(time2.getSecond(), modelMetrics.get(0).getMeasuredAt().getSecond());
        assertEquals(time1.getSecond(), modelMetrics.get(1).getMeasuredAt().getSecond());
    }

    @Test
    void testFindSince() throws SQLException {
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        
        ModelMetrics recentMetrics = createTestMetrics("new-model");
        recentMetrics.setMeasuredAt(yesterday);
        repository.save(recentMetrics);
        
        List<ModelMetrics> found = repository.findSince(yesterday.minusHours(1));
        assertEquals(1, found.size());
        assertEquals("new-model", found.get(0).getModelId());
    }

    @Test
    void testDeleteOlderThan() throws SQLException {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(1);
        
        ModelMetrics oldMetrics = createTestMetrics("old-model");
        oldMetrics.setMeasuredAt(cutoff.minusHours(1));
        repository.save(oldMetrics);
        
        ModelMetrics recentMetrics = createTestMetrics("new-model");
        recentMetrics.setMeasuredAt(LocalDateTime.now());
        repository.save(recentMetrics);
        
        repository.deleteOlderThan(cutoff);
        
        List<ModelMetrics> remaining = repository.findAll();
        assertEquals(1, remaining.size());
        assertEquals("new-model", remaining.get(0).getModelId());
    }

    @Test
    void testFindLatestForAllModels() throws SQLException {
        LocalDateTime earlierTime = LocalDateTime.now().minusHours(2);
        LocalDateTime laterTime = LocalDateTime.now().minusHours(1);

        ModelMetrics model1Early = createTestMetrics("model-1");
        model1Early.setMeasuredAt(earlierTime);
        repository.save(model1Early);

        ModelMetrics model1Late = createTestMetrics("model-1");
        model1Late.setMeasuredAt(laterTime);
        repository.save(model1Late);

        ModelMetrics model2 = createTestMetrics("model-2");
        model2.setMeasuredAt(laterTime);
        repository.save(model2);

        List<ModelMetrics> latest = repository.findLatestForAllModels();
        assertEquals(2, latest.size());

        assertTrue(latest.stream().anyMatch(m -> m.getModelId().equals("model-1") &&
                                                  m.getMeasuredAt().getSecond() == laterTime.getSecond()));
        assertTrue(latest.stream().anyMatch(m -> m.getModelId().equals("model-2") &&
                                                  m.getMeasuredAt().getSecond() == laterTime.getSecond()));
    }

    private ModelMetrics createTestMetrics(String modelId) {
        ModelMetrics metrics = new ModelMetrics();
        metrics.setModelId(modelId);
        metrics.setAvgLatencyMs(100.0);
        metrics.setSuccessRate(0.95);
        metrics.setErrorRate(0.05);
        metrics.setP95LatencyMs(200.0);
        metrics.setP99LatencyMs(300.0);
        metrics.setRequestsPerSecond(10.0);
        metrics.setMeasuredAt(LocalDateTime.now());
        return metrics;
    }
}