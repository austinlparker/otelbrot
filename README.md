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
4. Deploy the OTEL-LGTM all-in-one container (Loki, Grafana, Tempo, Prometheus/Mimir)
5. Deploy application components (Frontend, Orchestrator, Workers)

#### Prerequisites

- Kubernetes cluster with access to push images
- Helm 3+

### Local Deployment with kind

For local development and testing, you can use [kind](https://kind.sigs.k8s.io/) (Kubernetes in Docker):

```bash
# Set up a new kind cluster with ingress support
make kind-setup

# Deploy the application to kind
make kind-deploy
```

The configuration is optimized for high-performance ARM64 machines with 128 cores and hundreds of GB of memory:

- Standard 3-node kind cluster (1 control plane + 2 workers)
- Support for hundreds of concurrent worker pods through the orchestrator
- Enhanced Redis configuration for high concurrency
- Increased thread pools and queue capacity in the orchestrator
- Exposed ports for easy access to the frontend and monitoring

After deployment, the application will be available at:
- Frontend: http://otelbrot.local
- Metrics/Grafana: http://metrics.otelbrot.local

You may need to add these hostnames to your /etc/hosts file:
```
127.0.0.1 otelbrot.local metrics.otelbrot.local
```

#### Cleaning Up

```bash
# Clean up application components only
make helm-cleanup

# Clean up everything including operators and namespaces
make helm-cleanup-all

# Delete the kind cluster
kind delete cluster --name otelbrot
```

## Features

- Interactive Mandelbrot set exploration
- Distributed tile rendering
- Progressive loading from low to high resolution
- Observability with OpenTelemetry
- Keyboard and mouse controls for navigation