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
COPY --from=builder /app/config.json .

# Create config directory
RUN mkdir -p /app/config

# Run the worker with support for optional config
ENTRYPOINT ["/app/worker", "--config=/app/config.json"]