package com.robinllm.repository;

import com.robinllm.model.LLMModel;
import jakarta.enterprise.context.ApplicationScoped;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ModelRepository {

    private final String jdbcUrl;

    public ModelRepository() {
        this.jdbcUrl = System.getProperty("quarkus.datasource.jdbc.url", "jdbc:sqlite:robinllm.db");
    }

    public void save(LLMModel model) {
        String sql = """
            INSERT OR REPLACE INTO models (id, name, provider, endpoint, is_free, pricing_per_1k_tokens,
                                            max_tokens, context_window, capabilities, status, created_at, last_scraped)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, model.getId());
            stmt.setString(2, model.getName());
            stmt.setString(3, model.getProvider());
            stmt.setString(4, model.getEndpoint());
            stmt.setBoolean(5, model.isFree());
            stmt.setDouble(6, model.getPricingPer1kTokens());
            stmt.setInt(7, model.getMaxTokens());
            stmt.setInt(8, model.getContextWindow());
            stmt.setString(9, model.getCapabilities());
            stmt.setString(10, model.getStatus());
            stmt.setObject(11, model.getCreatedAt());
            stmt.setObject(12, model.getLastScraped());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save model", e);
        }
    }

    public void saveAll(List<LLMModel> models) {
        for (LLMModel model : models) {
            save(model);
        }
    }

    public Optional<LLMModel> findById(String id) {
        String sql = "SELECT * FROM models WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToModel(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find model", e);
        }
        return Optional.empty();
    }

    public List<LLMModel> findAll() {
        String sql = "SELECT * FROM models";
        List<LLMModel> models = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                models.add(mapRowToModel(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all models", e);
        }
        return models;
    }

    public List<LLMModel> findByStatus(String status) {
        String sql = "SELECT * FROM models WHERE status = ?";
        List<LLMModel> models = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                models.add(mapRowToModel(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find models by status", e);
        }
        return models;
    }

    public List<LLMModel> findByIsFree(boolean isFree) {
        String sql = "SELECT * FROM models WHERE is_free = ?";
        List<LLMModel> models = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, isFree);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                models.add(mapRowToModel(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find models by free status", e);
        }
        return models;
    }

    public List<LLMModel> findByProvider(String provider) {
        String sql = "SELECT * FROM models WHERE provider = ?";
        List<LLMModel> models = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, provider);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                models.add(mapRowToModel(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find models by provider", e);
        }
        return models;
    }

    public List<LLMModel> findByMaxPricing(double maxPricing) {
        String sql = "SELECT * FROM models WHERE pricing_per_1k_tokens <= ?";
        List<LLMModel> models = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setDouble(1, maxPricing);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                models.add(mapRowToModel(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find models by pricing", e);
        }
        return models;
    }

    public int countByStatus(String status) {
        String sql = "SELECT COUNT(*) FROM models WHERE status = ?";

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count models by status", e);
        }
        return 0;
    }

    public void delete(String id) {
        String sql = "DELETE FROM models WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete model", e);
        }
    }

    public void updateStatus(String id, String status) {
        String sql = "UPDATE models SET status = ?, last_scraped = ? WHERE id = ?";

        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setObject(2, LocalDateTime.now());
            stmt.setString(3, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update model status", e);
        }
    }

    private LLMModel mapRowToModel(ResultSet rs) throws SQLException {
        LLMModel model = new LLMModel();
        model.setId(rs.getString("id"));
        model.setName(rs.getString("name"));
        model.setProvider(rs.getString("provider"));
        model.setEndpoint(rs.getString("endpoint"));
        model.setFree(rs.getBoolean("is_free"));
        model.setPricingPer1kTokens(rs.getDouble("pricing_per_1k_tokens"));
        model.setMaxTokens(rs.getInt("max_tokens"));
        model.setContextWindow(rs.getInt("context_window"));
        model.setCapabilities(rs.getString("capabilities"));
        model.setStatus(rs.getString("status"));

        Object createdAt = rs.getObject("created_at");
        if (createdAt instanceof LocalDateTime) {
            model.setCreatedAt((LocalDateTime) createdAt);
        }

        Object lastScraped = rs.getObject("last_scraped");
        if (lastScraped instanceof LocalDateTime) {
            model.setLastScraped((LocalDateTime) lastScraped);
        }

        return model;
    }
}
