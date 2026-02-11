package com.robinllm.model;

import java.time.LocalDateTime;

public class LLMModel {
    private String id;
    private String name;
    private String provider;
    private String endpoint;
    private boolean isFree;
    private double pricingPer1kTokens;
    private int maxTokens;
    private int contextWindow;
    private String capabilities;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime lastScraped;

    public LLMModel() {
        this.createdAt = LocalDateTime.now();
        this.status = "active";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public boolean isFree() {
        return isFree;
    }

    public void setFree(boolean free) {
        isFree = free;
    }

    public double getPricingPer1kTokens() {
        return pricingPer1kTokens;
    }

    public void setPricingPer1kTokens(double pricingPer1kTokens) {
        this.pricingPer1kTokens = pricingPer1kTokens;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getContextWindow() {
        return contextWindow;
    }

    public void setContextWindow(int contextWindow) {
        this.contextWindow = contextWindow;
    }

    public String getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(String capabilities) {
        this.capabilities = capabilities;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastScraped() {
        return lastScraped;
    }

    public void setLastScraped(LocalDateTime lastScraped) {
        this.lastScraped = lastScraped;
    }
}
