# OTelBrot Go Worker

This is a Go implementation of the worker service for the OTelBrot fractal renderer.

## Features

- HTTP server for receiving tile computation requests
- Configurable worker pool for parallel processing
- OpenTelemetry instrumentation for distributed tracing
- Real-time metrics for monitoring worker performance

## Requirements

- Go 1.22 or higher

## Configuration

The worker can be configured using a JSON configuration file and/or environment variables:

### Environment Variables

- `SERVER_HOST`: Host to bind the HTTP server to (default: "0.0.0.0")
- `SERVER_PORT`: Port for the HTTP server (default: 8081)
- `MAX_WORKERS`: Maximum number of concurrent workers (default: 4)
- `QUEUE_SIZE`: Maximum size of the job queue (default: 100)
- `SERVICE_NAME`: Service name for OpenTelemetry (default: "go-worker")
- `OTEL_EXPORTER_OTLP_ENDPOINT`: URL of the OpenTelemetry collector (default: "http://localhost:4317")
- `TRACE_SAMPLING_RATIO`: Sampling ratio for traces (default: 1.0)
- `ORCHESTRATOR_URL`: URL of the orchestrator service (default: "http://localhost:8080")

### Configuration File

See `config.json` for an example configuration file.

## Building

```bash
# Build the binary
go build -o worker ./cmd/worker

# Build the Docker image
docker build -t otelbrot/go-worker:latest .
```

## Running

```bash
# Run with default configuration
./worker

# Run with a configuration file
./worker -config config.json
```

## API Endpoints

### POST /api/process

Process a tile computation request.

```json
{
  "jobId": "job123",
  "tileId": "tile456",
  "xMin": -2.0,
  "yMin": -1.5,
  "xMax": 1.0,
  "yMax": 1.5,
  "width": 800,
  "height": 600,
  "maxIterations": 1000,
  "colorScheme": "classic",
  "pixelStartX": 0,
  "pixelStartY": 0
}
```

### GET /health

Health check endpoint.

### GET /metrics

Get worker metrics in JSON format.

## Kubernetes Deployment

See `../k8s/go-worker.yaml` for a Kubernetes deployment configuration.