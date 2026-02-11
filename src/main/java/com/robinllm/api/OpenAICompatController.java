package com.robinllm.api;

import com.robinllm.dto.OpenAIChatRequest;
import com.robinllm.dto.OpenAIChatResponse;
import com.robinllm.dto.OpenAIChatStreamResponse;
import com.robinllm.dto.OpenAIModelListResponse;
import com.robinllm.model.LLMModel;
import com.robinllm.model.ModelPool;
import com.robinllm.metrics.MetricsCollector;
import com.robinllm.router.LoadBalancer;
import com.robinllm.router.RequestRouter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("/v1")
@ApplicationScoped
public class OpenAICompatController {
    private static final Logger LOG = LoggerFactory.getLogger(OpenAICompatController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    RequestRouter requestRouter;

    @Inject
    ModelPool modelPool;

    @Inject
    MetricsCollector metricsCollector;

    @Inject
    LoadBalancer loadBalancer;

    @POST
    @Path("/chat/completions")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response chatCompletions(OpenAIChatRequest request) {
        try {
            LOG.info("Received chat completion request for model: {}, messages: {}, stream: {}", 
                    request.getModel(), 
                    request.getMessages() != null ? request.getMessages().size() : 0,
                    request.isStream());
            
            if (request.getMessages() == null || request.getMessages().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\": \"No messages provided\"}")
                        .build();
            }

            // Handle streaming request
            if (request.isStream()) {
                return handleStreamingRequest(request);
            }

            // Handle regular (non-streaming) request
            OpenAIChatResponse response = requestRouter.routeRequest(request);
            return Response.ok(response).build();

        } catch (RuntimeException e) {
            LOG.error("Error processing chat completion: {}", e.getMessage(), e);
            if (request.isStream()) {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .type(MediaType.SERVER_SENT_EVENTS)
                        .entity(createErrorStreamingOutput(e.getMessage()))
                        .build();
            } else {
                OpenAIChatResponse errorResponse = requestRouter.createErrorResponse(e.getMessage());
                return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(errorResponse).build();
            }
        } catch (Exception e) {
            LOG.error("Unexpected error processing chat completion: {}", e.getMessage(), e);
            if (request.isStream()) {
                return Response.serverError()
                        .type(MediaType.SERVER_SENT_EVENTS)
                        .entity(createErrorStreamingOutput("Internal server error"))
                        .build();
            } else {
                OpenAIChatResponse errorResponse = requestRouter.createErrorResponse("Internal server error");
                return Response.serverError().entity(errorResponse).build();
            }
        }
    }

    private Response handleStreamingRequest(OpenAIChatRequest request) {
        try {
            // Get the stream of responses from the router
            Stream<OpenAIChatStreamResponse> streamResponse = requestRouter.routeRequestStream(request);
            
            // Create StreamingOutput to handle the SSE stream
            StreamingOutput streamingOutput = new StreamingOutput() {
                @Override
                public void write(OutputStream output) throws IOException {
                    PrintWriter writer = new PrintWriter(output, true, StandardCharsets.UTF_8);
                    
                    try {
                        streamResponse.forEach(chunk -> {
                            try {
                                String json = objectMapper.writeValueAsString(chunk);
                                writer.print("data: " + json + "\n\n");
                                writer.flush();
                            } catch (Exception e) {
                                LOG.error("Error serializing chunk: {}", e.getMessage());
                            }
                        });
                        
                        // Send the DONE marker
                        writer.print("data: [DONE]\n\n");
                        writer.flush();
                    } catch (Exception e) {
                        LOG.error("Error writing stream: {}", e.getMessage());
                        // Try to send error message
                        try {
                            String errorJson = objectMapper.writeValueAsString(createErrorChunk(e.getMessage()));
                            writer.print("data: " + errorJson + "\n\n");
                            writer.flush();
                        } catch (Exception ex) {
                            LOG.error("Error sending error chunk", ex);
                        }
                    }
                }
            };
            
            return Response.ok(streamingOutput)
                    .type(MediaType.SERVER_SENT_EVENTS)
                    .header("Cache-Control", "no-cache")
                    .header("Connection", "keep-alive")
                    .header("X-Accel-Buffering", "no")
                    .build();
                    
        } catch (Exception e) {
            LOG.error("Error setting up streaming response: {}", e.getMessage(), e);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .type(MediaType.SERVER_SENT_EVENTS)
                    .entity(createErrorStreamingOutput(e.getMessage()))
                    .build();
        }
    }

    private StreamingOutput createErrorStreamingOutput(String errorMessage) {
        return new StreamingOutput() {
            @Override
            public void write(OutputStream output) throws IOException {
                PrintWriter writer = new PrintWriter(output, true, StandardCharsets.UTF_8);
                try {
                    String errorJson = objectMapper.writeValueAsString(createErrorChunk(errorMessage));
                    writer.print("data: " + errorJson + "\n\n");
                    writer.print("data: [DONE]\n\n");
                    writer.flush();
                } catch (Exception e) {
                    LOG.error("Error creating error SSE stream", e);
                    writer.print("data: {\"error\": \"Error creating error response\"}\n\n");
                    writer.flush();
                }
            }
        };
    }

