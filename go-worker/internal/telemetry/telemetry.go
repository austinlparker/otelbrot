package telemetry

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"net/http/httptrace"
	"os"
	"time"

	"github.com/austinlparker/otelbrot/go-worker/internal/config"
	"go.opentelemetry.io/contrib/instrumentation/net/http/httptrace/otelhttptrace"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	otelconf "go.opentelemetry.io/contrib/otelconf/v0.3.0"
	"go.opentelemetry.io/otel/metric"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/trace"
)

// Telemetry provides centralized telemetry services
type Telemetry struct {
	Logger         *log.Logger
	tracerProvider trace.TracerProvider
	meterProvider  metric.MeterProvider
	shutdown       func(context.Context) error
}

// Setup initializes the telemetry system using the provided config file
func Setup(cfg *config.Config) (*Telemetry, context.Context, error) {
	logger := log.New(os.Stdout, "[telemetry] ", log.LstdFlags)
	logger.Println("Initializing telemetry system")

	configPath := os.Getenv("OTEL_CONFIG_FILE")
	if configPath == "" {
		return nil, nil, fmt.Errorf("OTEL_CONFIG_FILE environment variable not set")
	}

	// Read config file
	configBytes, err := os.ReadFile(configPath)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to read config file: %w", err)
	}

	// Parse YAML config
	configFile, err := otelconf.ParseYAML(configBytes)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to parse config file: %w", err)
	}

	// Create context with parent from environment
	ctx := extractParentContext(context.Background(), logger)

	// Create SDK with config
	sdk, err := otelconf.NewSDK(
		otelconf.WithContext(ctx),
		otelconf.WithOpenTelemetryConfiguration(*configFile),
	)
	if err != nil {
		return nil, nil, fmt.Errorf("failed to create SDK: %w", err)
	}

	telemetry := &Telemetry{
		Logger:         logger,
		tracerProvider: sdk.TracerProvider(),
		meterProvider:  sdk.MeterProvider(),
		shutdown:       sdk.Shutdown,
	}

	return telemetry, ctx, nil
}

// extractParentContext tries to extract parent context from environment variables
func extractParentContext(ctx context.Context, logger *log.Logger) context.Context {
	traceparent := os.Getenv("TRACEPARENT")
	tracestate := os.Getenv("TRACESTATE")

	if traceparent == "" {
		logger.Println("No parent trace context found in environment variables")
		return ctx
	}

	logger.Printf("Found trace context in environment: traceparent=%s", traceparent)

	carrier := propagation.MapCarrier{
		"traceparent": traceparent,
	}
	if tracestate != "" {
		carrier["tracestate"] = tracestate
	}

	prop := propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{},
		propagation.Baggage{},
	)

	ctx = prop.Extract(ctx, carrier)
	logger.Println("Parent context extracted from environment variables")
	return ctx
}

// NewHTTPClient creates an HTTP client with OpenTelemetry instrumentation
func (t *Telemetry) NewHTTPClient() *http.Client {
	return &http.Client{
		Transport: otelhttp.NewTransport(
			http.DefaultTransport,
			otelhttp.WithClientTrace(func(ctx context.Context) *httptrace.ClientTrace {
				return otelhttptrace.NewClientTrace(ctx)
			}),
		),
		Timeout: 30 * time.Second,
	}
}

// StartSpan is a helper to start a new span with the telemetry's tracer
func (t *Telemetry) StartSpan(ctx context.Context, name string, opts ...trace.SpanStartOption) (context.Context, trace.Span) {
	return t.tracerProvider.Tracer("go-worker").Start(ctx, name, opts...)
}

// ShutdownWithTimeout safely shuts down the telemetry system with a timeout
func (t *Telemetry) ShutdownWithTimeout(timeout time.Duration) error {
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	return t.shutdown(ctx)
}
