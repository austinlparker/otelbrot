package models

import (
	"os"
	"testing"
)

func TestNewTileSpecFromEnvironment(t *testing.T) {
	// Save original env vars to restore later
	originalEnv := map[string]string{}
	envVars := []string{
		"TILE_SPEC_JOB_ID",
		"TILE_SPEC_TILE_ID",
		"TILE_SPEC_X_MIN",
		"TILE_SPEC_Y_MIN",
		"TILE_SPEC_X_MAX",
		"TILE_SPEC_Y_MAX",
		"TILE_SPEC_WIDTH",
		"TILE_SPEC_HEIGHT",
		"TILE_SPEC_MAX_ITERATIONS",
		"TILE_SPEC_COLOR_SCHEME",
		"TILE_SPEC_PIXEL_START_X",
		"TILE_SPEC_PIXEL_START_Y",
	}

	for _, env := range envVars {
		originalEnv[env] = os.Getenv(env)
	}

	// Restore env vars after test
	defer func() {
		for k, v := range originalEnv {
			if v == "" {
				os.Unsetenv(k)
			} else {
				os.Setenv(k, v)
			}
		}
	}()

	// Test with valid environment variables
	t.Run("Valid environment variables", func(t *testing.T) {
		// Set environment variables
		os.Setenv("TILE_SPEC_JOB_ID", "test-job")
		os.Setenv("TILE_SPEC_TILE_ID", "test-tile")
		os.Setenv("TILE_SPEC_X_MIN", "-2.0")
		os.Setenv("TILE_SPEC_Y_MIN", "-1.5")
		os.Setenv("TILE_SPEC_X_MAX", "1.0")
		os.Setenv("TILE_SPEC_Y_MAX", "1.5")
		os.Setenv("TILE_SPEC_WIDTH", "800")
		os.Setenv("TILE_SPEC_HEIGHT", "600")
		os.Setenv("TILE_SPEC_MAX_ITERATIONS", "100")
		os.Setenv("TILE_SPEC_COLOR_SCHEME", "classic")
		os.Setenv("TILE_SPEC_PIXEL_START_X", "0")
		os.Setenv("TILE_SPEC_PIXEL_START_Y", "0")

		// Create tile spec from environment
		spec, err := NewTileSpecFromEnvironment()
		if err != nil {
			t.Fatalf("Error creating tile spec: %v", err)
		}

		// Check values
		if spec.JobID != "test-job" {
			t.Errorf("Expected JobID to be 'test-job', got '%s'", spec.JobID)
		}
		if spec.TileID != "test-tile" {
			t.Errorf("Expected TileID to be 'test-tile', got '%s'", spec.TileID)
		}
		if spec.XMin != -2.0 {
			t.Errorf("Expected XMin to be -2.0, got %f", spec.XMin)
		}
		if spec.YMin != -1.5 {
			t.Errorf("Expected YMin to be -1.5, got %f", spec.YMin)
		}
		if spec.XMax != 1.0 {
			t.Errorf("Expected XMax to be 1.0, got %f", spec.XMax)
		}
		if spec.YMax != 1.5 {
			t.Errorf("Expected YMax to be 1.5, got %f", spec.YMax)
		}
		if spec.Width != 800 {
			t.Errorf("Expected Width to be 800, got %d", spec.Width)
		}
		if spec.Height != 600 {
			t.Errorf("Expected Height to be 600, got %d", spec.Height)
		}
		if spec.MaxIterations != 100 {
			t.Errorf("Expected MaxIterations to be 100, got %d", spec.MaxIterations)
		}
		if spec.ColorScheme != "classic" {
			t.Errorf("Expected ColorScheme to be 'classic', got '%s'", spec.ColorScheme)
		}
		if spec.PixelStartX != 0 {
			t.Errorf("Expected PixelStartX to be 0, got %d", spec.PixelStartX)
		}
		if spec.PixelStartY != 0 {
			t.Errorf("Expected PixelStartY to be 0, got %d", spec.PixelStartY)
		}
	})

	// Test with missing environment variables
	t.Run("Missing environment variables", func(t *testing.T) {
		// Clear all environment variables
		for _, env := range envVars {
			os.Unsetenv(env)
		}

		// Try to create tile spec
		_, err := NewTileSpecFromEnvironment()
		if err == nil {
			t.Fatal("Expected error for missing environment variables, got nil")
		}
	})

	// Test with invalid environment variables
	t.Run("Invalid environment variables", func(t *testing.T) {
		// Set valid environment variables
		os.Setenv("TILE_SPEC_JOB_ID", "test-job")
		os.Setenv("TILE_SPEC_TILE_ID", "test-tile")
		os.Setenv("TILE_SPEC_X_MIN", "-2.0")
		os.Setenv("TILE_SPEC_Y_MIN", "-1.5")
		os.Setenv("TILE_SPEC_X_MAX", "1.0")
		os.Setenv("TILE_SPEC_Y_MAX", "1.5")
		os.Setenv("TILE_SPEC_WIDTH", "800")
		os.Setenv("TILE_SPEC_HEIGHT", "600")
		os.Setenv("TILE_SPEC_MAX_ITERATIONS", "100")
		os.Setenv("TILE_SPEC_COLOR_SCHEME", "classic")
		os.Setenv("TILE_SPEC_PIXEL_START_X", "0")
		os.Setenv("TILE_SPEC_PIXEL_START_Y", "0")

		// Test with invalid XMin
		os.Setenv("TILE_SPEC_X_MIN", "invalid")
		_, err := NewTileSpecFromEnvironment()
		if err == nil {
			t.Fatal("Expected error for invalid XMin, got nil")
		}

		// Reset to valid value
		os.Setenv("TILE_SPEC_X_MIN", "-2.0")

		// Test with invalid Width
		os.Setenv("TILE_SPEC_WIDTH", "invalid")
		_, err = NewTileSpecFromEnvironment()
		if err == nil {
			t.Fatal("Expected error for invalid Width, got nil")
		}
	})
}

