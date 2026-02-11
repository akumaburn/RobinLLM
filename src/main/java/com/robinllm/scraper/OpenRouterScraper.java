package com.robinllm.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.robinllm.config.AppConfig;
import com.robinllm.model.LLMModel;
import com.robinllm.repository.ModelRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class OpenRouterScraper {
    private static final Logger LOG = LoggerFactory.getLogger(OpenRouterScraper.class);

    @Inject
    ModelRepository modelRepository;

    @Inject
    ModelDetailsExtractor modelDetailsExtractor;

    @Inject
    AppConfig appConfig;

    public List<LLMModel> scrapeModels(String url, String filter) {
        List<LLMModel> models = new ArrayList<>();

        try {
            LOG.info("Fetching models from OpenRouter API");
            String apiKey = appConfig.getOpenrouterApiKey();
            if (apiKey == null || apiKey.isEmpty()) {
                LOG.error("OPENROUTER_API_KEY not configured");
                return models;
            }

            Client client = ClientBuilder.newClient();
            try {
                Response response = client.target("https://openrouter.ai/api/v1/models")
                        .request()
                        .header("Authorization", "Bearer " + apiKey)
                        .header("HTTP-Referer", "https://github.com/yourusername/robinllm")
                        .header("X-Title", "Robin LLM")
                        .get();

                try {
                    if (response.getStatus() != 200) {
                        LOG.error("Failed to fetch models: {}", response.getStatus());
                        return models;
                    }

                    String responseBody = response.readEntity(String.class);
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode root = mapper.readTree(responseBody);
                    JsonNode data = root.get("data");

                    if (data != null && data.isArray()) {
                        for (JsonNode modelNode : data) {
                            try {
                                LLMModel model = extractModelFromApi(modelNode);
                                if (model != null && modelMatchesFilter(model, filter)) {
                                    models.add(model);
                                }
                            } catch (Exception e) {
                                LOG.warn("Failed to extract model from API response: {}", e.getMessage());
                            }
                        }
                    }
                } finally {
                    response.close();
                }
            } finally {
                client.close();
            }

            LOG.info("Found {} models", models.size());

        } catch (Exception e) {
            LOG.error("Failed to fetch models: {}", e.getMessage(), e);
        }

        return models;
    }

    private LLMModel extractModelFromApi(JsonNode modelNode) {
        String id = modelNode.get("id").asText();
        String name = modelNode.get("name").asText();
        
        // Extract provider from ID (first part before /)
        String provider = "unknown";
        if (id.contains("/")) {
            provider = id.split("/")[0];
        }

        LLMModel model = new LLMModel();
        model.setId(id);
        model.setName(name);
        model.setProvider(provider);
        model.setEndpoint("https://openrouter.ai/api/v1/chat/completions");

        // Check if it's free based on pricing or :free suffix in ID
        boolean isFree = false;
        
        // First check if model ID ends with :free (OpenRouter convention for free models)
        if (id.endsWith(":free")) {
            isFree = true;
        }
        
        // Also check pricing as fallback
        if (!isFree) {
            JsonNode pricing = modelNode.get("pricing");
            if (pricing != null) {
                String promptPrice = pricing.has("prompt") ? pricing.get("prompt").asText() : "0";
                String completionPrice = pricing.has("completion") ? pricing.get("completion").asText() : "0";
                
                // Check if both prompt and completion are exactly $0
                try {
                    double promptCost = Double.parseDouble(promptPrice);
                    double completionCost = Double.parseDouble(completionPrice);
                    isFree = (promptCost == 0.0 && completionCost == 0.0);
                } catch (NumberFormatException e) {
                    // If parsing fails, assume not free unless ID ends with :free
                    isFree = false;
                }
            }
        }

        model.setFree(isFree);

        // Extract context length
        JsonNode contextNode = modelNode.path("top_provider").path("context_length");
        if (!contextNode.isMissingNode()) {
            model.setContextWindow(contextNode.asInt());
        }

        // Extract max tokens
        JsonNode maxTokensNode = modelNode.path("top_provider").path("max_completion_tokens");
        if (!maxTokensNode.isMissingNode()) {
            model.setMaxTokens(maxTokensNode.asInt());
        }

        // Set capabilities based on architecture
        JsonNode architecture = modelNode.get("architecture");
        if (architecture != null) {
            JsonNode modalities = architecture.get("modality");
            if (modalities != null) {
                model.setCapabilities(modalities.asText());
            }
        }

        model.setStatus("active");
        model.setLastScraped(LocalDateTime.now());

        return model;
    }

    private boolean modelMatchesFilter(LLMModel model, String filter) {
        if (filter == null || filter.isEmpty()) {
            return true;
        }

        String lowerFilter = filter.toLowerCase();
        
        // Check if filter is "free" and model is free
        if ("free".equals(lowerFilter)) {
            return model.isFree();
        }
        
        String modelName = model.getName().toLowerCase();
        String modelId = model.getId().toLowerCase();

        return modelName.contains(lowerFilter) || modelId.contains(lowerFilter);
    }

    public void saveModels(List<LLMModel> models) {
        for (LLMModel model : models) {
            modelRepository.save(model);
        }
        LOG.info("Saved {} models to database", models.size());
    }
}
