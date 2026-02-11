package com.robinllm.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.stream.Collectors;

@Singleton
public class DatabaseInitializer {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseInitializer.class);
    
    private final String jdbcUrl;
    
    public DatabaseInitializer() {
        this.jdbcUrl = System.getProperty("sqlite.url", "jdbc:sqlite:robinllm.db");
    }
    
    public void initialize() {
        try {
            // Test if database exists and tables are created
            try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
                // Check if tables exist by trying to query them
                java.sql.DatabaseMetaData meta = conn.getMetaData();
                boolean modelsExists = false;
                boolean metricsExists = false;
                
                try (var modelsTable = meta.getTables(null, null, "models", null)) {
                    modelsExists = modelsTable.next();
                }
                
                try (var metricsTable = meta.getTables(null, null, "metrics", null)) {
                    metricsExists = metricsTable.next();
                }
                
                if (!modelsExists || !metricsExists) {
                    LOG.info("Database tables not found, creating schema");
                    createSchema(conn);
                } else {
                    LOG.info("Database tables already exist");
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to initialize database: {}", e.getMessage(), e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    private void createSchema(Connection conn) throws Exception {
        // Read the SQL schema file
        InputStream is = getClass().getClassLoader().getResourceAsStream("db/migration/V1__Initial_schema.sql");
        if (is == null) {
            throw new RuntimeException("Could not find database schema file");
        }
        
        String schema = new BufferedReader(new InputStreamReader(is))
            .lines()
            .collect(Collectors.joining("\n"));
        
        // Split by semicolons and execute each statement
        String[] statements = schema.split(";");
        
        try (Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                sql = sql.trim();
                if (!sql.isEmpty()) {
                    stmt.execute(sql);
                }
            }
        }
        
        LOG.info("Database schema created successfully");
    }
}