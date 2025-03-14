package main

import (
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
	// Parse command line flags - keep minimal flags for debugging
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

	// Load configuration from environment variables
	cfg, err := config.Load()
	if err != nil {
		logger.Fatalf("Failed to load configuration: %v", err)
	}

	// Initialize telemetry system
	tel, ctx, err := telemetry.Setup(cfg)
	if err != nil {
		logger.Fatalf("Failed to initialize telemetry: %v", err)
	}

	// Create a context that will be canceled on SIGINT or SIGTERM
	ctx, cancel := signal.NotifyContext(ctx, syscall.SIGINT, syscall.SIGTERM)
	defer cancel()

	// Create a span for the entire worker process
	ctx, span := tel.StartSpan(ctx, "worker_process")
	defer span.End()

	// Create calculator and result sender
	calc := calculator.NewFractalCalculator(logger)
	resultSender := sender.NewResultSender(cfg.Orchestrator.URL, logger, tel)

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
		span.RecordError(err)
		logger.Fatalf("Failed to calculate tile: %v", err)
	}

	logger.Printf("Tile calculation completed in %d ms", time.Since(startTime).Milliseconds())

	// Send the result
	if err := resultSender.SendResult(ctx, result); err != nil {
		span.RecordError(err)
		logger.Fatalf("Failed to send result: %v", err)
	}

	logger.Println("Result sent successfully, finishing worker process")

	// End main span
	span.End()

	// Cancel the main context to signal that we're done with the main work
	cancel()

	// Shut down telemetry with timeout
	logger.Println("Shutting down telemetry, waiting for spans to flush...")
	shutdownErr := tel.ShutdownWithTimeout(5 * time.Second)
	if shutdownErr != nil {
		logger.Printf("Warning: Error during telemetry shutdown: %v", shutdownErr)
	}

	logger.Println("Worker completed successfully")
}
