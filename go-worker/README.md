# OTelBrot Go Worker

This is a Go implementation of the worker service for the OTelBrot fractal renderer.

## Features

- Single-shot worker process that calculates one fractal tile and exits
- Efficient Mandelbrot set calculation with optimizations
- OpenTelemetry instrumentation for distributed tracing
- Small memory and CPU footprint
- Docker image optimized for Kubernetes jobs

## Requirements

- Go 1.22 or higher

## Configuration

The worker can be configured using a JSON configuration file and/or environment variables:

### Environment Variables

- `TILE_SPEC_JOB_ID`: Job ID for the tile calculation
- `TILE_SPEC_TILE_ID`: Tile ID for the tile calculation
- `TILE_SPEC_X_MIN`: Minimum X coordinate for the tile
- `TILE_SPEC_Y_MIN`: Minimum Y coordinate for the tile
- `TILE_SPEC_X_MAX`: Maximum X coordinate for the tile
- `TILE_SPEC_Y_MAX`: Maximum Y coordinate for the tile
- `TILE_SPEC_WIDTH`: Width of the tile in pixels
- `TILE_SPEC_HEIGHT`: Height of the tile in pixels
- `TILE_SPEC_MAX_ITERATIONS`: Maximum iterations for the Mandelbrot set calculation
- `TILE_SPEC_COLOR_SCHEME`: Color scheme for the tile (classic, fire, ocean, grayscale, rainbow)
- `TILE_SPEC_PIXEL_START_X`: Starting X pixel position
- `TILE_SPEC_PIXEL_START_Y`: Starting Y pixel position
- `TRACEPARENT`: OpenTelemetry trace context
- `OTEL_EXPORTER_OTLP_ENDPOINT`: URL of the OpenTelemetry collector
- `ORCHESTRATOR_URL`: URL of the orchestrator service

## Building

```bash
# Build the binary
go build -o worker ./cmd/worker

# Build the Docker image
docker build -t otelbrot/go-worker:latest .
```

## Running

```bash
# Run with environment variables
TILE_SPEC_JOB_ID=test-job TILE_SPEC_TILE_ID=test-tile TILE_SPEC_X_MIN=-2.0 TILE_SPEC_Y_MIN=-1.5 TILE_SPEC_X_MAX=1.0 TILE_SPEC_Y_MAX=1.5 TILE_SPEC_WIDTH=800 TILE_SPEC_HEIGHT=600 TILE_SPEC_MAX_ITERATIONS=100 TILE_SPEC_COLOR_SCHEME=classic TILE_SPEC_PIXEL_START_X=0 TILE_SPEC_PIXEL_START_Y=0 ORCHESTRATOR_URL=http://localhost:8080 ./worker
```

## Kubernetes Deployment

The worker is designed to be run as a Kubernetes job. See `../k8s/go-worker.yaml` for a Kubernetes job configuration.

This design allows the Go worker to fit into the fan-out/fan-in architecture used by the orchestrator, where many worker instances are created to process tiles in parallel.