package server

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"sync"
	"time"

	"github.com/austinlparker/otelbrot/go-worker/internal/calculator"
	"github.com/austinlparker/otelbrot/go-worker/internal/config"
	"github.com/austinlparker/otelbrot/go-worker/internal/models"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/trace"
)

// ResultSender handles sending tile results back to the orchestrator
type ResultSender interface {
	SendResult(ctx context.Context, result *models.TileResult) error
}

// Server represents the HTTP server for the worker
type Server struct {
	cfg        *config.Config
	logger     *log.Logger
	server     *http.Server
	calculator *calculator.FractalCalculator
	sender     ResultSender
	tracer     trace.Tracer
	propagator propagation.TextMapPropagator
	
	// Worker pool
	workerPool chan struct{}
	// Job queue
	jobQueue   chan *models.TileSpec
	// Metrics
	metrics    *workerMetrics
}

// workerMetrics holds metrics about the worker
type workerMetrics struct {
	totalProcessed int64
	successful     int64
	failed         int64
	mu             sync.Mutex
}

// NewServer creates a new HTTP server
func NewServer(cfg *config.Config, logger *log.Logger) *Server {
	calc := calculator.NewFractalCalculator(logger)
	sender := &orchestratorSender{
		orchestratorURL: cfg.Orchestrator.URL,
		logger:          logger,
		client:          &http.Client{Timeout: 30 * time.Second},
		tracer:          otel.Tracer("result-sender"),
		propagator:      otel.GetTextMapPropagator(),
	}

	s := &Server{
		cfg:        cfg,
		logger:     logger,
		calculator: calc,
		sender:     sender,
		tracer:     otel.Tracer("worker-server"),
		propagator: otel.GetTextMapPropagator(),
		workerPool: make(chan struct{}, cfg.Fractal.MaxWorkers),
		jobQueue:   make(chan *models.TileSpec, cfg.Fractal.QueueSize),
		metrics:    &workerMetrics{},
	}

	// Create HTTP server
	mux := http.NewServeMux()
	mux.HandleFunc("/api/process", s.handleProcessTile)
	mux.HandleFunc("/health", s.handleHealth)
	mux.HandleFunc("/metrics", s.handleMetrics)

	s.server = &http.Server{
		Addr:         fmt.Sprintf("%s:%d", cfg.Server.Host, cfg.Server.Port),
		Handler:      mux,
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  120 * time.Second,
	}

	return s
}

// Start starts the server and worker pool
func (s *Server) Start() error {
	// Start worker pool
	go s.startWorkerPool()
	
	// Start HTTP server
	s.logger.Printf("Starting server on %s", s.server.Addr)
	return s.server.ListenAndServe()
}

// Stop stops the server gracefully
func (s *Server) Stop(ctx context.Context) error {
	return s.server.Shutdown(ctx)
}

// startWorkerPool starts the worker pool that processes jobs
func (s *Server) startWorkerPool() {
	s.logger.Printf("Starting worker pool with %d workers", s.cfg.Fractal.MaxWorkers)
	
	for i := 0; i < s.cfg.Fractal.MaxWorkers; i++ {
		go s.workerLoop(i)
	}
}

// workerLoop is the main loop for a worker
func (s *Server) workerLoop(workerID int) {
	s.logger.Printf("Worker %d started", workerID)
	
	for job := range s.jobQueue {
		s.workerPool <- struct{}{} // Acquire a worker slot
		
		s.logger.Printf("Worker %d processing job %s, tile %s", workerID, job.JobID, job.TileID)
		ctx := context.Background()
		
		// Create a span for this job
		ctx, span := s.tracer.Start(ctx, "WorkerProcess",
			trace.WithAttributes(
				attribute.String("worker.id", fmt.Sprintf("%d", workerID)),
				attribute.String("job.id", job.JobID),
				attribute.String("tile.id", job.TileID),
			))
		
		// Process the job
		result, err := s.calculator.CalculateTile(ctx, job)
		if err != nil {
			span.SetStatus(codes.Error, "Failed to calculate tile")
			span.RecordError(err)
			s.logger.Printf("Worker %d failed to process job %s: %v", workerID, job.JobID, err)
			
			// Update metrics
			s.metrics.mu.Lock()
			s.metrics.totalProcessed++
			s.metrics.failed++
			s.metrics.mu.Unlock()
		} else {
			// Send the result back to the orchestrator
			if err := s.sender.SendResult(ctx, result); err != nil {
				span.SetStatus(codes.Error, "Failed to send result")
				span.RecordError(err)
				s.logger.Printf("Worker %d failed to send result for job %s: %v", workerID, job.JobID, err)
				
				// Update metrics
				s.metrics.mu.Lock()
				s.metrics.totalProcessed++
				s.metrics.failed++
				s.metrics.mu.Unlock()
			} else {
				span.SetStatus(codes.Ok, "Successfully processed tile")
				s.logger.Printf("Worker %d successfully processed job %s", workerID, job.JobID)
				
				// Update metrics
				s.metrics.mu.Lock()
				s.metrics.totalProcessed++
				s.metrics.successful++
				s.metrics.mu.Unlock()
			}
		}
		
		span.End()
		<-s.workerPool // Release the worker slot
	}
}

