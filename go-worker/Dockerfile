# Build stage
FROM golang:1.24-alpine AS builder

# Set working directory
WORKDIR /app

# Copy go module files
COPY go.mod go.sum ./

# Download dependencies
RUN go mod download

# Copy source code
COPY . .

# Build the application
RUN CGO_ENABLED=0 GOOS=linux go build -o worker ./cmd/worker

# Runtime stage
FROM alpine:3.19

# Install CA certificates for secure communications
RUN apk --no-cache add ca-certificates

WORKDIR /app

# Copy the binary from builder stage
COPY --from=builder /app/worker .

# Set default environment variables
ENV SERVICE_NAME="go-worker" \
    MAX_WORKERS="4" \
    QUEUE_SIZE="100" \
    OTEL_EXPORTER_OTLP_ENDPOINT="localhost:4317" \
    TRACE_SAMPLING_RATIO="1.0" \
    ORCHESTRATOR_URL="http://localhost:8080"

# Expose relevant ports
EXPOSE 8081

# Run the worker
ENTRYPOINT ["/app/worker"]