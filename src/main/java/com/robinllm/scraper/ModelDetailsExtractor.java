package com.robinllm.scraper;

import com.robinllm.model.LLMModel;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ModelDetailsExtractor {

    public void enhanceModelDetails(LLMModel model) {
        if (model == null) {
            return;
        }

        if (model.getContextWindow() == 0) {
            model.setContextWindow(4096);
        }

        if (model.getMaxTokens() == 0) {
            model.setMaxTokens(model.getContextWindow());
        }

        if (model.getCapabilities() == null || model.getCapabilities().isEmpty()) {
            model.setCapabilities("text,chat");
        }

        if (model.getProvider() == null || model.getProvider().isEmpty()) {
            model.setProvider("openrouter");
        }
    }

    public String extractModelId(String name) {
        if (name == null) {
            return "";
        }
        return name.toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
