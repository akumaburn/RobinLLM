# Robin LLM

An intelligent LLM routing service that automatically discovers free language models and routes requests to the best-performing ones.

## Overview

Robin LLM scrapes OpenRouter's website for free LLM options, continuously tests their performance, and provides an OpenAI-compatible API that intelligently routes requests to the best available model. Think of it as a smart load balancer for free LLMs.

## Features

- **Automatic Discovery**: Scans OpenRouter for free models, adds them to the pool automatically
- **Performance Monitoring**: Continuously tests and measures model performance (latency, success rate, errors)
- **Intelligent Routing**: Routes requests to the best-performing models using a weighted scoring algorithm
- **Parallel Streaming Race**: Streaming requests in `auto` mode race up to 3 top models in parallel; the first model to emit a chunk wins and the other in-flight HTTP requests are cancelled immediately so the losing providers stop generating tokens
- **OpenAI Compatible**: Drop-in replacement for OpenAI API with standard `/v1/chat/completions` endpoint
- **Zero Configuration**: Works out of the box with automatic model discovery
- **Built with Java 21**: Uses virtual threads for high-performance concurrent operations
- **Lightweight**: Built on Quarkus for minimal resource usage and fast startup

## How It Works

1. **Scraping**: Every hour, Robin LLM scrapes OpenRouter's model page to discover new free models
2. **Testing**: Each model is tested with standardized prompts to measure performance
3. **Scoring**: Models are scored based on response time (60%), success rate (30%), and rate limit proximity (10%)
4. **Routing**: Incoming requests are automatically routed to the best-performing available model
5. **Streaming Race (auto mode)**: Streaming requests are dispatched to the top 3 candidate models in parallel. The first model whose first chunk arrives wins; every other in-flight HTTP `Call` is cancelled the instant the winner is declared, so the losing providers stop generating tokens and we stop paying for their compute. Workers that hadn't finished opening their connection yet self-cancel via the same coordinator.
6. **Failover**: If a model fails or degrades, requests automatically failover to the next best model

## Technology Stack

- **Java 21**: Latest LTS with virtual threads
- **Quarkus**: Fast, lightweight framework for low-latency API
- **SQLite**: Embedded database for metrics persistence
- **Maven**: Build and dependency management
- **Jsoup**: HTML scraping for model discovery
- **Retrofit/OkHttp**: HTTP client for LLM API communication
- **RestEasy Reactive**: Reactive REST API framework

## Advanced Features

- **Circuit Breaker**: Automatically stops routing to failing models and retries after cooldown
- **Automatic Failover**: Seamlessly switches to next best model on failure
- **Round-Robin Load Balancing**: Distributes requests across top-performing models
- **Streaming Race with Aggressive Cancellation**: For `auto` streaming requests, RobinLLM races up to 3 models concurrently and aborts the losing okhttp `Call`s the moment the winner's first chunk arrives - even if a loser is still mid-handshake or mid-headers. The race only fails when *all* contestants fail, not when the first one does, so a single fast failure doesn't take down the request.
- **Performance Metrics**: Tracks latency, success rate, and requests per second
- **Configurable Weights**: Customize the scoring algorithm for model selection

## Getting Started

### Prerequisites

