package com.robinllm.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenRouterClientTest {

    @Test
    void testNewClientHasNoRateLimitErrors() {
        OpenRouterClient client = new OpenRouterClient();
        assertEquals(0, client.getRateLimitCount("test-model"));
    }

    @Test
    void testNewClientReturnsZeroForDifferentModels() {
        OpenRouterClient client = new OpenRouterClient();
        assertEquals(0, client.getRateLimitCount("model-1"));
        assertEquals(0, client.getRateLimitCount("model-2"));
        assertEquals(0, client.getRateLimitCount("model-3"));
    }

    @Test
    void testResetClearsAllCounters() {
        OpenRouterClient client = new OpenRouterClient();
        client.resetRateLimitCounters();
        assertEquals(0, client.getRateLimitCount("any-model"));
    }
}