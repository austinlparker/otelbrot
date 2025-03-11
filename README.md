# OTelBrot: Distributed Mandelbrot Renderer with OpenTelemetry

## Notes

Some parts of this repository (the `uml` directory) are preserved to record the inputs to the AI model that was the primary author of this demo repository.

## Introduction

OTelBrot is a distributed Mandelbrot set renderer that uses OpenTelemetry for observability. It allows users to explore the Mandelbrot set by zooming into different regions, with the computation distributed across worker pods in a Kubernetes cluster.

## Architecture

The application consists of three main components:

1. **Frontend**: A React-based UI that provides an interactive interface for exploring the Mandelbrot set.
2. **Orchestrator**: A Spring Boot service that coordinates rendering jobs and manages tiles.
3. **Go Worker**: Multiple Go-based worker pods that render individual tiles of the Mandelbrot set.

Data is passed between components through Redis and WebSockets, with OpenTelemetry used for tracing and observability.

## Development

### Prerequisites

- JDK 21
- Maven
- Node.js and npm
- Docker and Kubernetes (for deployment)
- Redis (for development)

### Building

```bash
# Build the frontend
cd frontend
npm install
npm run build

# Build the orchestrator
cd ../orchestrator
./mvnw clean package

# Build the Go worker
cd ../go-worker
go build ./cmd/worker
```

### Running Locally

```bash
# Start Redis
docker run -d -p 6379:6379 redis:alpine

# Start the orchestrator
cd orchestrator
./mvnw spring-boot:run

# Start a Go worker
cd ../go-worker
./worker

# Start the frontend
cd ../frontend
npm run dev
```

### Kubernetes Deployment

The application can be deployed using Helm charts:

```bash
# Deploy everything using Helm
make helm-deploy
```

This will:
1. Build Docker images for all components
2. Create required namespaces
3. Install OpenTelemetry Operator and Collectors
4. Deploy the LGTM stack (Loki, Grafana, Tempo, Mimir)
5. Deploy application components (Frontend, Orchestrator, Workers)

#### Prerequisites

- Kubernetes cluster with access to push images
- Honeycomb API key (set as `HONEYCOMB_API_KEY` environment variable)
- Helm 3+

#### Cleaning Up

```bash
# Clean up application components only
make helm-cleanup

# Clean up everything including operators and namespaces
make helm-cleanup-all
```

## Features

- Interactive Mandelbrot set exploration
- Distributed tile rendering
- Progressive loading from low to high resolution
- Observability with OpenTelemetry
- Keyboard and mouse controls for navigation