CREATE TABLE models (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    provider TEXT,
    endpoint TEXT NOT NULL,
    is_free BOOLEAN DEFAULT true,
    pricing_per_1k_tokens REAL,
    max_tokens INTEGER,
    context_window INTEGER,
    capabilities TEXT,
    status TEXT DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_scraped TIMESTAMP
);

CREATE INDEX idx_models_status ON models(status);

CREATE TABLE metrics (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    model_id TEXT NOT NULL,
    avg_latency_ms REAL,
    success_rate REAL,
    error_rate REAL,
    p95_latency_ms REAL,
    p99_latency_ms REAL,
    requests_per_second REAL,
    measured_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (model_id) REFERENCES models(id)
);

CREATE INDEX idx_metrics_model_id ON metrics(model_id);
CREATE INDEX idx_metrics_measured_at ON metrics(measured_at);
