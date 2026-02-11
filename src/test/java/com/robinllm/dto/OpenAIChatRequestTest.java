package com.robinllm.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpenAIChatRequestTest {

    private OpenAIChatRequest request;

    @BeforeEach
    void setUp() {
        request = new OpenAIChatRequest();
    }

    @Test
    void testMessageClass() {
        // Test default constructor
        OpenAIChatRequest.Message message = new OpenAIChatRequest.Message();
        assertNull(message.getRole());
        assertNull(message.getContent());

        // Test parameterized constructor
        message = new OpenAIChatRequest.Message("user", "Hello world");
        assertEquals("user", message.getRole());
        assertEquals("Hello world", message.getContent());

        // Test setters
        message.setRole("assistant");
        message.setContent("Hi there!");
        assertEquals("assistant", message.getRole());
        assertEquals("Hi there!", message.getContent());
    }

    @Test
    void testGettersAndSetters() {
        // Test model
        request.setModel("gpt-3.5-turbo");
        assertEquals("gpt-3.5-turbo", request.getModel());

        // Test messages
        List<OpenAIChatRequest.Message> messages = Arrays.asList(
            new OpenAIChatRequest.Message("system", "You are a helpful assistant."),
            new OpenAIChatRequest.Message("user", "Hello!")
        );
        request.setMessages(messages);
        assertEquals(messages, request.getMessages());

        // Test temperature
        request.setTemperature(0.7);
        assertEquals(0.7, request.getTemperature());

        // Test max tokens
        request.setMaxTokens(1000);
        assertEquals(1000, request.getMaxTokens());

        // Test top_p
        request.setTopP(0.9);
        assertEquals(0.9, request.getTopP());

        // Test frequency penalty
        request.setFrequencyPenalty("0.5");
        assertEquals("0.5", request.getFrequencyPenalty());

        // Test presence penalty
        request.setPresencePenalty("0.2");
        assertEquals("0.2", request.getPresencePenalty());

        // Test stop
        List<String> stop = Arrays.asList("\n", "user:");
        request.setStop(stop);
        assertEquals(stop, request.getStop());

        // Test stream
        request.setStream(true);
        assertTrue(request.isStream());
    }

    @Test
    void testWithNullValues() {
        // Test with all null values
        assertNull(request.getModel());
        assertNull(request.getMessages());
        assertNull(request.getTemperature());
        assertNull(request.getMaxTokens());
        assertNull(request.getTopP());
        assertNull(request.getFrequencyPenalty());
        assertNull(request.getPresencePenalty());
        assertNull(request.getStop());
        assertFalse(request.isStream()); // Default should be false
    }

    @Test
    void testWithEmptyValues() {
        // Test with empty values
        request.setMessages(Collections.emptyList());
        request.setStop(Collections.emptyList());
        
        assertEquals(Collections.emptyList(), request.getMessages());
        assertEquals(Collections.emptyList(), request.getStop());
    }

    @Test
    void testCompleteRequest() {
        OpenAIChatRequest completeRequest = new OpenAIChatRequest();
        completeRequest.setModel("gpt-4");
        completeRequest.setMessages(Arrays.asList(
            new OpenAIChatRequest.Message("system", "You are a helpful assistant."),
            new OpenAIChatRequest.Message("user", "Explain quantum computing.")
        ));
        completeRequest.setTemperature(0.5);
        completeRequest.setMaxTokens(2048);
        completeRequest.setTopP(1.0);
        completeRequest.setFrequencyPenalty("0.0");
        completeRequest.setPresencePenalty("0.0");
        completeRequest.setStop(Arrays.asList("\n"));
        completeRequest.setStream(false);

        assertEquals("gpt-4", completeRequest.getModel());
        assertEquals(2, completeRequest.getMessages().size());
        assertEquals("system", completeRequest.getMessages().get(0).getRole());
        assertEquals("You are a helpful assistant.", completeRequest.getMessages().get(0).getContent());
        assertEquals("user", completeRequest.getMessages().get(1).getRole());
        assertEquals("Explain quantum computing.", completeRequest.getMessages().get(1).getContent());
        assertEquals(0.5, completeRequest.getTemperature());
        assertEquals(2048, completeRequest.getMaxTokens());
        assertEquals(1.0, completeRequest.getTopP());
        assertEquals("0.0", completeRequest.getFrequencyPenalty());
        assertEquals("0.0", completeRequest.getPresencePenalty());
        assertEquals(Arrays.asList("\n"), completeRequest.getStop());
        assertFalse(completeRequest.isStream());
    }
}