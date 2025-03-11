# Deprecated Java Worker

**This Java worker implementation has been deprecated in favor of the Go worker implementation.**

## Migration to Go Worker

The Java-based worker has been replaced by the Go implementation located in the `go-worker/` directory. 
The Go worker provides better performance and resource utilization for fractal rendering.

## Reason for Removal

1. **Performance**: The Go implementation offers better performance for CPU-intensive tasks
2. **Resource Efficiency**: Lower memory footprint and faster startup times
3. **Simplified Deployment**: No JVM requirements, smaller container images
4. **Developer Experience**: Simplified codebase with fewer dependencies

## Using the Go Worker

Please see the Go worker documentation in the `go-worker/` directory for instructions on how to build and run the Go worker.

```bash
# Build the Go worker
cd ../go-worker
go build ./cmd/worker

# Run the Go worker
./worker
```