- Java 21 or later
- Maven 3.8+
- OpenRouter API key (get one at https://openrouter.ai/keys)

### Installation

```bash
# Clone the repository
git clone https://github.com/yourusername/robinllm.git
cd robinllm

# Build the project
mvn clean package

# Set your OpenRouter API key (get one at https://openrouter.ai/keys)
export OPENROUTER_API_KEY=your_api_key_here

# Run the application
java -jar target/quarkus-app/quarkus-run.jar
```

### Usage

Once running, use the OpenAI-compatible API:

```bash
curl http://localhost:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "auto",
    "messages": [
      {"role": "user", "content": "Hello, how are you?"}
    ]
  }'
```

Use `"model": "auto"` to let Robin LLM automatically select the best model, or specify a model ID from `/v1/models`.

Additional examples:
```bash
# List available models
curl http://localhost:8080/v1/models

# Get model metrics
curl http://localhost:8080/v1/models/{model_id}/metrics

# Get system statistics
curl http://localhost:8080/v1/stats
```

**Note**: All endpoints are prefixed with `/v1` (e.g., health is at `/v1/health`)

### API Endpoints

All endpoints are prefixed with `/v1`.

#### POST /v1/chat/completions
Send chat completion requests (OpenAI compatible)

#### GET /v1/models
List all available free models with their performance metrics

#### GET /v1/models/{id}
Get details for a specific model

#### GET /v1/models/{id}/metrics
Get performance metrics for a specific model

#### GET /v1/stats
Get routing statistics and system health

#### POST /v1/stats/reset
Reset statistics and circuit breakers

#### GET /v1/health
Health check endpoint (returns "OK")

#### GET /v1/
Service information and available endpoints

## Configuration

Robin LLM can be configured via environment variables or `application.properties`:

### Scraping Configuration
```properties
scraper.enabled=true                    # Enable/disable model discovery
scraper.interval=1h                     # Scraping interval
scraper.openrouter.url=https://openrouter.ai/models
scraper.filter=free                    # Model filter criteria
```

### Metrics Configuration
```properties
metrics.enabled=true                    # Enable/disable metrics collection
metrics.interval=1h                    # Testing interval
metrics.test.prompts=What is 2+2?,Explain photosynthesis
metrics.top-models=3                   # Number of top models to test
```

### Routing Configuration
```properties
router.weight.latency=0.6               # Weight for latency in scoring
router.weight.success=0.3               # Weight for success rate in scoring
router.weight.rate-limit=0.1           # Weight for rate limit proximity
router.circuit-breaker.threshold=0.5   # Failure rate threshold for circuit breaker
router.retry.max=3                     # Maximum retry attempts
router.retry.backoff=1000              # Backoff time in milliseconds
```

### API Configuration
```properties
api.compatibility=openai                # API compatibility mode
api.max-tokens=4096                     # Maximum tokens per request
api.timeout=30000                       # Request timeout in milliseconds
```

### OpenRouter Configuration
```properties
openrouter.api-key=your_api_key_here    # OpenRouter API key (set via env var)
openrouter.base-url=https://openrouter.ai/api/v1
```

## Monitoring and Troubleshooting

### Health Check
```bash
curl http://localhost:8080/v1/health
```

### View Statistics
```bash
curl http://localhost:8080/v1/stats
```

Response includes:
- Total models available
- Active models
- Free models
- Total requests served
- Total failures
- Success rate
- Service uptime

### View Model Metrics
```bash
curl http://localhost:8080/v1/models/{model_id}/metrics
```

Response includes:
- Average latency (ms)
- Success rate
- Error rate
- P95/P99 latency
- Requests per second

### Reset Statistics
```bash
curl -X POST http://localhost:8080/v1/stats/reset
```

### Troubleshooting

**No models available:**
- Verify OpenRouter API key is set correctly
- Check that scraper is enabled in configuration
- Review logs for scraping errors

**High error rates:**
- Check `/v1/stats` for model-specific metrics
- Review circuit breaker status
- Ensure network connectivity to OpenRouter

**Slow responses:**
- Check model latency metrics via `/v1/models/{id}/metrics`
- Consider adjusting router weights for faster models
- Verify network connectivity

See [RobinLLM.md](RobinLLM.md) for the complete development plan and technical details.

## Development

```bash
# Run in development mode with hot reload
mvn quarkus:dev

# Run tests
mvn test

# Build production JAR
mvn clean package

# Build native image (requires GraalVM)
mvn package -Pnative
```

### Testing

The project includes comprehensive unit and integration tests. To run tests:
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=OpenRouterClientTest
```

## License

MIT License - see LICENSE file for details

## Contributing

Contributions welcome! Please read [RobinLLM.md](RobinLLM.md) for the detailed implementation plan and architecture.

## Status

✅ Fully functional and ready for use - See [RobinLLM.md](RobinLLM.md) for detailed technical documentation
