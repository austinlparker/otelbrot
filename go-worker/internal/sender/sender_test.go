package sender

import (
	"context"
	"log"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"

	"github.com/austinlparker/otelbrot/go-worker/internal/models"
)

func TestSendResult(t *testing.T) {
	// Create a logger for testing
	logger := log.New(os.Stdout, "TEST: ", log.LstdFlags)
	
	// Test with successful response
	t.Run("Success", func(t *testing.T) {
		// Create a test server
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			// Check request path
			if r.URL.Path != "/api/fractal/tile-result" {
				t.Errorf("Expected path to be '/api/fractal/tile-result', got '%s'", r.URL.Path)
			}
			
			// Check content type
			contentType := r.Header.Get("Content-Type")
			if contentType != "application/json" {
				t.Errorf("Expected Content-Type to be 'application/json', got '%s'", contentType)
			}
			
			// Check method
			if r.Method != http.MethodPost {
				t.Errorf("Expected method to be POST, got '%s'", r.Method)
			}
			
			// Return 202 Accepted
			w.WriteHeader(http.StatusAccepted)
		}))
		defer server.Close()
		
		// Create result sender with test server URL
		sender := NewResultSender(server.URL, logger)
		
		// Create a test result
		result := &models.TileResult{
			JobID:             "test-job",
			TileID:            "test-tile",
			Width:             800,
			Height:            600,
			ImageData:         []byte("test image data"),
			PixelStartX:       0,
			PixelStartY:       0,
			CalculationTimeMs: 123,
			Status:            models.TileStatusCompleted,
		}
		
		// Send result
		err := sender.SendResult(context.Background(), result)
		if err != nil {
			t.Fatalf("Error sending result: %v", err)
		}
	})
	
	// Test with error response
	t.Run("Error Response", func(t *testing.T) {
		// Create a test server that returns an error
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.WriteHeader(http.StatusInternalServerError)
		}))
		defer server.Close()
		
		// Create result sender with test server URL
		sender := NewResultSender(server.URL, logger)
		
		// Create a test result
		result := &models.TileResult{
			JobID:             "test-job",
			TileID:            "test-tile",
			Width:             800,
			Height:            600,
			ImageData:         []byte("test image data"),
			PixelStartX:       0,
			PixelStartY:       0,
			CalculationTimeMs: 123,
			Status:            models.TileStatusCompleted,
		}
		
		// Send result
		err := sender.SendResult(context.Background(), result)
		if err == nil {
			t.Fatal("Expected error for 500 response, got nil")
		}
	})
	
	// Test with connection error
	t.Run("Connection Error", func(t *testing.T) {
		// Create result sender with invalid URL
		sender := NewResultSender("http://invalid-url-that-doesnt-exist.example", logger)
		
		// Create a test result
		result := &models.TileResult{
			JobID:             "test-job",
			TileID:            "test-tile",
			Width:             800,
			Height:            600,
			ImageData:         []byte("test image data"),
			PixelStartX:       0,
			PixelStartY:       0,
			CalculationTimeMs: 123,
			Status:            models.TileStatusCompleted,
		}
		
		// Send result
		err := sender.SendResult(context.Background(), result)
		if err == nil {
			t.Fatal("Expected error for connection failure, got nil")
		}
	})
}