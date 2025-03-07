package models

import (
	"encoding/json"
	"fmt"
	"os"
	"strconv"
)

// TileStatus represents the status of a tile computation
type TileStatus string

const (
	TileStatusCompleted TileStatus = "COMPLETED"
	TileStatusFailed    TileStatus = "FAILED"
	TileStatusProcessing TileStatus = "PROCESSING"
)

// TileSpec represents a specification for a fractal tile to be computed
type TileSpec struct {
	JobID         string  `json:"jobId"`
	TileID        string  `json:"tileId"`
	XMin          float64 `json:"xMin"`
	YMin          float64 `json:"yMin"`
	XMax          float64 `json:"xMax"`
	YMax          float64 `json:"yMax"`
	Width         int     `json:"width"`
	Height        int     `json:"height"`
	MaxIterations int     `json:"maxIterations"`
	ColorScheme   string  `json:"colorScheme"`
	PixelStartX   int     `json:"pixelStartX"`
	PixelStartY   int     `json:"pixelStartY"`
}

// TileResult represents the result of a fractal tile computation
type TileResult struct {
	JobID             string     `json:"jobId"`
	TileID            string     `json:"tileId"`
	Width             int        `json:"width"`
	Height            int        `json:"height"`
	ImageData         []byte     `json:"imageData"`
	PixelStartX       int        `json:"pixelStartX"`
	PixelStartY       int        `json:"pixelStartY"`
	CalculationTimeMs int64      `json:"calculationTimeMs"`
	Status            TileStatus `json:"status"`
}

// NewTileSpecFromEnvironment creates a new TileSpec from environment variables
func NewTileSpecFromEnvironment() (*TileSpec, error) {
	jobID := os.Getenv("TILE_SPEC_JOB_ID")
	tileID := os.Getenv("TILE_SPEC_TILE_ID")
	
	xMinStr := os.Getenv("TILE_SPEC_X_MIN")
	yMinStr := os.Getenv("TILE_SPEC_Y_MIN")
	xMaxStr := os.Getenv("TILE_SPEC_X_MAX")
	yMaxStr := os.Getenv("TILE_SPEC_Y_MAX")
	widthStr := os.Getenv("TILE_SPEC_WIDTH")
	heightStr := os.Getenv("TILE_SPEC_HEIGHT")
	maxIterationsStr := os.Getenv("TILE_SPEC_MAX_ITERATIONS")
	pixelStartXStr := os.Getenv("TILE_SPEC_PIXEL_START_X")
	pixelStartYStr := os.Getenv("TILE_SPEC_PIXEL_START_Y")
	colorScheme := os.Getenv("TILE_SPEC_COLOR_SCHEME")
	
	// Basic validation
	if jobID == "" || tileID == "" || xMinStr == "" || yMinStr == "" || xMaxStr == "" ||
		yMaxStr == "" || widthStr == "" || heightStr == "" || maxIterationsStr == "" ||
		pixelStartXStr == "" || pixelStartYStr == "" {
		return nil, fmt.Errorf("missing required environment variables for TileSpec")
	}
	
	// Parse floating point values
	xMin, err := strconv.ParseFloat(xMinStr, 64)
	if err != nil {
		return nil, fmt.Errorf("invalid xMin: %w", err)
	}
	
	yMin, err := strconv.ParseFloat(yMinStr, 64)
	if err != nil {
		return nil, fmt.Errorf("invalid yMin: %w", err)
	}
	
	xMax, err := strconv.ParseFloat(xMaxStr, 64)
	if err != nil {
		return nil, fmt.Errorf("invalid xMax: %w", err)
	}
	
	yMax, err := strconv.ParseFloat(yMaxStr, 64)
	if err != nil {
		return nil, fmt.Errorf("invalid yMax: %w", err)
	}
	
	// Parse integer values
	width, err := strconv.Atoi(widthStr)
	if err != nil {
		return nil, fmt.Errorf("invalid width: %w", err)
	}
	
	height, err := strconv.Atoi(heightStr)
	if err != nil {
		return nil, fmt.Errorf("invalid height: %w", err)
	}
	
	maxIterations, err := strconv.Atoi(maxIterationsStr)
	if err != nil {
		return nil, fmt.Errorf("invalid maxIterations: %w", err)
	}
	
	pixelStartX, err := strconv.Atoi(pixelStartXStr)
	if err != nil {
		return nil, fmt.Errorf("invalid pixelStartX: %w", err)
	}
	
	pixelStartY, err := strconv.Atoi(pixelStartYStr)
	if err != nil {
		return nil, fmt.Errorf("invalid pixelStartY: %w", err)
	}
	
	return &TileSpec{
		JobID:         jobID,
		TileID:        tileID,
		XMin:          xMin,
		YMin:          yMin,
		XMax:          xMax,
		YMax:          yMax,
		Width:         width,
		Height:        height,
		MaxIterations: maxIterations,
		ColorScheme:   colorScheme,
		PixelStartX:   pixelStartX,
		PixelStartY:   pixelStartY,
	}, nil
}

// MarshalJSON custom marshaller that encodes the byte array as base64
func (r TileResult) MarshalJSON() ([]byte, error) {
	type Alias TileResult
	return json.Marshal(&struct {
		ImageData []byte `json:"imageData"`
		Status    string `json:"status"`
		Alias
	}{
		ImageData: r.ImageData,
		Status:    string(r.Status),
		Alias:     Alias(r),
	})
}

// NewTileResultFromCalculation creates a new TileResult from a calculation
func NewTileResultFromCalculation(spec *TileSpec, imageData []byte, calculationTime int64) *TileResult {
	return &TileResult{
		JobID:             spec.JobID,
		TileID:            spec.TileID,
		Width:             spec.Width,
		Height:            spec.Height,
		ImageData:         imageData,
		PixelStartX:       spec.PixelStartX,
		PixelStartY:       spec.PixelStartY,
		CalculationTimeMs: calculationTime,
		Status:            TileStatusCompleted,
	}
}

// NewTileResultFromError creates a new TileResult for a failed calculation
func NewTileResultFromError(spec *TileSpec, errorMessage string) *TileResult {
	return &TileResult{
		JobID:             spec.JobID,
		TileID:            spec.TileID,
		Width:             spec.Width,
		Height:            spec.Height,
		ImageData:         []byte(errorMessage),
		PixelStartX:       spec.PixelStartX,
		PixelStartY:       spec.PixelStartY,
		CalculationTimeMs: 0,
		Status:            TileStatusFailed,
	}
}