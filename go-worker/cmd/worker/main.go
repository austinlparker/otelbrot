package main

import (
	"context"
	"flag"
	"log"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/austinlparker/otelbrot/go-worker/internal/config"
	"github.com/austinlparker/otelbrot/go-worker/internal/server"
	"github.com/austinlparker/otelbrot/go-worker/internal/telemetry"
)

func main() {
	// Parse command line flags
	configPath := flag.String("config", "", "path to config file")
	flag.Parse()

	// Load configuration
	cfg, err := config.Load(*configPath)
	if err != nil {
		log.Fatalf("Failed to load configuration: %v", err)
	}

	// Initialize logger
	logger := log.New(os.Stdout, "", log.LstdFlags|log.Lshortfile)
	logger.Printf("Starting worker with config: %+v", cfg)

	// Initialize OpenTelemetry
	shutdown, err := telemetry.InitTracer(cfg)
	if err != nil {
		logger.Fatalf("Failed to initialize telemetry: %v", err)
	}
	defer shutdown(context.Background())

	// Create and start the server
	s := server.NewServer(cfg, logger)
	go func() {
		if err := s.Start(); err != nil {
			logger.Fatalf("Failed to start server: %v", err)
		}
	}()

	// Wait for termination signal
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	// Graceful shutdown
	logger.Println("Shutting down...")
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	
	if err := s.Stop(ctx); err != nil {
		logger.Fatalf("Failed to stop server: %v", err)
	}

	logger.Println("Worker shutdown complete")
}