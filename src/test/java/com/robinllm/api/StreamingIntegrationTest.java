package com.robinllm.api;

import com.robinllm.dto.OpenAIChatRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
@Tag("integration")
public class StreamingIntegrationTest {

    @Test
    void testStreamingEndpoint() {
        OpenAIChatRequest request = new OpenAIChatRequest();
        request.setModel("auto");
        request.setStream(true);
        request.setMessages(java.util.List.of(
            new OpenAIChatRequest.Message("user", "Say hello")
        ));

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/v1/chat/completions")
            .then()
            .statusCode(200)
            .header("Content-Type", containsString("text/event-stream"));
    }

    @Test
    void testNonStreamingEndpoint() {
        OpenAIChatRequest request = new OpenAIChatRequest();
        request.setModel("auto");
        request.setStream(false);
        request.setMessages(java.util.List.of(
            new OpenAIChatRequest.Message("user", "Say hello")
        ));

        given()
            .contentType(ContentType.JSON)
            .body(request)
            .when()
            .post("/v1/chat/completions")
            .then()
            .statusCode(200)
            .header("Content-Type", containsString("application/json"));
    }
}