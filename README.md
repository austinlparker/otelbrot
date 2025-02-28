# OTelBrot: Distributed Mandelbrot Renderer with OpenTelemetry

## Notes

Some parts of this repository (the `uml` directory) are preserved to record the inputs to the AI model that was the primary author of this demo repository.

## Introduction

OTelBrot is a distributed Mandelbrot set renderer that uses OpenTelemetry for observability. It allows users to explore the Mandelbrot set by zooming into different regions, with the computation distributed across worker pods in a Kubernetes cluster.

## Architecture

The application consists of three main components:

1. **Frontend**: A React-based UI that provides an interactive interface for exploring the Mandelbrot set.
2. **Orchestrator**: A Spring Boot service that coordinates rendering jobs and manages tiles.
3. **Worker**: Multiple worker pods that render individual tiles of the Mandelbrot set.

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

# Build the worker
cd ../worker
./mvnw clean package
```

### Running Locally

```bash
# Start Redis
docker run -d -p 6379:6379 redis:alpine

# Start the orchestrator
cd orchestrator
./mvnw spring-boot:run

# Start a worker
cd ../worker
./mvnw spring-boot:run

# Start the frontend
cd ../frontend
npm run dev
```

### Kubernetes Deployment

```bash
# Apply Kubernetes manifests
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/rbac.yaml
kubectl apply -f k8s/redis.yaml
kubectl apply -f k8s/orchestrator.yaml
kubectl apply -f k8s/worker.yaml
kubectl apply -f k8s/frontend.yaml
```

## Features

- Interactive Mandelbrot set exploration
- Distributed tile rendering
- Progressive loading from low to high resolution
- Observability with OpenTelemetry
- Keyboard and mouse controls for navigation