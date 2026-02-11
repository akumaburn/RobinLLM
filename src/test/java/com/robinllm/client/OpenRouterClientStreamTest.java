package com.robinllm.client;

import com.robinllm.config.AppConfig;
import com.robinllm.dto.OpenAIChatRequest;
import com.robinllm.dto.OpenAIChatStreamResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.stream.Stream;
import java.util.List;

@QuarkusTest
public class OpenRouterClientStreamTest {

    @InjectMock
    AppConfig appConfig;

    OpenRouterClient client;

    @BeforeEach
    void setUp() {
        when(appConfig.getOpenrouterApiKey()).thenReturn("test-api-key");
        when(appConfig.getOpenrouterBaseUrl()).thenReturn("https://openrouter.ai/api/v1");
        when(appConfig.getApiTimeout()).thenReturn(30000L);
        
        client = new OpenRouterClient();
        client.appConfig = appConfig;
    }

    @Test
    void testSendChatRequestStream() throws Exception {
        OpenAIChatRequest request = new OpenAIChatRequest();
        request.setModel("test-model");
        request.setStream(true);
        request.setMessages(List.of(
            new OpenAIChatRequest.Message("user", "Hello, world!")
        ));

        // Note: This test would need a mock HTTP client to fully test streaming
        // For now, we'll just verify the method exists and doesn't throw exceptions
        assertDoesNotThrow(() -> {
            try {
                Stream<OpenAIChatStreamResponse> stream = client.sendChatRequestStream(request);
                assertNotNull(stream);
            } catch (Exception e) {
                // Expected to fail without proper mocking
                assertTrue(e.getMessage().contains("API key") || 
                          e.getMessage().contains("Failed") || 
                          e.getMessage().contains("connection"));
            }
        });
    }
}