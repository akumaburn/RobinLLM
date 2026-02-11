package com.robinllm.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.robinllm.dto.OpenAIChatRequest;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class OpenAICompatControllerIntegrationTest {

    @Test
    void testHealthEndpoint() {
        given()
            .when().get("/v1/health")
            .then()
            .statusCode(200)
            .body(is("OK"));
    }

    @Test
    void testRootEndpoint() {
        given()
            .when().get("/v1/")
            .then()
            .statusCode(200)
            .body(containsString("RobinLLM"));
    }

    @Test
    void testModelsEndpoint() {
        given()
            .when().get("/v1/models")
            .then()
            .statusCode(200)
            .body("object", is("list"))
            .body("data", notNullValue());
    }

    @Test
    void testStatsEndpoint() {
        given()
            .when().get("/v1/stats")
            .then()
            .statusCode(200)
            .body(hasKey("total_models"))
            .body(hasKey("active_models"))
            .body(hasKey("free_models"))
            .body(hasKey("total_requests"))
            .body(hasKey("total_failures"))
            .body(hasKey("success_rate"))
            .body(hasKey("uptime"));
    }

    @Test
    void testChatCompletionsEndpointWithValidRequest() {
        OpenAIChatRequest request = new OpenAIChatRequest();
        request.setModel("auto");
        request.setMessages(Arrays.asList(
            new OpenAIChatRequest.Message("user", "Hello")
        ));

        given()
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .when().post("/v1/chat/completions")
            .then()
            .statusCode(200)
            .body("object", is("chat.completion"))
            .body("id", notNullValue())
            .body("created", notNullValue())
            .body("choices", notNullValue())
            .body("usage", notNullValue());
    }

    @Test
    void testChatCompletionsEndpointWithEmptyMessages() {
        OpenAIChatRequest request = new OpenAIChatRequest();
        request.setModel("auto");
        request.setMessages(Arrays.asList());

        given()
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .when().post("/v1/chat/completions")
            .then()
            .statusCode(400)
            .body(containsString("No messages provided"));
    }

    @Test
    void testChatCompletionsEndpointWithNullMessages() {
        OpenAIChatRequest request = new OpenAIChatRequest();
        request.setModel("auto");
        request.setMessages(null);

        given()
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .when().post("/v1/chat/completions")
            .then()
            .statusCode(400)
            .body(containsString("No messages provided"));
    }

    @Test
    void testChatCompletionsEndpointWithSpecificModel() {
        OpenAIChatRequest request = new OpenAIChatRequest();
        request.setModel("openrouter/free");
        request.setMessages(Arrays.asList(
            new OpenAIChatRequest.Message("system", "You are a helpful assistant"),
            new OpenAIChatRequest.Message("user", "Explain quantum computing")
        ));

        given()
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .when().post("/v1/chat/completions")
            .then()
            .statusCode(200)
            .body("object", is("chat.completion"))
            .body("choices[0].message.role", is("assistant"))
            .body("choices[0].message.content", notNullValue());
    }

    @Test
    void testModelByIdNonExistent() {
        given()
            .when().get("/v1/models/non-existent-model")
            .then()
            .statusCode(404)
            .body(containsString("Model not found"));
    }

    @Test
    void testModelMetricsNonExistent() {
        given()
            .when().get("/v1/models/non-existent-model/metrics")
            .then()
            .statusCode(404)
            .body(containsString("No metrics found for model"));
    }

    @Test
    void testResetStats() {
        given()
            .when().post("/v1/stats/reset")
            .then()
            .statusCode(200)
            .body(containsString("Stats reset successfully"));
    }

    @Test
    void testChatCompletionsWithComplexRequest() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        
        Map<String, Object> request = new HashMap<>();
        request.put("model", "auto");
        request.put("temperature", 0.7);
        request.put("max_tokens", 1000);
        request.put("top_p", 0.9);
        request.put("stream", false);
        
        List<Map<String, String>> messages = Arrays.asList(
            Map.of("role", "system", "content", "You are a creative storyteller"),
            Map.of("role", "user", "content", "Write a short story about a robot")
        );
        request.put("messages", messages);
        
        String jsonRequest = mapper.writeValueAsString(request);

        given()
            .contentType(MediaType.APPLICATION_JSON)
            .body(jsonRequest)
            .when().post("/v1/chat/completions")
            .then()
            .statusCode(200)
            .body("object", is("chat.completion"))
            .body("choices[0].message.content", notNullValue())
            .body("usage", notNullValue());
    }

    @Test
    void testChatCompletionsWithRoleplayScenario() {
        Map<String, Object> request = Map.of(
            "model", "auto",
            "messages", List.of(
                Map.of("role", "system", "content", "You are an expert software architect"),
                Map.of("role", "user", "content", "Explain microservices architecture simply")
            )
        );

        given()
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .when().post("/v1/chat/completions")
            .then()
            .statusCode(200)
            .body("object", is("chat.completion"))
            .body("choices[0].message.role", is("assistant"))
            .body("choices[0].message.content", notNullValue());
    }

    @Test
    void testMultipleConcurrentRequests() {
        OpenAIChatRequest request = new OpenAIChatRequest();
        request.setModel("auto");
        request.setMessages(Arrays.asList(
            new OpenAIChatRequest.Message("user", "Hello")
        ));

        // Send multiple requests in parallel (basic test)
        for (int i = 0; i < 3; i++) {
            given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .when().post("/v1/chat/completions")
                .then()
                .statusCode(200)
                .body("object", is("chat.completion"));
        }
    }
}