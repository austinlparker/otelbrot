package telemetry

import (
	"context"
	"fmt"
	"log"
	"os"
	"time"

	"github.com/austinlparker/otelbrot/go-worker/internal/config"
	"go.opentelemetry.io/contrib/instrumentation/runtime"
	otelconf "go.opentelemetry.io/contrib/otelconf/v0.3.0"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlpmetric/otlpmetrichttp"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/metric"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.24.0"
)

// logger is the package logger
var logger = log.New(os.Stdout, "[telemetry] ", log.LstdFlags|log.Lshortfile)

// InitTracer initializes an OTLP exporter, and configures the corresponding trace provider
// It also extracts trace context from environment variables if available
// Returns a shutdown function, the propagated context, and any error
func InitTracer(cfg *config.Config) (func(context.Context) error, context.Context, error) {
	// Get parent trace context from environment variables
	traceParent := os.Getenv("TRACEPARENT")
	traceState := os.Getenv("TRACESTATE")

	logger.Printf("Starting telemetry initialization with trace context: traceparent=%s", traceParent)

	// Create initial context
	ctx := context.Background()

	// Extract parent context if available
	if traceParent != "" {
		logger.Printf("Found trace context: traceparent=%s", traceParent)

		// Create a carrier with the trace context
		carrier := propagation.MapCarrier{
			"traceparent": traceParent,
		}

		// Add tracestate if it exists
		if traceState != "" {
			carrier["tracestate"] = traceState
			logger.Printf("Found trace state: tracestate=%s", traceState)
		}

		// Create propagator
		prop := propagation.NewCompositeTextMapPropagator(
			propagation.TraceContext{},
			propagation.Baggage{},
		)

		// Extract the context
		ctx = prop.Extract(ctx, carrier)
		logger.Printf("Parent context extracted from environment variables")
	} else {
		logger.Printf("No parent context found in environment variables")
	}

	// Check for otel configuration file
	var shutdown func(context.Context) error
	var err error

	if configPath := os.Getenv("OTEL_CONFIG_FILE"); configPath != "" {
		logger.Printf("Using OpenTelemetry configuration from file: %s", configPath)
		shutdown, err = initFromConfigFile(ctx, configPath, cfg)
	} else {
		// Fall back to programmatic configuration
		logger.Printf("Using programmatic OpenTelemetry configuration")
		shutdown, err = initProgrammatically(ctx, cfg)
	}

	if err != nil {
		return nil, ctx, err
	}

	return shutdown, ctx, nil
}

// initFromConfigFile initializes OpenTelemetry using a configuration file
func initFromConfigFile(ctx context.Context, configPath string, cfg *config.Config) (func(context.Context) error, error) {
	// Read the config file
	configBytes, err := os.ReadFile(configPath)
	if err != nil {
		return nil, fmt.Errorf("failed to read config file: %w", err)
	}

	// Parse the YAML configuration
	otelConfig, err := otelconf.ParseYAML(configBytes)
	if err != nil {
		return nil, fmt.Errorf("failed to parse config file: %w", err)
	}

	// Create new SDK from config
	sdk, err := otelconf.NewSDK(
		otelconf.WithContext(ctx),
		otelconf.WithOpenTelemetryConfiguration(*otelConfig),
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create SDK from config: %w", err)
	}

	// Set global providers
	otel.SetTracerProvider(sdk.TracerProvider())
	otel.SetMeterProvider(sdk.MeterProvider())

	// Set up runtime metrics collection
	err = runtime.Start(runtime.WithMeterProvider(sdk.MeterProvider()))
	if err != nil {
		return nil, fmt.Errorf("failed to start runtime metrics collection: %w", err)
	}

	// Create shutdown function
	shutdown := func(ctx context.Context) error {
		return sdk.Shutdown(ctx)
	}

	return shutdown, nil
}

// initProgrammatically initializes OpenTelemetry programmatically
func initProgrammatically(ctx context.Context, cfg *config.Config) (func(context.Context) error, error) {
	// Create resource with service information
	res, err := resource.New(ctx,
		resource.WithAttributes(
			semconv.ServiceName(cfg.Telemetry.ServiceName),
		),
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create resource: %w", err)
	}

	// Create OTLP trace exporter using HTTP
	traceExporter, err := otlptrace.New(ctx,
		otlptracehttp.NewClient(
			otlptracehttp.WithEndpoint(cfg.Telemetry.CollectorURL),
			otlptracehttp.WithInsecure(),
			otlptracehttp.WithTimeout(5*time.Second),
		),
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create trace exporter: %w", err)
	}

	// Create OTLP metric exporter using HTTP
	metricExporter, err := otlpmetrichttp.New(ctx,
		otlpmetrichttp.WithEndpoint(cfg.Telemetry.CollectorURL),
		otlpmetrichttp.WithInsecure(),
		otlpmetrichttp.WithTimeout(5*time.Second),
	)
	if err != nil {
		return nil, fmt.Errorf("failed to create metric exporter: %w", err)
	}

	// Create trace provider
	tracerProvider := sdktrace.NewTracerProvider(
		sdktrace.WithSampler(sdktrace.TraceIDRatioBased(cfg.Telemetry.TraceSamplingRatio)),
		sdktrace.WithBatcher(traceExporter),
		sdktrace.WithResource(res),
	)

	// Create metric provider with periodic reader
	meterProvider := metric.NewMeterProvider(
		metric.WithResource(res),
		metric.WithReader(metric.NewPeriodicReader(metricExporter,
			// Collect metrics every 10 seconds
			metric.WithInterval(10*time.Second),
		)),
	)

	// Set up runtime metrics collection
	err = runtime.Start(runtime.WithMeterProvider(meterProvider))
	if err != nil {
		return nil, fmt.Errorf("failed to start runtime metrics collection: %w", err)
	}

	// Set global providers
	otel.SetTracerProvider(tracerProvider)
	otel.SetMeterProvider(meterProvider)
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(
		propagation.TraceContext{},
		propagation.Baggage{},
	))

	// Create a shutdown function that can be called to clean up
	shutdown := func(ctx context.Context) error {
		// First shutdown the trace provider
		if err := tracerProvider.Shutdown(ctx); err != nil {
			return fmt.Errorf("failed to shutdown trace provider: %w", err)
		}
		// Then shutdown the meter provider
		if err := meterProvider.Shutdown(ctx); err != nil {
			return fmt.Errorf("failed to shutdown meter provider: %w", err)
		}
		return nil
	}

	return shutdown, nil
}
