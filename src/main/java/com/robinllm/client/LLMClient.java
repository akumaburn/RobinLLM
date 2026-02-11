package com.robinllm.client;

import com.robinllm.dto.OpenAIChatRequest;
import com.robinllm.dto.OpenAIChatResponse;
import com.robinllm.dto.OpenAIChatStreamResponse;
import java.util.stream.Stream;

public interface LLMClient {
    /**
     * Send a non-streaming chat completion request
     * @param request The chat completion request
     * @return The chat completion response
     * @throws Exception If the request fails
     */
    OpenAIChatResponse sendChatRequest(OpenAIChatRequest request) throws Exception;
    
    /**
     * Send a streaming chat completion request
     * @param request The chat completion request
     * @return A stream of chat completion chunks
     * @throws Exception If the request fails
     */
    Stream<OpenAIChatStreamResponse> sendChatRequestStream(OpenAIChatRequest request) throws Exception;
}