    private OpenAIChatStreamResponse createErrorChunk(String errorMessage) {
        OpenAIChatStreamResponse errorResponse = new OpenAIChatStreamResponse();
        errorResponse.setId("err-" + System.currentTimeMillis());
        errorResponse.setObject("chat.completion.chunk");
        errorResponse.setCreated(Instant.now().getEpochSecond());
        errorResponse.setModel("error");

        OpenAIChatStreamResponse.Choice choice = new OpenAIChatStreamResponse.Choice();
        choice.setIndex(0);
        choice.setFinishReason("error");

        OpenAIChatStreamResponse.Choice.Delta delta = new OpenAIChatStreamResponse.Choice.Delta();
        delta.setRole("assistant");
        delta.setContent(errorMessage);
        choice.setDelta(delta);

        errorResponse.setChoices(List.of(choice));
        return errorResponse;
    }

    @GET
    @Path("/models")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listModels() {
        try {
            OpenAIModelListResponse response = new OpenAIModelListResponse();
            response.setObject("list");

            List<OpenAIModelListResponse.ModelData> modelDataList = modelPool.getAvailableModels().stream()
                    .map(model -> {
                        OpenAIModelListResponse.ModelData data = new OpenAIModelListResponse.ModelData();
                        data.setId(model.getId());
                        data.setObject("model");
                        data.setCreated(model.getCreatedAt() != null ? model.getCreatedAt().toEpochSecond(java.time.ZoneOffset.UTC) : Instant.now().getEpochSecond());
                        data.setOwnedBy(model.getProvider());
                        return data;
                    })
                    .collect(Collectors.toList());

            response.setData(modelDataList);
            return Response.ok(response).build();

        } catch (Exception e) {
            LOG.error("Error listing models: {}", e.getMessage(), e);
            return Response.serverError().entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }

    @GET
    @Path("/models/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getModel(@PathParam("id") String id) {
        try {
            LLMModel model = modelPool.getModel(id);
            if (model == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\": \"Model not found\"}")
                        .build();
            }

            OpenAIModelListResponse response = new OpenAIModelListResponse();
            response.setObject("model");

            OpenAIModelListResponse.ModelData data = new OpenAIModelListResponse.ModelData();
            data.setId(model.getId());
            data.setObject("model");
            data.setCreated(model.getCreatedAt() != null ? model.getCreatedAt().toEpochSecond(java.time.ZoneOffset.UTC) : Instant.now().getEpochSecond());
            data.setOwnedBy(model.getProvider());

            response.setData(List.of(data));
            return Response.ok(response).build();

        } catch (Exception e) {
            LOG.error("Error getting model: {}", e.getMessage(), e);
            return Response.serverError().entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }

    @GET
    @Path("/models/{id}/metrics")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getModelMetrics(@PathParam("id") String id) {
        try {
            var metricsOpt = metricsCollector.getLatestMetrics(id);
            if (metricsOpt.isEmpty()) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("{\"error\": \"No metrics found for model\"}")
                        .build();
            }

            return Response.ok(metricsOpt.get()).build();
        } catch (Exception e) {
            LOG.error("Error getting model metrics: {}", e.getMessage(), e);
            return Response.serverError().entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }

    @GET
    @Path("/stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStats() {
        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("total_models", modelPool.size());
            stats.put("active_models", modelPool.getActiveModels().size());
            stats.put("free_models", modelPool.getFreeModels().size());
            stats.put("total_requests", requestRouter.getTotalRequests());
            stats.put("total_failures", requestRouter.getTotalFailures());
            stats.put("success_rate", String.format("%.2f%%", requestRouter.getSuccessRate() * 100));
            stats.put("uptime", System.currentTimeMillis() / 1000);

            return Response.ok(stats).build();
        } catch (Exception e) {
            LOG.error("Error getting stats: {}", e.getMessage(), e);
            return Response.serverError().entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }

    @POST
    @Path("/stats/reset")
    @Produces(MediaType.APPLICATION_JSON)
    public Response resetStats() {
        try {
            requestRouter.resetStats();
            loadBalancer.resetAllCircuitBreakers();
            return Response.ok("{\"message\": \"Stats reset successfully\"}").build();
        } catch (Exception e) {
            LOG.error("Error resetting stats: {}", e.getMessage(), e);
            return Response.serverError().entity("{\"error\": \"" + e.getMessage() + "\"}").build();
        }
    }

    @GET
    @Path("/health")
    @Produces(MediaType.TEXT_PLAIN)
    public Response health() {
        return Response.ok("OK").build();
    }

    @GET
    @Path("/")
    @Produces(MediaType.TEXT_PLAIN)
    public Response root() {
        return Response.ok("RobinLLM - Intelligent LLM Routing Service\n\nAll endpoints are prefixed with /v1\n\nEndpoints:\n" +
                "  POST /v1/chat/completions - Chat completions\n" +
                "  GET  /v1/models - List models\n" +
                "  GET  /v1/models/{id} - Get model details\n" +
                "  GET  /v1/models/{id}/metrics - Get model metrics\n" +
                "  GET  /v1/stats - Routing statistics\n" +
                "  POST /v1/stats/reset - Reset statistics\n" +
                "  GET  /v1/health - Health check\n" +
                "  GET  /v1/ - This endpoint").build();
    }
}
