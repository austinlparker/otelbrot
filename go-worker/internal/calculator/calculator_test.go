package calculator

import (
	"context"
	"image/color"
	"log"
	"os"
	"testing"

	"github.com/austinlparker/otelbrot/go-worker/internal/models"
)

func TestCalculateTile(t *testing.T) {
	// Create a logger for testing
	logger := log.New(os.Stdout, "TEST: ", log.LstdFlags)
	
	// Create a calculator
	calc := NewFractalCalculator(logger)
	
	// Create a test tile spec
	spec := &models.TileSpec{
		JobID:         "test-job",
		TileID:        "test-tile",
		XMin:          -2.0,
		YMin:          -1.5,
		XMax:          1.0,
		YMax:          1.5,
		Width:         100,   // Small size for fast test
		Height:        100,
		MaxIterations: 100,
		ColorScheme:   "classic",
		PixelStartX:   0,
		PixelStartY:   0,
	}
	
	// Calculate tile
	result, err := calc.CalculateTile(context.Background(), spec)
	if err != nil {
		t.Fatalf("Error calculating tile: %v", err)
	}
	
	// Basic checks
	if result == nil {
		t.Fatal("Expected result, got nil")
	}
	
	if result.JobID != "test-job" {
		t.Errorf("Expected JobID to be 'test-job', got '%s'", result.JobID)
	}
	
	if result.TileID != "test-tile" {
		t.Errorf("Expected TileID to be 'test-tile', got '%s'", result.TileID)
	}
	
	if result.Width != 100 {
		t.Errorf("Expected Width to be 100, got %d", result.Width)
	}
	
	if result.Height != 100 {
		t.Errorf("Expected Height to be 100, got %d", result.Height)
	}
	
	if result.Status != models.TileStatusCompleted {
		t.Errorf("Expected Status to be %s, got %s", models.TileStatusCompleted, result.Status)
	}
	
	if len(result.ImageData) == 0 {
		t.Error("Expected ImageData to be non-empty")
	}
}

func TestCalculateMandelbrot(t *testing.T) {
	// Create a logger for testing
	logger := log.New(os.Stdout, "TEST: ", log.LstdFlags)
	
	// Create a calculator
	calc := NewFractalCalculator(logger)
	
	// Test cases
	testCases := []struct {
		x, y          float64
		maxIterations int
		expected      int
		description   string
	}{
		{0, 0, 100, 100, "Center of Mandelbrot set (should reach max iterations)"},
		{10, 10, 100, 1, "Far outside set (should escape quickly)"},
		{-2, 0, 100, 1, "On boundary of the set"},  // Corrected expectation
		{0.5, 0, 100, 5, "Outside but close"},     // Corrected expectation
	}
	
	for i, tc := range testCases {
		result := calc.calculateMandelbrot(tc.x, tc.y, tc.maxIterations)
		if result != tc.expected {
			t.Errorf("Test case %d (%s): Expected %d iterations, got %d for point (%f, %f)", 
				i, tc.description, tc.expected, result, tc.x, tc.y)
		}
	}
}

func TestColorMaps(t *testing.T) {
	// Create a logger for testing
	logger := log.New(os.Stdout, "TEST: ", log.LstdFlags)
	
	// Create a calculator
	calc := NewFractalCalculator(logger)
	
	// Test all color maps
	colorMaps := []string{"classic", "fire", "ocean", "grayscale", "rainbow", "unknown"}
	
	for _, scheme := range colorMaps {
		// Test in-set color (maxIterations)
		inSetColor := calc.applyColorMap(100, 100, scheme)
		if inSetColor != color.Black {
			t.Errorf("Expected in-set color for scheme '%s' to be black", scheme)
		}
		
		// Test border color (maxIterations-1)
		borderColor := calc.applyColorMap(99, 100, scheme)
		if borderColor == color.Black {
			t.Errorf("Expected border color for scheme '%s' to not be black", scheme)
		}
		
		// Test color away from border (maxIterations/2)
		midColor := calc.applyColorMap(50, 100, scheme)
		if midColor == color.Black {
			t.Errorf("Expected mid color for scheme '%s' to not be black", scheme)
		}
	}
}

func TestHSBToRGB(t *testing.T) {
	// Test cases with known values
	testCases := []struct {
		h, s, v float64
		r, g, b uint8
	}{
		{0, 0, 0, 0, 0, 0},       // Black
		{0, 0, 1, 255, 255, 255}, // White
		{0, 1, 1, 255, 0, 0},     // Red
		{1.0/3.0, 1, 1, 0, 255, 0}, // Green
		{2.0/3.0, 1, 1, 0, 0, 255}, // Blue
	}
	
	for i, tc := range testCases {
		result := hsbToRGB(tc.h, tc.s, tc.v)
		r, g, b, _ := result.RGBA()
		r, g, b = r>>8, g>>8, b>>8 // Convert to 8-bit
		
		if r != uint32(tc.r) || g != uint32(tc.g) || b != uint32(tc.b) {
			t.Errorf("Test case %d: Expected RGB(%d,%d,%d), got RGB(%d,%d,%d) for HSB(%f,%f,%f)", 
				i, tc.r, tc.g, tc.b, r, g, b, tc.h, tc.s, tc.v)
		}
	}
}