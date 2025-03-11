package sender

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"time"

	"github.com/austinlparker/otelbrot/go-worker/internal/models"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/trace"
)

// ResultSender sends tile results back to the orchestrator
type ResultSender struct {
	orchestratorURL string
	logger          *log.Logger
	client          *http.Client
	tracer          trace.Tracer
	propagator      propagation.TextMapPropagator
}

// NewResultSender creates a new result sender
func NewResultSender(orchestratorURL string, logger *log.Logger) *ResultSender {
	return &ResultSender{
		orchestratorURL: orchestratorURL,
		logger:          logger,
		client:          &http.Client{Timeout: 30 * time.Second},
		tracer:          otel.Tracer("result-sender"),
		propagator:      otel.GetTextMapPropagator(),
	}
}

// SendResult sends a tile result back to the orchestrator
func (s *ResultSender) SendResult(ctx context.Context, result *models.TileResult) error {
	// Log trace context information
	spanContext := trace.SpanContextFromContext(ctx)
	if spanContext.IsValid() {
		s.logger.Printf("Sending result with trace context: traceID=%s, spanID=%s, sampled=%t", 
			spanContext.TraceID().String(), spanContext.SpanID().String(), spanContext.IsSampled())
	} else {
		s.logger.Printf("No valid trace context found when sending result")
	}

	ctx, span := s.tracer.Start(ctx, "ResultSender.SendResult",
		trace.WithAttributes(
			attribute.String("job.id", result.JobID),
			attribute.String("tile.id", result.TileID),
			attribute.Int("result.width", result.Width),
			attribute.Int("result.height", result.Height),
			attribute.Int64("result.calculationTimeMs", result.CalculationTimeMs),
			attribute.String("result.status", string(result.Status)),
			attribute.Int("result.imageDataSize", len(result.ImageData)),
		))
	defer span.End()
	
	endpoint := fmt.Sprintf("%s/api/fractal/tile-result", s.orchestratorURL)
	s.logger.Printf("Sending tile result to %s: job %s, tile %s, status %s, data size %d bytes", 
		endpoint, result.JobID, result.TileID, result.Status, len(result.ImageData))
	
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