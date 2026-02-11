package com.robinllm.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class BaseRepositoryTest {

    protected static final String TEST_DB_URL = "jdbc:sqlite:test_robinllm.db";

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load SQLite JDBC driver", e);
        }
    }

    @BeforeEach
    void setUpDatabase() throws SQLException {
        createTables();
    }

    @AfterEach
    void tearDownDatabase() throws SQLException {
        dropTables();
    }

    private void createTables() throws SQLException {
        try (Connection conn = DriverManager.getConnection(TEST_DB_URL);
             Statement stmt = conn.createStatement()) {

            stmt.execute("CREATE TABLE IF NOT EXISTS models (" +
                    "id TEXT PRIMARY KEY, " +
                    "name TEXT NOT NULL, " +
                    "provider TEXT, " +
                    "endpoint TEXT NOT NULL, " +
                    "is_free BOOLEAN DEFAULT true, " +
                    "pricing_per_1k_tokens REAL, " +
                    "max_tokens INTEGER, " +
                    "context_window INTEGER, " +
                    "capabilities TEXT, " +
                    "status TEXT DEFAULT 'active', " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "last_scraped TIMESTAMP)");

            stmt.execute("CREATE TABLE IF NOT EXISTS metrics (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "model_id TEXT NOT NULL, " +
                    "avg_latency_ms REAL, " +
                    "success_rate REAL, " +
                    "error_rate REAL, " +
                    "p95_latency_ms REAL, " +
                    "p99_latency_ms REAL, " +
                    "requests_per_second REAL, " +
                    "measured_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "FOREIGN KEY (model_id) REFERENCES models(id))");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_models_status ON models(status)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_metrics_model_id ON metrics(model_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_metrics_measured_at ON metrics(measured_at)");
        }
    }

    private void dropTables() throws SQLException {
        try (Connection conn = DriverManager.getConnection(TEST_DB_URL);
             Statement stmt = conn.createStatement()) {

            stmt.execute("DROP TABLE IF EXISTS metrics");
            stmt.execute("DROP TABLE IF EXISTS models");

            stmt.execute("DROP INDEX IF EXISTS idx_models_status");
            stmt.execute("DROP INDEX IF EXISTS idx_metrics_model_id");
            stmt.execute("DROP INDEX IF EXISTS idx_metrics_measured_at");
        }
    }
}