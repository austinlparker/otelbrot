package main

import (
	"context"
	"flag"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/austinlparker/otelbrot/go-worker/internal/calculator"
	"github.com/austinlparker/otelbrot/go-worker/internal/config"
	"github.com/austinlparker/otelbrot/go-worker/internal/models"
	"github.com/austinlparker/otelbrot/go-worker/internal/sender"
	"github.com/austinlparker/otelbrot/go-worker/internal/telemetry"
)

func main() {
	// Parse command line flags
	configPath := flag.String("config", "", "path to config file")
	otelConfigPath := flag.String("otel-config", "", "path to OpenTelemetry config file")
	verbose := flag.Bool("verbose", false, "enable verbose logging")
	flag.Parse()

	// Initialize logger
	logPrefix := "[worker] "
	logger := log.New(os.Stdout, logPrefix, log.LstdFlags|log.Lshortfile)
	logger.Println("Starting worker...")

	// Log all environment variables in verbose mode
	if *verbose {
		logger.Println("Environment variables:")
		for _, env := range os.Environ() {
			logger.Println("  " + env)
		}
	}

	// Check for trace context
	traceParent := os.Getenv("TRACEPARENT")
	if traceParent != "" {
		logger.Printf("Found TRACEPARENT: %s", traceParent)
	} else {
		logger.Printf("No TRACEPARENT environment variable found")
	}

	// Set OpenTelemetry config file path if provided via flag
	if *otelConfigPath != "" {
		os.Setenv("OTEL_CONFIG_FILE", *otelConfigPath)
		logger.Printf("Using OpenTelemetry config from: %s", *otelConfigPath)
	}

	// Load configuration
	cfg, err := config.Load(*configPath)
	if err != nil {
		logger.Fatalf("Failed to load configuration: %v", err)
	}
	
	// Print configuration
	logger.Printf("Configuration loaded: serviceName=%s, collectorURL=%s", 
		cfg.Telemetry.ServiceName, cfg.Telemetry.CollectorURL)

	// Initialize OpenTelemetry and get propagated context
	shutdown, propagatedCtx, err := telemetry.InitTracer(cfg)
	if err != nil {
		logger.Fatalf("Failed to initialize telemetry: %v", err)
	}
	defer shutdown(context.Background())

	// Create a context that will be canceled on SIGINT or SIGTERM
	// Use the propagated context as the parent context
	ctx, cancel := signal.NotifyContext(propagatedCtx, syscall.SIGINT, syscall.SIGTERM)
	defer cancel()

	// Create calculator and result sender
	calc := calculator.NewFractalCalculator(logger)
	resultSender := sender.NewResultSender(cfg.Orchestrator.URL, logger)

	// Try to get tile spec from environment variables
	tileSpec, err := models.NewTileSpecFromEnvironment()
	if err != nil {
		logger.Fatalf("Failed to get tile spec from environment: %v", err)
	}

	logger.Printf("Processing tile: job=%s, tile=%s", tileSpec.JobID, tileSpec.TileID)

	// Process the tile
	startTime := time.Now()
	result, err := calc.CalculateTile(ctx, tileSpec)
	if err != nil {
		logger.Fatalf("Failed to calculate tile: %v", err)
	}

	logger.Printf("Tile calculation completed in %d ms", time.Since(startTime).Milliseconds())

	// Send the result
	if err := resultSender.SendResult(ctx, result); err != nil {
		logger.Fatalf("Failed to send result: %v", err)
	}

	logger.Println("Result sent successfully, exiting")
}