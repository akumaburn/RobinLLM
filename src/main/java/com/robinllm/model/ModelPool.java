package com.robinllm.model;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@ApplicationScoped
public class ModelPool {
    private final Map<String, LLMModel> models;
    private final List<LLMModel> availableModels;
    private final Map<String, String> modelStatus;

    public ModelPool() {
        this.models = new ConcurrentHashMap<>();
        this.availableModels = new CopyOnWriteArrayList<>();
        this.modelStatus = new ConcurrentHashMap<>();
    }

    public void addModel(LLMModel model) {
        models.put(model.getId(), model);
        modelStatus.put(model.getId(), model.getStatus());
        if ("active".equals(model.getStatus())) {
            availableModels.add(model);
        }
    }

    public void removeModel(String modelId) {
        LLMModel model = models.remove(modelId);
        modelStatus.remove(modelId);
        if (model != null) {
            availableModels.remove(model);
        }
    }

    public LLMModel getModel(String modelId) {
        return models.get(modelId);
    }

    public String getModelStatus(String modelId) {
        return modelStatus.get(modelId);
    }

    public List<LLMModel> getAvailableModels() {
        return List.copyOf(availableModels);
    }

    public List<LLMModel> getActiveModels() {
        return availableModels.stream()
                .filter(m -> "active".equals(m.getStatus()))
                .collect(Collectors.toList());
    }

    public List<LLMModel> getFreeModels() {
        return availableModels.stream()
                .filter(LLMModel::isFree)
                .collect(Collectors.toList());
    }

    public List<LLMModel> getDegradedModels() {
        return availableModels.stream()
                .filter(m -> "degraded".equals(m.getStatus()))
                .collect(Collectors.toList());
    }

    public List<LLMModel> getUnavailableModels() {
        return models.values().stream()
                .filter(m -> "unavailable".equals(m.getStatus()))
                .collect(Collectors.toList());
    }

    public int size() {
        return models.size();
    }

    public void updateModelStatus(String modelId, String status) {
        LLMModel model = models.get(modelId);
        if (model != null) {
            model.setStatus(status);
            model.setLastScraped(java.time.LocalDateTime.now());
            modelStatus.put(modelId, status);

            if ("active".equals(status)) {
                if (!availableModels.contains(model)) {
                    availableModels.add(model);
                }
            } else if ("degraded".equals(status) || "unavailable".equals(status)) {
                availableModels.remove(model);
            }
        }
    }

    public void clear() {
        models.clear();
        availableModels.clear();
        modelStatus.clear();
    }

    public Map<String, String> getAllStatuses() {
        return Map.copyOf(modelStatus);
    }
}
