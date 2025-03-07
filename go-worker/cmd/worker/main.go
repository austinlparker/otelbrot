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
	flag.Parse()

	// Initialize logger
	logger := log.New(os.Stdout, "", log.LstdFlags|log.Lshortfile)
	logger.Println("Starting worker...")

	// Load configuration
	cfg, err := config.Load(*configPath)
	if err != nil {
		logger.Fatalf("Failed to load configuration: %v", err)
	}

	// Initialize OpenTelemetry
	shutdown, err := telemetry.InitTracer(cfg)
	if err != nil {
		logger.Fatalf("Failed to initialize telemetry: %v", err)
	}
	defer shutdown(context.Background())

	// Create a context that will be canceled on SIGINT or SIGTERM
	ctx, cancel := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
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