# Robin LLM

An intelligent LLM routing service that automatically discovers free language models and routes requests to the best-performing ones.

## Overview

Robin LLM queries OpenRouter's model API for free LLM options, can optionally benchmark them in the background, and provides an OpenAI-compatible API that intelligently routes requests to the best available model. Think of it as a smart load balancer for free LLMs.

## Features

- **Automatic Discovery**: Pulls free models from OpenRouter's `/api/v1/models` JSON endpoint and adds them to the pool automatically
- **Performance Monitoring (opt-in)**: Optional background benchmarker that probes models with canned prompts and records latency / success-rate / errors. Disabled by default (`metrics.enabled=false`); turn on to populate `/v1/models/{id}/metrics`
- **Intelligent Routing**: Selects the best model for non-streaming requests using a weighted scoring algorithm (latency / success rate / rate-limit proximity)
- **Parallel Streaming Race**: Streaming requests in `auto` mode dispatch up to 3 top models in parallel. The first model to emit a chunk wins; every other in-flight okhttp Call is cancelled and any losing worker sitting in a rate-limit backoff sleep wakes up within ~100ms instead of burning through the full backoff. The race only fails when *all* contestants fail
- **OpenAI Compatible**: Drop-in replacement for OpenAI API with standard `/v1/chat/completions` endpoint
- **Zero Configuration**: Works out of the box with automatic model discovery
- **Built with Java 21 + Quarkus**: Background scraper and metrics tester run on virtual-thread executors; the request hot path uses CompletableFuture on the common ForkJoinPool
- **Lightweight**: Built on Quarkus for minimal resource usage and fast startup

## How It Works

1. **Discovery**: Every `scraper.interval` (default 1h), Robin LLM calls OpenRouter's `/api/v1/models` JSON endpoint, filters for free models, and persists them to SQLite
2. **Optional benchmarking**: If `metrics.enabled=true`, a background tester probes the top-N models with canned prompts to populate latency / success-rate metrics. Off by default
3. **Scoring**: Models are scored based on response time (60%), success rate (30%), and rate limit proximity (10%)
4. **Non-streaming routing**: Incoming non-streaming requests pick the single best-scoring model. On failure they retry with exponential backoff and fall back to the next best model
5. **Streaming race (`auto` mode)**: Streaming requests are dispatched to the top 3 candidate models in parallel. The first model whose first chunk arrives wins; every other in-flight okhttp `Call` is cancelled the instant the winner is declared, so losing providers stop generating tokens immediately. Losers that were waiting out a rate-limit backoff wake up within ~100ms instead of burning through the full sleep. Workers whose connection hadn't yet opened self-cancel via the same coordinator. The race only fails when *all* three contestants fail or the 10s first-chunk timeout fires
6. **Streaming with explicit model**: When the request specifies a single model (not `auto`), Robin LLM skips the race and streams from that model directly
7. **Circuit breaker / failover**: Models that exceed the failure threshold are temporarily removed from selection and re-tested after a cooldown

## Technology Stack

- **Java 21**: Latest LTS; virtual threads used for the background scraper and metrics tester executors
- **Quarkus 3.6**: Fast, lightweight framework for low-latency API
- **SQLite (via xerial jdbc)**: Embedded database for model and metrics persistence
- **Maven**: Build and dependency management
- **OkHttp** (transitive via Retrofit 2.9): HTTP client used for streaming and non-streaming OpenRouter calls
- **RESTEasy Reactive + Jackson**: REST framework and JSON (de)serialization

## Advanced Features

- **Circuit Breaker**: Automatically stops routing to failing models and retries after cooldown
- **Automatic Failover (non-streaming)**: Non-streaming requests that fail retry with exponential backoff and roll over to the next best model
- **Round-Robin Load Balancing**: Distributes requests across top-performing models after scoring narrows the pool
- **Streaming Race with Aggressive Cancellation**: For `auto` streaming requests, RobinLLM races up to 3 models concurrently and aborts the losing okhttp `Call`s the moment the winner's first chunk arrives - even if a loser is still mid-handshake, mid-headers, or sitting in a rate-limit backoff. The race only fails when *all* contestants fail, not when the first one does, so a single fast failure doesn't take down the request
- **Sensible default for `max_tokens`**: When the caller omits `max_tokens`, Robin LLM forwards the configured `api.max-tokens` (default 4096) capped at the model's advertised max - it does *not* blast the model's optimistic advertised completion size, which often exceeds what the upstream provider actually accepts and produces spurious 400s. Caller-supplied `max_tokens` is honored but still capped at the model's max
- **Performance Metrics**: Tracks latency, success rate, P95/P99 latency and requests per second (when `metrics.enabled=true`)
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
List all discovered free models (id, owner, created timestamp - OpenAI-compatible shape). For latency / success-rate data, call `/v1/models/{id}/metrics`

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

### Model Discovery Configuration
```properties
scraper.enabled=true                    # Enable/disable model discovery
scraper.interval=1h                     # How often to refresh the model list
scraper.openrouter.url=https://openrouter.ai/models   # Informational; the JSON list is fetched from openrouter.base-url + /models
scraper.filter=free                     # Model filter criteria
```

### Metrics Configuration
```properties
metrics.enabled=false                   # Background benchmarking. Disabled by default in shipped config
metrics.interval=1h                     # Testing interval (when enabled)
metrics.test.prompts=What is 2+2?,Explain photosynthesis
metrics.top-models=3                    # Number of top models to test
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
api.max-tokens=4096                     # Default max_tokens used when the caller omits it (capped at the model's advertised max)
api.timeout=30000                       # Connect/write timeout for OpenRouter HTTP calls (ms). Read timeout is disabled so streaming can run indefinitely
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
