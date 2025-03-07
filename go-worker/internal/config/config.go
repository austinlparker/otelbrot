package config

import (
	"encoding/json"
	"fmt"
	"os"
	"strconv"
)

// Config holds all configuration for the worker
type Config struct {
	Server     ServerConfig     `json:"server"`
	Fractal    FractalConfig    `json:"fractal"`
	Telemetry  TelemetryConfig  `json:"telemetry"`
	Orchestrator OrchestratorConfig `json:"orchestrator"`
}

// ServerConfig holds the HTTP server configuration
type ServerConfig struct {
	Host string `json:"host"`
	Port int    `json:"port"`
}

// FractalConfig holds the fractal calculation configuration
type FractalConfig struct {
	MaxWorkers int `json:"maxWorkers"`
	QueueSize  int `json:"queueSize"`
}

// TelemetryConfig holds the OpenTelemetry configuration
type TelemetryConfig struct {
	ServiceName    string `json:"serviceName"`
	CollectorURL   string `json:"collectorUrl"`
	TraceSamplingRatio float64 `json:"traceSamplingRatio"`
}

// OrchestratorConfig holds the configuration for connecting to the orchestrator
type OrchestratorConfig struct {
	URL string `json:"url"`
}

// Load loads the configuration from a file and environment variables
func Load(configFile string) (*Config, error) {
	// Default configuration
	cfg := &Config{
		Server: ServerConfig{
			Host: "0.0.0.0",
			Port: 8081,
		},
		Fractal: FractalConfig{
			MaxWorkers: getEnvAsInt("MAX_WORKERS", 4),
			QueueSize:  getEnvAsInt("QUEUE_SIZE", 100),
		},
		Telemetry: TelemetryConfig{
			ServiceName:  getEnv("SERVICE_NAME", "go-worker"),
			CollectorURL: getEnv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://localhost:4317"),
			TraceSamplingRatio: getEnvAsFloat("TRACE_SAMPLING_RATIO", 1.0),
		},
		Orchestrator: OrchestratorConfig{
			URL: getEnv("ORCHESTRATOR_URL", "http://localhost:8080"),
		},
	}

	// Load configuration from file if provided
	if configFile != "" {
		file, err := os.Open(configFile)
		if err != nil {
			return nil, fmt.Errorf("failed to open config file: %w", err)
		}
		defer file.Close()

		decoder := json.NewDecoder(file)
		if err := decoder.Decode(cfg); err != nil {
			return nil, fmt.Errorf("failed to decode config file: %w", err)
		}
	}

	// Override with environment variables
	if host := os.Getenv("SERVER_HOST"); host != "" {
		cfg.Server.Host = host
	}
	if port := getEnvAsInt("SERVER_PORT", 0); port != 0 {
		cfg.Server.Port = port
	}
	if url := os.Getenv("ORCHESTRATOR_URL"); url != "" {
		cfg.Orchestrator.URL = url
	}

	return cfg, nil
}

// Helper function to get an environment variable with a default value
func getEnv(key, defaultValue string) string {
	if value, exists := os.LookupEnv(key); exists {
		return value
	}
	return defaultValue
}

// Helper function to get an environment variable as an integer
func getEnvAsInt(key string, defaultValue int) int {
	strValue := getEnv(key, "")
	if strValue == "" {
		return defaultValue
	}
	
	value, err := strconv.Atoi(strValue)
	if err != nil {
		return defaultValue
	}
	return value
}

// Helper function to get an environment variable as a float
func getEnvAsFloat(key string, defaultValue float64) float64 {
	strValue := getEnv(key, "")
	if strValue == "" {
		return defaultValue
	}
	
	value, err := strconv.ParseFloat(strValue, 64)
	if err != nil {
		return defaultValue
	}
	return value
}