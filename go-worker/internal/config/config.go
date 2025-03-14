package config

import (
	"log"
	"os"
	"strconv"
)

// Config holds all configuration for the worker
type Config struct {
	Server       ServerConfig     `json:"server"`
	Fractal      FractalConfig    `json:"fractal"`
	Telemetry    TelemetryConfig  `json:"telemetry"`
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
	ServiceName        string  `json:"serviceName"`
	CollectorURL       string  `json:"collectorUrl"`
	TraceSamplingRatio float64 `json:"traceSamplingRatio"`
}

// OrchestratorConfig holds the configuration for connecting to the orchestrator
type OrchestratorConfig struct {
	URL string `json:"url"`
}

// Load loads the configuration from environment variables
func Load() (*Config, error) {
	logger := log.New(os.Stdout, "[config] ", log.LstdFlags)
	logger.Println("Loading configuration from environment")
	
	// Create configuration populated from environment variables
	cfg := &Config{
		Server: ServerConfig{
			Host: getEnv("SERVER_HOST", "0.0.0.0"),
			Port: getEnvAsInt("SERVER_PORT", 8081),
		},
		Fractal: FractalConfig{
			MaxWorkers: getEnvAsInt("MAX_WORKERS", 4),
			QueueSize:  getEnvAsInt("QUEUE_SIZE", 100),
		},
		Telemetry: TelemetryConfig{
			ServiceName:        getEnv("SERVICE_NAME", "go-worker"),
			CollectorURL:       getEnv("OTEL_EXPORTER_OTLP_ENDPOINT", "localhost:4317"),
			TraceSamplingRatio: getEnvAsFloat("TRACE_SAMPLING_RATIO", 1.0),
		},
		Orchestrator: OrchestratorConfig{
			URL: getEnv("ORCHESTRATOR_URL", "http://localhost:8080"),
		},
	}

	// Log the loaded configuration
	logger.Printf("Configuration loaded successfully:")
	logger.Printf("- Service name: %s", cfg.Telemetry.ServiceName)
	logger.Printf("- Orchestrator URL: %s", cfg.Orchestrator.URL)
	logger.Printf("- Max workers: %d", cfg.Fractal.MaxWorkers)

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