// handleProcessTile handles requests to process a tile
func (s *Server) handleProcessTile(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}
	
	// Extract the trace context
	ctx := s.propagator.Extract(r.Context(), propagation.HeaderCarrier(r.Header))
	
	// Start a new span
	ctx, span := s.tracer.Start(ctx, "HandleProcessTile")
	defer span.End()
	
	// Decode the request
	var tileSpec models.TileSpec
	if err := json.NewDecoder(r.Body).Decode(&tileSpec); err != nil {
		span.SetStatus(codes.Error, "Failed to decode request")
		span.RecordError(err)
		http.Error(w, fmt.Sprintf("Failed to decode request: %v", err), http.StatusBadRequest)
		return
	}
	
	span.SetAttributes(
		attribute.String("job.id", tileSpec.JobID),
		attribute.String("tile.id", tileSpec.TileID),
	)
	
	// Check if we have capacity in the queue
	if len(s.jobQueue) >= s.cfg.Fractal.QueueSize {
		span.SetStatus(codes.Error, "Job queue is full")
		http.Error(w, "Job queue is full", http.StatusServiceUnavailable)
		return
	}
	
	// Add the job to the queue
	s.jobQueue <- &tileSpec
	
	span.SetStatus(codes.Ok, "Job accepted")
	w.WriteHeader(http.StatusAccepted)
	fmt.Fprintf(w, "Job accepted: %s, tile: %s", tileSpec.JobID, tileSpec.TileID)
}

// handleHealth handles health check requests
func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	fmt.Fprintf(w, "OK")
}

// handleMetrics handles metrics requests
func (s *Server) handleMetrics(w http.ResponseWriter, r *http.Request) {
	s.metrics.mu.Lock()
	defer s.metrics.mu.Unlock()
	
	metrics := map[string]interface{}{
		"totalProcessed": s.metrics.totalProcessed,
		"successful":     s.metrics.successful,
		"failed":         s.metrics.failed,
		"queueSize":      len(s.jobQueue),
		"maxQueueSize":   s.cfg.Fractal.QueueSize,
		"workers":        s.cfg.Fractal.MaxWorkers,
	}
	
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(metrics)
}

// orchestratorSender sends results back to the orchestrator
type orchestratorSender struct {
	orchestratorURL string
	logger          *log.Logger
	client          *http.Client
	tracer          trace.Tracer
	propagator      propagation.TextMapPropagator
}

// SendResult sends a tile result back to the orchestrator
func (s *orchestratorSender) SendResult(ctx context.Context, result *models.TileResult) error {
	ctx, span := s.tracer.Start(ctx, "SendResult",
		trace.WithAttributes(
			attribute.String("job.id", result.JobID),
			attribute.String("tile.id", result.TileID),
		))
	defer span.End()
	
	endpoint := fmt.Sprintf("%s/api/fractal/tile-result", s.orchestratorURL)
	s.logger.Printf("Sending tile result to %s: job %s, tile %s", 
		endpoint, result.JobID, result.TileID)
	
	// Create the request
	body, err := json.Marshal(result)
	if err != nil {
		span.SetStatus(codes.Error, "Failed to marshal result")
		span.RecordError(err)
		return fmt.Errorf("failed to marshal result: %w", err)
	}
	
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, 
		bytes.NewBuffer(body))
	if err != nil {
		span.SetStatus(codes.Error, "Failed to create request")
		span.RecordError(err)
		return fmt.Errorf("failed to create request: %w", err)
	}
	
	// Add trace context to the request headers
	s.propagator.Inject(ctx, propagation.HeaderCarrier(req.Header))
	
	// Set content type
	req.Header.Set("Content-Type", "application/json")
	
	// Send the request
	resp, err := s.client.Do(req)
	if err != nil {
		span.SetStatus(codes.Error, "Failed to send request")
		span.RecordError(err)
		return fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()
	
	// Check the response status
	if resp.StatusCode != http.StatusAccepted {
		err := fmt.Errorf("unexpected status code: %d", resp.StatusCode)
		span.SetStatus(codes.Error, "Unexpected status code")
		span.RecordError(err)
		return err
	}
	
	span.SetStatus(codes.Ok, "Successfully sent result")
	s.logger.Printf("Successfully sent tile result for job %s, tile %s", 
		result.JobID, result.TileID)
	return nil
}