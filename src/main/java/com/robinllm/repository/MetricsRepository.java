package com.robinllm.repository;

import com.robinllm.model.ModelMetrics;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class MetricsRepository {

    private final String jdbcUrl;

    public MetricsRepository() {
        this.jdbcUrl = System.getProperty("quarkus.datasource.jdbc.url", "jdbc:sqlite:robinllm.db");
    }

    public void save(ModelMetrics metrics) {
        String sql = "INSERT INTO metrics (model_id, avg_latency_ms, success_rate, error_rate, " +
                "p95_latency_ms, p99_latency_ms, requests_per_second, measured_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, metrics.getModelId());
            stmt.setDouble(2, metrics.getAvgLatencyMs());
            stmt.setDouble(3, metrics.getSuccessRate());
            stmt.setDouble(4, metrics.getErrorRate());
            stmt.setDouble(5, metrics.getP95LatencyMs());
            stmt.setDouble(6, metrics.getP99LatencyMs());
            stmt.setDouble(7, metrics.getRequestsPerSecond());
            stmt.setObject(8, metrics.getMeasuredAt());
            stmt.executeUpdate();

            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    metrics.setId(generatedKeys.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save metrics", e);
        }
    }

    public Optional<ModelMetrics> findById(long id) {
        String sql = "SELECT * FROM metrics WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToMetrics(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find metrics", e);
        }
        return Optional.empty();
    }

    public List<ModelMetrics> findAll() {
        String sql = "SELECT * FROM metrics ORDER BY measured_at DESC";
        List<ModelMetrics> metrics = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    metrics.add(mapRowToMetrics(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all metrics", e);
        }
        return metrics;
    }

    public Optional<ModelMetrics> findLatestByModelId(String modelId) {
        String sql = "SELECT * FROM metrics WHERE model_id = ? ORDER BY measured_at DESC LIMIT 1";

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, modelId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToMetrics(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find latest metrics", e);
        }
        return Optional.empty();
    }

    public List<ModelMetrics> findByModelId(String modelId) {
        String sql = "SELECT * FROM metrics WHERE model_id = ? ORDER BY measured_at DESC";
        List<ModelMetrics> metrics = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, modelId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    metrics.add(mapRowToMetrics(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find metrics by model id", e);
        }
        return metrics;
    }

    public List<ModelMetrics> findSince(LocalDateTime since) {
        String sql = "SELECT * FROM metrics WHERE measured_at >= ? ORDER BY measured_at DESC";
        List<ModelMetrics> metrics = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, since);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    metrics.add(mapRowToMetrics(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find metrics since date", e);
        }
        return metrics;
    }

    public List<ModelMetrics> findLatestForAllModels() {
        String sql = "SELECT m.* FROM metrics m " +
                "INNER JOIN (" +
                "SELECT model_id, MAX(measured_at) as max_measured_at " +
                "FROM metrics " +
                "GROUP BY model_id" +
                ") latest ON m.model_id = latest.model_id AND m.measured_at = latest.max_measured_at " +
                "ORDER BY m.measured_at DESC";

        List<ModelMetrics> metrics = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    metrics.add(mapRowToMetrics(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find latest metrics for all models", e);
        }
        return metrics;
    }

    public void deleteOlderThan(LocalDateTime cutoff) {
        String sql = "DELETE FROM metrics WHERE measured_at < ?";

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, cutoff);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete old metrics", e);
        }
    }

    private ModelMetrics mapRowToMetrics(ResultSet rs) throws SQLException {
        ModelMetrics metrics = new ModelMetrics();
        metrics.setId(rs.getLong("id"));
        metrics.setModelId(rs.getString("model_id"));
        metrics.setAvgLatencyMs(rs.getDouble("avg_latency_ms"));
        metrics.setSuccessRate(rs.getDouble("success_rate"));
        metrics.setErrorRate(rs.getDouble("error_rate"));
        metrics.setP95LatencyMs(rs.getDouble("p95_latency_ms"));
        metrics.setP99LatencyMs(rs.getDouble("p99_latency_ms"));
        metrics.setRequestsPerSecond(rs.getDouble("requests_per_second"));

        Object measuredAt = rs.getObject("measured_at");
        if (measuredAt instanceof LocalDateTime) {
            metrics.setMeasuredAt((LocalDateTime) measuredAt);
        }

        return metrics;
    }
}