func TestNewTileResultFromCalculation(t *testing.T) {
	// Create a test tile spec
	spec := &TileSpec{
		JobID:         "test-job",
		TileID:        "test-tile",
		Width:         800,
		Height:        600,
		PixelStartX:   0,
		PixelStartY:   0,
		MaxIterations: 100,
	}

	// Create test image data
	imageData := []byte("test image data")

	// Create a result from calculation
	result := NewTileResultFromCalculation(spec, imageData, 123)

	// Check values
	if result.JobID != "test-job" {
		t.Errorf("Expected JobID to be 'test-job', got '%s'", result.JobID)
	}
	if result.TileID != "test-tile" {
		t.Errorf("Expected TileID to be 'test-tile', got '%s'", result.TileID)
	}
	if result.Width != 800 {
		t.Errorf("Expected Width to be 800, got %d", result.Width)
	}
	if result.Height != 600 {
		t.Errorf("Expected Height to be 600, got %d", result.Height)
	}
	if string(result.ImageData) != "test image data" {
		t.Errorf("Expected ImageData to be 'test image data', got '%s'", string(result.ImageData))
	}
	if result.CalculationTimeMs != 123 {
		t.Errorf("Expected CalculationTimeMs to be 123, got %d", result.CalculationTimeMs)
	}
	if result.Status != TileStatusCompleted {
		t.Errorf("Expected Status to be %s, got %s", TileStatusCompleted, result.Status)
	}
}

func TestNewTileResultFromError(t *testing.T) {
	// Create a test tile spec
	spec := &TileSpec{
		JobID:         "test-job",
		TileID:        "test-tile",
		Width:         800,
		Height:        600,
		PixelStartX:   0,
		PixelStartY:   0,
		MaxIterations: 100,
	}

	// Create a result from error
	errorMessage := "test error message"
	result := NewTileResultFromError(spec, errorMessage)

	// Check values
	if result.JobID != "test-job" {
		t.Errorf("Expected JobID to be 'test-job', got '%s'", result.JobID)
	}
	if result.TileID != "test-tile" {
		t.Errorf("Expected TileID to be 'test-tile', got '%s'", result.TileID)
	}
	if result.Width != 800 {
		t.Errorf("Expected Width to be 800, got %d", result.Width)
	}
	if result.Height != 600 {
		t.Errorf("Expected Height to be 600, got %d", result.Height)
	}
	if string(result.ImageData) != errorMessage {
		t.Errorf("Expected ImageData to be '%s', got '%s'", errorMessage, string(result.ImageData))
	}
	if result.CalculationTimeMs != 0 {
		t.Errorf("Expected CalculationTimeMs to be 0, got %d", result.CalculationTimeMs)
	}
	if result.Status != TileStatusFailed {
		t.Errorf("Expected Status to be %s, got %s", TileStatusFailed, result.Status)
	}
}

func TestTileResultMarshalJSON(t *testing.T) {
	// Create a test result
	result := &TileResult{
		JobID:             "test-job",
		TileID:            "test-tile",
		Width:             800,
		Height:            600,
		ImageData:         []byte("test image data"),
		PixelStartX:       0,
		PixelStartY:       0,
		CalculationTimeMs: 123,
		Status:            TileStatusCompleted,
	}

	// Marshal to JSON
	jsonData, err := result.MarshalJSON()
	if err != nil {
		t.Fatalf("Error marshaling tile result: %v", err)
	}

	// Check that the JSON contains expected values
	jsonStr := string(jsonData)
	expectedValues := []string{
		"test-job",
		"test-tile",
		"800",
		"600",
		"COMPLETED",
	}

	for _, expected := range expectedValues {
		if !contains(jsonStr, expected) {
			t.Errorf("Expected JSON to contain '%s', but it doesn't: %s", expected, jsonStr)
		}
	}
}

// Helper function to check if a string contains a substring
func contains(s, substr string) bool {
	return s != "" && s != substr && len(s) > len(substr) && s[:len(substr)] != substr && s[len(s)-len(substr):] != substr && s[1:len(s)-1] != substr
}