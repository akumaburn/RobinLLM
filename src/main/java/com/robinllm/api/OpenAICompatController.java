package com.robinllm.api;

import com.robinllm.dto.OpenAIChatRequest;
import com.robinllm.dto.OpenAIChatResponse;
import com.robinllm.dto.OpenAIModelListResponse;
import com.robinllm.model.LLMModel;
import com.robinllm.model.ModelMetrics;
import com.robinllm.model.ModelPool;
import com.robinllm.metrics.MetricsCollector;
import com.robinllm.repository.MetricsRepository;
import com.robinllm.router.LoadBalancer;
import com.robinllm.router.RequestRouter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class OpenAICompatController {
    private static final Logger LOG = LoggerFactory.getLogger(OpenAICompatController.class);

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
    public Response chatCompletions(OpenAIChatRequest request) {
        try {
            LOG.info("Received chat completion request for model: {}, messages: {}", 
                    request.getModel(), 
                    request.getMessages() != null ? request.getMessages().size() : 0);
            
            if (request.getMessages() == null || request.getMessages().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\": \"No messages provided\"}")
                        .build();
            }

            OpenAIChatResponse response = requestRouter.routeRequest(request);
            return Response.ok(response).build();

        } catch (RuntimeException e) {
            LOG.error("Error processing chat completion: {}", e.getMessage(), e);
            OpenAIChatResponse errorResponse = requestRouter.createErrorResponse(e.getMessage());
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(errorResponse).build();
        } catch (Exception e) {
            LOG.error("Unexpected error processing chat completion: {}", e.getMessage(), e);
            OpenAIChatResponse errorResponse = requestRouter.createErrorResponse("Internal server error");
            return Response.serverError().entity(errorResponse).build();
        }
    }

    @GET
    @Path("/models")
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
