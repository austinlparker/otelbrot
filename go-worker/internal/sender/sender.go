package sender

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"log"
	"net/http"

	"github.com/austinlparker/otelbrot/go-worker/internal/models"
	"github.com/austinlparker/otelbrot/go-worker/internal/telemetry"
	"go.opentelemetry.io/otel/trace"
)

// ResultSender sends tile results back to the orchestrator
type ResultSender struct {
	orchestratorURL string
	logger          *log.Logger
	client          *http.Client
	telemetry       *telemetry.Telemetry
}

// NewResultSender creates a new result sender with telemetry instrumentation
func NewResultSender(orchestratorURL string, logger *log.Logger, tel *telemetry.Telemetry) *ResultSender {
	return &ResultSender{
		orchestratorURL: orchestratorURL,
		logger:          logger,
		client:          tel.NewHTTPClient(), // Get instrumented HTTP client from telemetry
		telemetry:       tel,
	}
}

// SendResult sends a tile result back to the orchestrator
func (s *ResultSender) SendResult(ctx context.Context, result *models.TileResult) error {
	// Create a span for the overall send operation
	ctx, span := s.telemetry.StartSpan(ctx, "send_tile_result")
	defer span.End()

	// Log trace info for debugging
	spanContext := trace.SpanContextFromContext(ctx)
	if spanContext.IsValid() {
		s.logger.Printf("Sending result with trace context: traceID=%s, spanID=%s",
			spanContext.TraceID().String(), spanContext.SpanID().String())
	} else {
		s.logger.Printf("No valid trace context found when sending result")
	}

	endpoint := fmt.Sprintf("%s/api/fractal/tile-result", s.orchestratorURL)
	s.logger.Printf("Sending tile result to %s: job %s, tile %s", 
		endpoint, result.JobID, result.TileID)

	// Create the request
	body, err := json.Marshal(result)
	if err != nil {
		span.RecordError(err)
		return fmt.Errorf("failed to marshal result: %w", err)
	}

	// Create request with our traced context
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, bytes.NewBuffer(body))
	if err != nil {
		span.RecordError(err)
		return fmt.Errorf("failed to create request: %w", err)
	}

	// Set content type
	req.Header.Set("Content-Type", "application/json")

	// Send the request - the instrumented client will handle context propagation
	resp, err := s.client.Do(req)
	if err != nil {
		span.RecordError(err)
		return fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()

	// Check the response status
	if resp.StatusCode != http.StatusAccepted {
		err := fmt.Errorf("unexpected status code: %d", resp.StatusCode)
		span.RecordError(err)
		return err
	}

	s.logger.Printf("Successfully sent tile result for job %s, tile %s", result.JobID, result.TileID)
	return nil
}
