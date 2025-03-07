package config

import (
	"os"
	"testing"
)

func TestLoad(t *testing.T) {
	// Test loading from environment variables
	t.Run("Load from environment variables", func(t *testing.T) {
		// Save original env vars to restore later
		originalEnv := map[string]string{
			"SERVER_HOST":                 os.Getenv("SERVER_HOST"),
			"SERVER_PORT":                 os.Getenv("SERVER_PORT"),
			"MAX_WORKERS":                 os.Getenv("MAX_WORKERS"),
			"QUEUE_SIZE":                  os.Getenv("QUEUE_SIZE"),
			"SERVICE_NAME":                os.Getenv("SERVICE_NAME"),
			"OTEL_EXPORTER_OTLP_ENDPOINT": os.Getenv("OTEL_EXPORTER_OTLP_ENDPOINT"),
			"TRACE_SAMPLING_RATIO":        os.Getenv("TRACE_SAMPLING_RATIO"),
			"ORCHESTRATOR_URL":            os.Getenv("ORCHESTRATOR_URL"),
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

		// Set test environment variables
		os.Setenv("SERVER_HOST", "test-host")
		os.Setenv("SERVER_PORT", "8888")
		os.Setenv("MAX_WORKERS", "10")
		os.Setenv("QUEUE_SIZE", "200")
		os.Setenv("SERVICE_NAME", "test-service")
		os.Setenv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://test-otel:4317")
		os.Setenv("TRACE_SAMPLING_RATIO", "0.5")
		os.Setenv("ORCHESTRATOR_URL", "http://test-orchestrator:8080")

		// Load config
		cfg, err := Load("")
		if err != nil {
			t.Fatalf("Error loading config: %v", err)
		}

		// Check values
		if cfg.Server.Host != "test-host" {
			t.Errorf("Expected Server.Host to be 'test-host', got '%s'", cfg.Server.Host)
		}
		if cfg.Server.Port != 8888 {
			t.Errorf("Expected Server.Port to be 8888, got %d", cfg.Server.Port)
		}
		if cfg.Fractal.MaxWorkers != 10 {
			t.Errorf("Expected Fractal.MaxWorkers to be 10, got %d", cfg.Fractal.MaxWorkers)
		}
		if cfg.Fractal.QueueSize != 200 {
			t.Errorf("Expected Fractal.QueueSize to be 200, got %d", cfg.Fractal.QueueSize)
		}
		if cfg.Telemetry.ServiceName != "test-service" {
			t.Errorf("Expected Telemetry.ServiceName to be 'test-service', got '%s'", cfg.Telemetry.ServiceName)
		}
		if cfg.Telemetry.CollectorURL != "http://test-otel:4317" {
			t.Errorf("Expected Telemetry.CollectorURL to be 'http://test-otel:4317', got '%s'", cfg.Telemetry.CollectorURL)
		}
		if cfg.Telemetry.TraceSamplingRatio != 0.5 {
			t.Errorf("Expected Telemetry.TraceSamplingRatio to be 0.5, got %f", cfg.Telemetry.TraceSamplingRatio)
		}
		if cfg.Orchestrator.URL != "http://test-orchestrator:8080" {
			t.Errorf("Expected Orchestrator.URL to be 'http://test-orchestrator:8080', got '%s'", cfg.Orchestrator.URL)
		}
	})

	// Test loading from file
	t.Run("Load from file", func(t *testing.T) {
		// Create a temporary config file
		configFile, err := os.CreateTemp("", "config-*.json")
		if err != nil {
			t.Fatalf("Error creating temporary file: %v", err)
		}
		defer os.Remove(configFile.Name())

		// Write config JSON
		configJSON := `{
			"server": {
				"host": "file-host",
				"port": 9999
			},
			"fractal": {
				"maxWorkers": 20,
				"queueSize": 300
			},
			"telemetry": {
				"serviceName": "file-service",
				"collectorUrl": "http://file-otel:4317",
				"traceSamplingRatio": 0.75
			},
			"orchestrator": {
				"url": "http://file-orchestrator:8080"
			}
		}`
		if _, err := configFile.Write([]byte(configJSON)); err != nil {
			t.Fatalf("Error writing to temporary file: %v", err)
		}
		if err := configFile.Close(); err != nil {
			t.Fatalf("Error closing temporary file: %v", err)
		}

		// Load config from file
		cfg, err := Load(configFile.Name())
		if err != nil {
			t.Fatalf("Error loading config: %v", err)
		}

		// Check values
		if cfg.Server.Host != "file-host" {
			t.Errorf("Expected Server.Host to be 'file-host', got '%s'", cfg.Server.Host)
		}
		if cfg.Server.Port != 9999 {
			t.Errorf("Expected Server.Port to be 9999, got %d", cfg.Server.Port)
		}
		if cfg.Fractal.MaxWorkers != 20 {
			t.Errorf("Expected Fractal.MaxWorkers to be 20, got %d", cfg.Fractal.MaxWorkers)
		}
		if cfg.Fractal.QueueSize != 300 {
			t.Errorf("Expected Fractal.QueueSize to be 300, got %d", cfg.Fractal.QueueSize)
		}
		if cfg.Telemetry.ServiceName != "file-service" {
			t.Errorf("Expected Telemetry.ServiceName to be 'file-service', got '%s'", cfg.Telemetry.ServiceName)
		}
		if cfg.Telemetry.CollectorURL != "http://file-otel:4317" {
			t.Errorf("Expected Telemetry.CollectorURL to be 'http://file-otel:4317', got '%s'", cfg.Telemetry.CollectorURL)
		}
		if cfg.Telemetry.TraceSamplingRatio != 0.75 {
			t.Errorf("Expected Telemetry.TraceSamplingRatio to be 0.75, got %f", cfg.Telemetry.TraceSamplingRatio)
		}
		if cfg.Orchestrator.URL != "http://file-orchestrator:8080" {
			t.Errorf("Expected Orchestrator.URL to be 'http://file-orchestrator:8080', got '%s'", cfg.Orchestrator.URL)
		}
	})

	// Test loading with invalid file
	t.Run("Load with invalid file", func(t *testing.T) {
		_, err := Load("/non/existent/file.json")
		if err == nil {
			t.Fatal("Expected error for non-existent file, got nil")
		}
	})

	// Test loading with invalid JSON
	t.Run("Load with invalid JSON", func(t *testing.T) {
		// Create a temporary config file
		configFile, err := os.CreateTemp("", "config-*.json")
		if err != nil {
			t.Fatalf("Error creating temporary file: %v", err)
		}
		defer os.Remove(configFile.Name())

		// Write invalid JSON
		if _, err := configFile.Write([]byte("invalid JSON")); err != nil {
			t.Fatalf("Error writing to temporary file: %v", err)
		}
		if err := configFile.Close(); err != nil {
			t.Fatalf("Error closing temporary file: %v", err)
		}

		// Load config from file
		_, err = Load(configFile.Name())
		if err == nil {
			t.Fatal("Expected error for invalid JSON, got nil")
		}
	})
}

func TestHelperFunctions(t *testing.T) {
	// Test getEnv
	t.Run("getEnv", func(t *testing.T) {
		// Test with existing variable
		os.Setenv("TEST_ENV_VAR", "test-value")
		if getEnv("TEST_ENV_VAR", "default") != "test-value" {
			t.Error("Expected getEnv to return 'test-value'")
		}

		// Test with non-existing variable
		os.Unsetenv("TEST_ENV_VAR")
		if getEnv("TEST_ENV_VAR", "default") != "default" {
			t.Error("Expected getEnv to return 'default'")
		}
	})

	// Test getEnvAsInt
	t.Run("getEnvAsInt", func(t *testing.T) {
		// Test with valid int
		os.Setenv("TEST_ENV_INT", "123")
		if getEnvAsInt("TEST_ENV_INT", 456) != 123 {
			t.Error("Expected getEnvAsInt to return 123")
		}

		// Test with invalid int
		os.Setenv("TEST_ENV_INT", "not-an-int")
		if getEnvAsInt("TEST_ENV_INT", 456) != 456 {
			t.Error("Expected getEnvAsInt to return default 456 for invalid input")
		}

		// Test with non-existing variable
		os.Unsetenv("TEST_ENV_INT")
		if getEnvAsInt("TEST_ENV_INT", 456) != 456 {
			t.Error("Expected getEnvAsInt to return default 456 for non-existing variable")
		}
	})

	// Test getEnvAsFloat
	t.Run("getEnvAsFloat", func(t *testing.T) {
		// Test with valid float
		os.Setenv("TEST_ENV_FLOAT", "1.23")
		if getEnvAsFloat("TEST_ENV_FLOAT", 4.56) != 1.23 {
			t.Error("Expected getEnvAsFloat to return 1.23")
		}

		// Test with invalid float
		os.Setenv("TEST_ENV_FLOAT", "not-a-float")
		if getEnvAsFloat("TEST_ENV_FLOAT", 4.56) != 4.56 {
			t.Error("Expected getEnvAsFloat to return default 4.56 for invalid input")
		}

		// Test with non-existing variable
		os.Unsetenv("TEST_ENV_FLOAT")
		if getEnvAsFloat("TEST_ENV_FLOAT", 4.56) != 4.56 {
			t.Error("Expected getEnvAsFloat to return default 4.56 for non-existing variable")
		}
	})
}