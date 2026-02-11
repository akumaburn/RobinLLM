package com.robinllm.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class AppConfig {

    @ConfigProperty(name = "scraper.enabled", defaultValue = "true")
    public boolean scraperEnabled;

    @ConfigProperty(name = "scraper.interval", defaultValue = "1h")
    public String scraperInterval;

    @ConfigProperty(name = "scraper.openrouter.url", defaultValue = "https://openrouter.ai/models")
    public String scraperOpenrouterUrl;

    @ConfigProperty(name = "scraper.filter", defaultValue = "free")
    public String scraperFilter;

    @ConfigProperty(name = "metrics.enabled", defaultValue = "true")
    public boolean metricsEnabled;

    @ConfigProperty(name = "metrics.interval", defaultValue = "1h")
    public String metricsInterval;

    @ConfigProperty(name = "metrics.test.prompts", defaultValue = "What is 2+2?,Explain photosynthesis")
    public String metricsTestPrompts;

    @ConfigProperty(name = "metrics.top-models", defaultValue = "3")
    public int metricsTopModels;

    @ConfigProperty(name = "router.weight.latency", defaultValue = "0.6")
    public double routerWeightLatency;

    @ConfigProperty(name = "router.weight.success", defaultValue = "0.3")
    public double routerWeightSuccess;

    @ConfigProperty(name = "router.weight.rate-limit", defaultValue = "0.1")
    public double routerWeightRateLimit;

    @ConfigProperty(name = "router.circuit-breaker.threshold", defaultValue = "0.5")
    public double routerCircuitBreakerThreshold;

    @ConfigProperty(name = "router.retry.max", defaultValue = "3")
    public int routerRetryMax;

    @ConfigProperty(name = "router.retry.backoff", defaultValue = "1000")
    public long routerRetryBackoff;

    @ConfigProperty(name = "api.compatibility", defaultValue = "openai")
    public String apiCompatibility;

    @ConfigProperty(name = "api.max-tokens", defaultValue = "4096")
    public int apiMaxTokens;

    @ConfigProperty(name = "api.timeout", defaultValue = "30000")
    public long apiTimeout;

    @ConfigProperty(name = "openrouter.api-key", defaultValue = "test-key-for-testing")
    public String openrouterApiKey;

    @ConfigProperty(name = "openrouter.base-url", defaultValue = "https://openrouter.ai/api/v1")
    public String openrouterBaseUrl;

    public boolean isScraperEnabled() {
        return scraperEnabled;
    }

    public String getScraperInterval() {
        return scraperInterval;
    }

    public String getScraperOpenrouterUrl() {
        return scraperOpenrouterUrl;
    }

    public String getScraperFilter() {
        return scraperFilter;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public String getMetricsInterval() {
        return metricsInterval;
    }

    public String getMetricsTestPrompts() {
        return metricsTestPrompts;
    }

    public int getMetricsTopModels() {
        return metricsTopModels;
    }

    public double getRouterWeightLatency() {
        return routerWeightLatency;
    }

    public double getRouterWeightSuccess() {
        return routerWeightSuccess;
    }

    public double getRouterWeightRateLimit() {
        return routerWeightRateLimit;
    }

    public double getRouterCircuitBreakerThreshold() {
        return routerCircuitBreakerThreshold;
    }

    public int getRouterRetryMax() {
        return routerRetryMax;
    }

    public long getRouterRetryBackoff() {
        return routerRetryBackoff;
    }

    public String getApiCompatibility() {
        return apiCompatibility;
    }

    public int getApiMaxTokens() {
        return apiMaxTokens;
    }

    public long getApiTimeout() {
        return apiTimeout;
    }

    public String getOpenrouterApiKey() {
        return openrouterApiKey;
    }

    public String getOpenrouterBaseUrl() {
        return openrouterBaseUrl;
    }
}
