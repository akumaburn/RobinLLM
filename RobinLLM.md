# RobinLLM - Technical Documentation

## Table of Contents
- [Overview](#overview)
- [Architecture](#architecture)
- [Components](#components)
- [API Documentation](#api-documentation)
- [Configuration](#configuration)
- [Development](#development)
- [Deployment](#deployment)
- [Performance](#performance)

## Overview

RobinLLM is an intelligent Large Language Model (LLM) routing service that automatically selects the best performing model for each request. It provides an OpenAI-compatible API while intelligently routing requests across multiple free LLM providers.

### Key Features
- **Intelligent Routing**: Automatically selects models based on latency, success rate, and rate limit proximity
- **Performance Monitoring**: Tracks metrics for all models and routes to best performers
- **Circuit Breaker**: Automatically stops routing to failing models with automatic recovery
- **Load Balancing**: Distributes requests across top-performing models using round-robin
- **Auto-Discovery**: Scrapes available models from OpenRouter API
- **OpenAI Compatible**: Drop-in replacement for OpenAI API clients

## Architecture

### System Design

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│   Client    │────▶│   Router     │────▶│  Model Pool │
└─────────────┘     └──────────────┘     └─────────────┘
                           │                     │
                           │                     ▼
                           │              ┌──────────────┐
                           │              │   Metrics     │
                           │              │  Collector   │
                           │              └──────────────┘
                           ▼
                    ┌──────────────┐
                    │ Load Balancer │
                    └──────────────┘
                           │
           ┌───────────────┼───────────────┐
           ▼               ▼               ▼
    ┌──────────┐    ┌──────────┐   ┌──────────┐
    │ Model A  │    │ Model B  │   │ Model C  │
    └──────────┘    └──────────┘   └──────────┘
```

### Data Flow

1. **Request Ingestion**: Client sends request to `/v1/chat/completions`
2. **Model Selection**: Router selects best models based on current metrics
3. **Load Balancing**: Load balancer picks model from top performers using round-robin
4. **Circuit Breaker Check**: Verifies selected model is not in open circuit state
5. **Request Execution**: Forwards request to selected model endpoint
6. **Response Handling**: Returns OpenAI-compatible response to client
7. **Metrics Collection**: Updates performance metrics for the used model
8. **Circuit Breaker Update**: Adjusts circuit breaker state based on success/failure

## Components

### 1. OpenAICompatController
**Location**: `com.robinllm.api.OpenAICompatController`

REST controller providing OpenAI-compatible API endpoints.

**Endpoints**:
- `POST /v1/chat/completions` - Main chat completion endpoint
- `GET /v1/models` - List all available models
- `GET /v1/models/{id}` - Get specific model details
- `GET /v1/models/{id}/metrics` - Get model performance metrics
- `GET /v1/stats` - Get system statistics
- `POST /v1/stats/reset` - Reset all statistics
- `GET /v1/health` - Health check
- `GET /v1/` - Service information

**Responsibilities**:
- Request validation
- Response formatting (OpenAI-compatible)
- Error handling and HTTP status codes
- Metrics and statistics aggregation

### 2. RequestRouter
**Location**: `com.robinllm.router.RequestRouter`

Core routing logic for LLM requests.

**Methods**:
- `routeRequest(OpenAIChatRequest)` - Routes request to best available model
- `selectBestModel()` - Selects model based on scoring algorithm
- `createErrorResponse(String)` - Creates error response in OpenAI format
- `getTotalRequests()` - Returns total requests served
- `getTotalFailures()` - Returns total failed requests
- `getSuccessRate()` - Returns current success rate
- `resetStats()` - Resets all routing statistics

**Routing Algorithm**:
```
score = (1 - latency_norm) * weight_latency +
         success_rate * weight_success +
         (1 - rate_limit_proximity) * weight_rate_limit
```

Where:
- `latency_norm` = normalized average latency (0-1)
- `success_rate` = recent success rate (0-1)
- `rate_limit_proximity` = how close to rate limit (0-1)
- Default weights: latency=0.6, success=0.3, rate_limit=0.1

### 3. LoadBalancer
**Location**: `com.robinllm.router.LoadBalancer`

Implements load balancing with circuit breaker pattern.

**Methods**:
- `selectModel(List<LLMModel>)` - Selects model using round-robin
- `recordSuccess(String)` - Records successful request for model
- `recordFailure(String)` - Records failed request for model
- `getModelHealthScore(String)` - Returns current health score (0-1)
- `resetCircuitBreaker(String)` - Resets circuit breaker for specific model
- `resetAllCircuitBreakers()` - Resets all circuit breakers

**Circuit Breaker States**:
- **CLOSED**: Normal operation, requests flow through
- **OPEN**: Model has failed too many times, requests blocked
- **HALF_OPEN**: Testing if model has recovered

**Thresholds**:
- Failure threshold: 50% (configurable)
- Recovery cooldown: 5 minutes
- Minimum requests before opening: 5

### 4. ModelPool
**Location**: `com.robinllm.model.ModelPool`

Manages collection of available LLM models.

**Methods**:
- `addModel(LLMModel)` - Adds model to pool
- `removeModel(String)` - Removes model from pool
- `getModel(String)` - Retrieves model by ID
- `getModelStatus(String)` - Gets model status
- `getAvailableModels()` - Lists all available models
- `getActiveModels()` - Lists active models
- `getFreeModels()` - Lists free models
- `size()` - Returns total model count

### 5. ModelSelector
**Location**: `com.robinllm.router.ModelSelector`

Ranks models based on performance metrics.

**Methods**:
- `selectBestModels(List<LLMModel>)` - Returns top N models by score
- `calculateScore(LLMModel)` - Calculates model score using weights

### 6. MetricsCollector
**Location**: `com.robinllm.metrics.MetricsCollector`

Collects and aggregates performance metrics.

**Methods**:
- `recordRequest(String, long, boolean)` - Records request latency and success
- `getLatestMetrics(String)` - Retrieves latest metrics for model
- `calculatePerformanceMetrics(String)` - Computes P95, P99, etc.

### 7. OpenRouterClient
**Location**: `com.robinllm.client.OpenRouterClient`

HTTP client for OpenRouter API.

**Methods**:
- `chatCompletions(OpenAIChatRequest)` - Sends chat completion request
- `getModelInfo(String)` - Retrieves model information
- `resetRateLimiters()` - Resets rate limit tracking

**Rate Limiting**:
- Tracks requests per model
- Implements exponential backoff
- Prevents quota exhaustion

### 8. OpenRouterScraper
**Location**: `com.robinllm.scraper.OpenRouterScraper`

Discovers and scrapes available models from OpenRouter.

**Methods**:
- `scrapeModels(String, String)` - Fetches models from API
- `saveModels(List<LLMModel>)` - Persists models to database

### 9. Database Components

**ModelRepository** - `com.robinllm.repository.ModelRepository`
- `save(LLMModel)` - Saves/updates model
- `findById(String)` - Retrieves model by ID
- `findAll()` - Lists all models
- `findByStatus(String)` - Filters by status
- `delete(String)` - Removes model

**MetricsRepository** - `com.robinllm.repository.MetricsRepository`
- `save(ModelMetrics)` - Saves metrics snapshot
- `findById(long)` - Retrieves metrics by ID
- `findLatestByModelId(String)` - Gets most recent metrics
- `findByModelId(String)` - Gets all metrics for model
- `findSince(LocalDateTime)` - Gets metrics after timestamp
- `findLatestForAllModels()` - Gets latest metrics per model
- `deleteOlderThan(LocalDateTime)` - Cleanup old metrics

## API Documentation

### Request Format

**POST /v1/chat/completions**

```json
{
  "model": "auto | openrouter/free | meta-llama/llama-3-8b-instruct",
  "messages": [
    {
      "role": "user",
      "content": "Hello, how are you?"
    }
  ],
  "temperature": 0.7,
  "max_tokens": 1000,
  "top_p": 0.9
}
```

### Response Format

**Success Response**

```json
{
  "id": "chatcmpl-abc123",
  "object": "chat.completion",
  "created": 1234567890,
  "model": "meta-llama/llama-3-8b-instruct",
  "provider": "meta-llama",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "I'm doing well, thank you for asking!"
      },
      "finish_reason": "stop"
    }
  ],
  "usage": {
    "prompt_tokens": 10,
    "completion_tokens": 20,
    "total_tokens": 30
  }
}
```

**Error Response**

```json
{
  "id": "err-abc123",
  "object": "chat.completion",
  "created": 1234567890,
  "model": "error",
  "choices": [
    {
      "index": 0,
      "message": {
        "role": "assistant",
        "content": "Error message here"
      },
      "finish_reason": "error"
    }
  ],
  "usage": {
    "prompt_tokens": 0,
    "completion_tokens": 0,
    "total_tokens": 0
  }
}
```

### Model List Response

**GET /v1/models**

```json
{
  "object": "list",
  "data": [
    {
      "id": "meta-llama/llama-3-8b-instruct",
      "object": "model",
      "created": 1234567890,
      "owned_by": "meta-llama"
    }
  ]
}
```

### Model Metrics Response

**GET /v1/models/{id}/metrics**

```json
{
  "id": 1,
  "modelId": "meta-llama/llama-3-8b-instruct",
  "avgLatencyMs": 450.5,
  "successRate": 0.95,
  "errorRate": 0.05,
  "p95LatencyMs": 800.0,
  "p99LatencyMs": 1200.0,
  "requestsPerSecond": 2.5,
  "measuredAt": "2025-02-10T12:00:00"
}
```

### Statistics Response

**GET /v1/stats**

```json
{
  "total_models": 181,
  "active_models": 150,
  "free_models": 75,
  "total_requests": 10000,
  "total_failures": 250,
  "success_rate": "97.50%",
  "uptime": 1234567
}
```

## Configuration

### Application Properties

**File**: `src/main/resources/application.properties`

```properties
# Application
quarkus.application.name=robinllm
quarkus.http.port=8080

# Scraper
scraper.enabled=true
scraper.interval=1h
scraper.openrouter.url=https://openrouter.ai/models
scraper.filter=free

# Metrics
metrics.enabled=true
metrics.interval=1h
metrics.test.prompts=What is 2+2?,Explain photosynthesis
metrics.top-models=3

# Router
router.weight.latency=0.6
router.weight.success=0.3
router.weight.rate-limit=0.1
router.circuit-breaker.threshold=0.5
router.retry.max=3
router.retry.backoff=1000

# API
api.compatibility=openai
api.max-tokens=4096
api.timeout=30000

# OpenRouter
openrouter.api-key=${OPENROUTER_API_KEY}
openrouter.base-url=https://openrouter.ai/api/v1
```

### Environment Variables

- `OPENROUTER_API_KEY` - Your OpenRouter API key (required)

### Configuration Weights

The router uses configurable weights to prioritize different factors:

| Factor | Default Weight | Description |
|--------|---------------|-------------|
| Latency | 0.6 | Lower latency = higher score |
| Success Rate | 0.3 | Higher success = higher score |
| Rate Limit Proximity | 0.1 | Farther from limit = higher score |

Adjust weights based on your priorities:
- Prioritize speed: Increase `weight.latency`
- Prioritize reliability: Increase `weight.success`
- Prioritize quota: Increase `weight.rate-limit`

## Development

### Tech Stack

- **Java 21** - Latest LTS with virtual threads support
- **Quarkus 3.6.4** - Cloud-native framework
- **Maven** - Build and dependency management
- **SQLite** - Embedded database for metrics persistence
- **JAX-RS** - Jakarta REST API specification
- **Jackson** - JSON serialization/deserialization

### Project Structure

```
robinllm/
├── src/main/java/com/robinllm/
│   ├── api/                    # REST controllers
│   │   └── OpenAICompatController.java
│   ├── client/                  # External API clients
│   │   ├── LLMClientFactory.java
│   │   └── OpenRouterClient.java
│   ├── config/                  # Configuration classes
│   │   └── AppConfig.java
│   ├── dto/                     # Data Transfer Objects
│   │   ├── OpenAIChatRequest.java
│   │   └── OpenAIChatResponse.java
│   ├── metrics/                 # Metrics collection
│   │   ├── MetricsCollector.java
│   │   └── MetricsScheduler.java
│   ├── model/                   # Domain models
│   │   ├── LLMModel.java
│   │   ├── ModelMetrics.java
│   │   ├── ModelPool.java
│   │   └── PerformanceMetrics.java
│   ├── repository/              # Data access
│   │   ├── BaseRepository.java
│   │   ├── MetricsRepository.java
│   │   └── ModelRepository.java
│   ├── router/                  # Routing logic
│   │   ├── CircuitBreakerState.java
│   │   ├── LoadBalancer.java
│   │   ├── ModelHealth.java
│   │   ├── ModelSelector.java
│   │   └── RequestRouter.java
│   ├── scraper/                 # Model discovery
│   │   ├── ModelDetailsExtractor.java
│   │   ├── OpenRouterScraper.java
│   │   └── ScraperScheduler.java
│   ├── startup/                 # Application startup
│   │   ├── DatabaseInitializer.java
│   │   └── StartupInitializer.java
│   └── RobinLLMMain.java        # Application entry point
├── src/main/resources/
│   ├── application.properties       # Configuration
│   └── db/migration/
│       └── V1__Initial_schema.sql  # Database schema
└── src/test/java/com/robinllm/  # Test suite
```

### Building

```bash
# Compile
mvn clean compile

# Run tests
mvn test

# Build package
mvn clean package

# Skip tests during build
mvn clean package -DskipTests

# Run in dev mode
mvn quarkus:dev
```

### Testing

Unit tests cover:
- Repository operations
- DTO validation
- Client communication
- Routing algorithms
- Load balancing logic

Run specific test classes:
```bash
mvn test -Dtest=OpenRouterClientTest
mvn test -Dtest=ModelPoolTest
mvn test -Dtest=MetricsRepositoryTest
```

### Adding New LLM Providers

To add support for a new LLM provider:

1. Create client class implementing LLM client interface
2. Add provider-specific metrics collection
3. Update LoadBalancer to include new provider models
4. Add configuration properties for provider
5. Update scraper to discover provider's models

## Deployment

### Requirements

- Java 21 or later
- Maven 3.8+
- 512MB RAM minimum (2GB recommended)
- 100MB disk space

### Quick Start

```bash
# Build
mvn clean package

# Set API key
export OPENROUTER_API_KEY=your_api_key_here

# Run
java -jar target/quarkus-app/quarkus-run.jar
```

### Docker Deployment

```dockerfile
FROM quay.io/quarkus/quarkus-micro-image:2.0
WORKDIR /work
COPY target/quarkus-app/quarkus-run.jar application.properties ./
EXPOSE 8080
CMD ["java", "-jar", "quarkus-run.jar"]
```

```bash
# Build Docker image
docker build -t robinllm .

# Run container
docker run -p 8080:8080 -e OPENROUTER_API_KEY=your_key robinllm
```

### Production Considerations

1. **Database Persistence**: SQLite is embedded - ensure backup strategy
2. **Rate Limits**: Monitor OpenRouter API quotas
3. **Circuit Breaker**: Adjust thresholds based on production patterns
4. **Logging**: Configure appropriate log levels
5. **Scaling**: Can horizontally scale behind load balancer

## Performance

### Metrics Collection

Metrics are collected:
- **Per Request**: Latency, success/failure, error type
- **Per Model**: Average latency, P95/P99, success rate, RPS
- **System**: Total requests, total failures, overall success rate, uptime

### Optimization Strategies

1. **Connection Pooling**: Reuse HTTP connections
2. **Caching**: Cache model list and metadata
3. **Async Operations**: Use virtual threads for I/O
4. **Bulk Metrics**: Batch metric updates to database

### Benchmarks

Expected performance on typical hardware:
- Request routing: < 1ms
- Model selection: < 10ms
- End-to-end latency: Model latency + ~50ms overhead

## Troubleshooting

### Common Issues

**No models available**
- Verify API key is set
- Check scraper logs for errors
- Confirm OpenRouter API is accessible

**High error rates**
- Review `/v1/stats` for model-specific metrics
- Check circuit breaker status
- Verify network connectivity

**Slow responses**
- Check model latency metrics via `/v1/models/{id}/metrics`
- Consider adjusting router weights
- Verify database performance

### Logging

Configure logging in `application.properties`:

```properties
# Log level
quarkus.log.level=INFO
quarkus.log.category."com.robinllm".level=DEBUG

# Log format
quarkus.log.console.format=%d{HH:mm:ss} %-5p [%c{2.}] (%t) %s%e%n
```

## Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Write tests for changes
4. Submit a pull request

## License

MIT License - See [LICENSE](LICENSE) file for details.
