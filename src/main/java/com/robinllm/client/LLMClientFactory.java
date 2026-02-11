package com.robinllm.client;

import com.robinllm.model.LLMModel;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LLMClientFactory {

    @Inject
    OpenRouterClient openRouterClient;

    public OpenRouterClient getClient(LLMModel model) {
        return openRouterClient;
    }
}
