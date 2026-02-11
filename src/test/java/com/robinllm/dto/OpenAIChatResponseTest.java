package com.robinllm.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class OpenAIChatResponseTest {

    private OpenAIChatResponse response;

    @BeforeEach
    void setUp() {
        response = new OpenAIChatResponse();
    }

    @Test
    void testChoiceMessageClass() {
        OpenAIChatResponse.Choice.Message message = new OpenAIChatResponse.Choice.Message();
        assertNull(message.getRole());
        assertNull(message.getContent());

        message.setRole("assistant");
        message.setContent("Hello there!");
        assertEquals("assistant", message.getRole());
        assertEquals("Hello there!", message.getContent());
    }

    @Test
    void testChoiceClass() {
        OpenAIChatResponse.Choice choice = new OpenAIChatResponse.Choice();
        assertEquals(0, choice.getIndex()); // Default should be 0
        assertNull(choice.getMessage());
        assertNull(choice.getFinishReason());

        choice.setIndex(1);
        OpenAIChatResponse.Choice.Message message = new OpenAIChatResponse.Choice.Message();
        message.setRole("assistant");
        message.setContent("Test response");
        choice.setMessage(message);
        choice.setFinishReason("stop");

        assertEquals(1, choice.getIndex());
        assertEquals("assistant", choice.getMessage().getRole());
        assertEquals("Test response", choice.getMessage().getContent());
        assertEquals("stop", choice.getFinishReason());
    }

    @Test
    void testUsageClass() {
        OpenAIChatResponse.Usage usage = new OpenAIChatResponse.Usage();
        assertEquals(0, usage.getPromptTokens()); // Default should be 0
        assertEquals(0, usage.getCompletionTokens());
        assertEquals(0, usage.getTotalTokens());

        usage.setPromptTokens(10);
        usage.setCompletionTokens(20);
        usage.setTotalTokens(30);

        assertEquals(10, usage.getPromptTokens());
        assertEquals(20, usage.getCompletionTokens());
        assertEquals(30, usage.getTotalTokens());
    }

    @Test
    void testGettersAndSetters() {
        // Test id
        response.setId("test-id-123");
        assertEquals("test-id-123", response.getId());

        // Test object
        response.setObject("chat.completion");
        assertEquals("chat.completion", response.getObject());

        // Test created
        response.setCreated(1234567890L);
        assertEquals(1234567890L, response.getCreated());

        // Test model
        response.setModel("gpt-3.5-turbo");
        assertEquals("gpt-3.5-turbo", response.getModel());

        // Test provider
        response.setProvider("openai");
        assertEquals("openai", response.getProvider());

        // Test choices
        OpenAIChatResponse.Choice choice = new OpenAIChatResponse.Choice();
        choice.setIndex(0);
        OpenAIChatResponse.Choice.Message message = new OpenAIChatResponse.Choice.Message();
        message.setRole("assistant");
        message.setContent("Hello!");
        choice.setMessage(message);
        response.setChoices(Arrays.asList(choice));

        assertEquals(1, response.getChoices().size());
        assertEquals(0, response.getChoices().get(0).getIndex());
        assertEquals("assistant", response.getChoices().get(0).getMessage().getRole());
        assertEquals("Hello!", response.getChoices().get(0).getMessage().getContent());

        // Test usage
        OpenAIChatResponse.Usage usage = new OpenAIChatResponse.Usage();
        usage.setPromptTokens(10);
        usage.setCompletionTokens(20);
        usage.setTotalTokens(30);
        response.setUsage(usage);

        assertEquals(10, response.getUsage().getPromptTokens());
        assertEquals(20, response.getUsage().getCompletionTokens());
        assertEquals(30, response.getUsage().getTotalTokens());
    }

    @Test
    void testWithNullValues() {
        // Test with all null values
        assertNull(response.getId());
        assertNull(response.getObject());
        assertEquals(0, response.getCreated()); // Default should be 0
        assertNull(response.getModel());
        assertNull(response.getProvider());
        assertNull(response.getChoices());
        assertNull(response.getUsage());
    }

    @Test
    void testWithEmptyValues() {
        // Test with empty values
        response.setChoices(Collections.emptyList());
        assertEquals(Collections.emptyList(), response.getChoices());
    }

    @Test
    void testCompleteResponse() {
        OpenAIChatResponse completeResponse = new OpenAIChatResponse();
        completeResponse.setId("chatcmpl-test123");
        completeResponse.setObject("chat.completion");
        completeResponse.setCreated(1234567890L);
        completeResponse.setModel("gpt-4");
        completeResponse.setProvider("openai");

        OpenAIChatResponse.Choice choice = new OpenAIChatResponse.Choice();
        choice.setIndex(0);
        OpenAIChatResponse.Choice.Message message = new OpenAIChatResponse.Choice.Message();
        message.setRole("assistant");
        message.setContent("This is a test response.");
        choice.setMessage(message);
        choice.setFinishReason("stop");
        completeResponse.setChoices(Arrays.asList(choice));

        OpenAIChatResponse.Usage usage = new OpenAIChatResponse.Usage();
        usage.setPromptTokens(15);
        usage.setCompletionTokens(25);
        usage.setTotalTokens(40);
        completeResponse.setUsage(usage);

        assertEquals("chatcmpl-test123", completeResponse.getId());
        assertEquals("chat.completion", completeResponse.getObject());
        assertEquals(1234567890L, completeResponse.getCreated());
        assertEquals("gpt-4", completeResponse.getModel());
        assertEquals("openai", completeResponse.getProvider());
        assertEquals(1, completeResponse.getChoices().size());
        assertEquals(0, completeResponse.getChoices().get(0).getIndex());
        assertEquals("assistant", completeResponse.getChoices().get(0).getMessage().getRole());
        assertEquals("This is a test response.", completeResponse.getChoices().get(0).getMessage().getContent());
        assertEquals("stop", completeResponse.getChoices().get(0).getFinishReason());
        assertEquals(15, completeResponse.getUsage().getPromptTokens());
        assertEquals(25, completeResponse.getUsage().getCompletionTokens());
        assertEquals(40, completeResponse.getUsage().getTotalTokens());
    }

    @Test
    void testMultipleChoices() {
        OpenAIChatResponse.Choice choice1 = new OpenAIChatResponse.Choice();
        choice1.setIndex(0);
        OpenAIChatResponse.Choice.Message message1 = new OpenAIChatResponse.Choice.Message();
        message1.setContent("Response 1");
        choice1.setMessage(message1);

        OpenAIChatResponse.Choice choice2 = new OpenAIChatResponse.Choice();
        choice2.setIndex(1);
        OpenAIChatResponse.Choice.Message message2 = new OpenAIChatResponse.Choice.Message();
        message2.setContent("Response 2");
        choice2.setMessage(message2);

        response.setChoices(Arrays.asList(choice1, choice2));

        assertEquals(2, response.getChoices().size());
        assertEquals(0, response.getChoices().get(0).getIndex());
        assertEquals("Response 1", response.getChoices().get(0).getMessage().getContent());
        assertEquals(1, response.getChoices().get(1).getIndex());
        assertEquals("Response 2", response.getChoices().get(1).getMessage().getContent());
    }
}