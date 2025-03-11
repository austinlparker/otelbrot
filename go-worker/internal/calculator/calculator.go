package calculator

import (
	"bytes"
	"context"
	"fmt"
	"image"
	"image/color"
	"image/png"
	"log"
	"time"

	"github.com/austinlparker/otelbrot/go-worker/internal/models"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/trace"
)

// FractalCalculator calculates Mandelbrot set fractals
type FractalCalculator struct {
	logger *log.Logger
	tracer trace.Tracer
}

// NewFractalCalculator creates a new fractal calculator
func NewFractalCalculator(logger *log.Logger) *FractalCalculator {
	return &FractalCalculator{
		logger: logger,
		tracer: otel.Tracer("fractal-calculator"),
	}
}

// CalculateTile calculates a fractal tile based on the provided specification
func (c *FractalCalculator) CalculateTile(ctx context.Context, spec *models.TileSpec) (*models.TileResult, error) {
	// Extract any existing trace context
	spanContext := trace.SpanContextFromContext(ctx)
	if spanContext.IsValid() {
		c.logger.Printf("Using existing trace context in calculator: traceID=%s, spanID=%s, sampled=%t", 
			spanContext.TraceID().String(), spanContext.SpanID().String(), spanContext.IsSampled())
	} else {
		c.logger.Printf("No valid trace context found in calculator, creating a new trace")
	}

	ctx, span := c.tracer.Start(ctx, "FractalCalculator.CalculateTile",
		trace.WithAttributes(
			attribute.String("jobId", spec.JobID),
			attribute.String("tileId", spec.TileID),
			attribute.Int("width", spec.Width),
			attribute.Int("height", spec.Height),
			attribute.Int("maxIterations", spec.MaxIterations),
			attribute.String("colorScheme", spec.ColorScheme),
			attribute.Float64("xMin", spec.XMin),
			attribute.Float64("xMax", spec.XMax),
			attribute.Float64("yMin", spec.YMin),
			attribute.Float64("yMax", spec.YMax),
		))
	defer span.End()

	spanContext = span.SpanContext()
	c.logger.Printf("Created calculation span: traceID=%s, spanID=%s", 
		spanContext.TraceID().String(), spanContext.SpanID().String())
	c.logger.Printf("Calculating tile for job: %s, tile: %s, dimensions: %dx%d", 
		spec.JobID, spec.TileID, spec.Width, spec.Height)
	startTime := time.Now()

	// Create the image
	img := image.NewRGBA(image.Rect(0, 0, spec.Width, spec.Height))

	// Calculate the pixel size in fractal coordinates
	pixelWidth := (spec.XMax - spec.XMin) / float64(spec.Width)
	pixelHeight := (spec.YMax - spec.YMin) / float64(spec.Height)

	// Calculate the fractal
	for y := 0; y < spec.Height; y++ {
		for x := 0; x < spec.Width; x++ {
			// Convert pixel coordinates to fractal coordinates
			cx := spec.XMin + float64(x)*pixelWidth
			cy := spec.YMin + float64(y)*pixelHeight

			// Calculate the number of iterations
			iterations := c.calculateMandelbrot(cx, cy, spec.MaxIterations)

			// Map the iteration count to a color
			col := c.applyColorMap(iterations, spec.MaxIterations, spec.ColorScheme)

			// Set the pixel color
			img.Set(x, y, col)
		}
	}

	// Encode the image to PNG
	var buf bytes.Buffer
	if err := png.Encode(&buf, img); err != nil {
		span.SetStatus(codes.Error, "Failed to encode image")
		span.RecordError(err)
		return nil, fmt.Errorf("failed to encode image: %w", err)
	}

	// Create the result
	calculationTime := time.Since(startTime).Milliseconds()
	c.logger.Printf("Calculated tile in %d ms", calculationTime)
	span.SetAttributes(attribute.Int64("calculationTimeMs", calculationTime))

	return models.NewTileResultFromCalculation(spec, buf.Bytes(), calculationTime), nil
}

// calculateMandelbrot calculates the Mandelbrot set iteration count for a point
func (c *FractalCalculator) calculateMandelbrot(cx, cy float64, maxIterations int) int {
	// Cardioid and period-2 bulb optimization
	q := (cx-0.25)*(cx-0.25) + cy*cy
	if q*(q+(cx-0.25)) < 0.25*cy*cy {
		return maxIterations
	}

	// Period doubling bulb
	if (cx+1.0)*(cx+1.0)+cy*cy < 0.0625 {
		return maxIterations
	}

	// Standard Mandelbrot iteration
	x, y := 0.0, 0.0
	iteration := 0

	for x*x+y*y < 4.0 && iteration < maxIterations {
		xtemp := x*x - y*y + cx
		y = 2*x*y + cy
		x = xtemp
		iteration++
	}

	return iteration
}

// applyColorMap maps an iteration count to a color
func (c *FractalCalculator) applyColorMap(iterations, maxIterations int, scheme string) color.Color {
	if iterations == maxIterations {
		return color.Black
	}

	switch scheme {
	case "fire":
		return c.fireColorMap(iterations, maxIterations)
	case "ocean":
		return c.oceanColorMap(iterations, maxIterations)
	case "grayscale":
		return c.grayscaleColorMap(iterations, maxIterations)
	case "rainbow":
		return c.rainbowColorMap(iterations, maxIterations)
	default:
		return c.classicColorMap(iterations, maxIterations)
	}
}

// classicColorMap returns a classic color mapping
func (c *FractalCalculator) classicColorMap(iterations, maxIterations int) color.Color {
	if iterations == maxIterations {
		return color.Black
	}

	hue := 0.7 + 0.3*float64(iterations)/float64(maxIterations)
	return hsbToRGB(hue, 0.8, 1.0)
}

// fireColorMap returns a fire-themed color mapping
func (c *FractalCalculator) fireColorMap(iterations, maxIterations int) color.Color {
	if iterations == maxIterations {
		return color.Black
	}

	ratio := float64(iterations) / float64(maxIterations)
	r := uint8(min(255.0, 255.0*min(1.0, ratio*2.0)))
	g := uint8(min(255.0, 255.0*min(1.0, ratio)))
	b := uint8(0)

	return color.RGBA{r, g, b, 255}
}

// oceanColorMap returns an ocean-themed color mapping
func (c *FractalCalculator) oceanColorMap(iterations, maxIterations int) color.Color {
	if iterations == maxIterations {
		return color.Black
	}

	ratio := float64(iterations) / float64(maxIterations)
	r := uint8(0)
	g := uint8(min(255.0, 255.0*min(1.0, ratio)))
	b := uint8(min(255.0, 255.0*min(1.0, ratio*1.5)))

	return color.RGBA{r, g, b, 255}
}

// grayscaleColorMap returns a grayscale color mapping
func (c *FractalCalculator) grayscaleColorMap(iterations, maxIterations int) color.Color {
	if iterations == maxIterations {
		return color.Black
	}

	brightness := 1.0 - float64(iterations)/float64(maxIterations)
	value := uint8(brightness * 255)

	return color.RGBA{value, value, value, 255}
}

// rainbowColorMap returns a rainbow color mapping
func (c *FractalCalculator) rainbowColorMap(iterations, maxIterations int) color.Color {
	if iterations == maxIterations {
		return color.Black
	}

	hue := float64(iterations) / float64(maxIterations)
	return hsbToRGB(hue, 0.85, 1.0)
}

// hsbToRGB converts HSB color to RGB
func hsbToRGB(h, s, v float64) color.Color {
	if s == 0 {
		// Achromatic (gray)
		value := uint8(v * 255)
		return color.RGBA{value, value, value, 255}
	}

	h *= 6 // Sector in the color wheel (0 to 6)
	i := int(h)
	f := h - float64(i) // Fractional part

	p := v * (1 - s)
	q := v * (1 - s*f)
	t := v * (1 - s*(1-f))

	var r, g, b float64
	switch i % 6 {
	case 0:
		r, g, b = v, t, p
	case 1:
		r, g, b = q, v, p
	case 2:
		r, g, b = p, v, t
	case 3:
		r, g, b = p, q, v
	case 4:
		r, g, b = t, p, v
	case 5:
		r, g, b = v, p, q
	}

	return color.RGBA{
		uint8(r * 255),
		uint8(g * 255),
		uint8(b * 255),
		255,
	}
}

// min returns the smaller of x or y
func min(x, y float64) float64 {
	if x < y {
		return x
	}
	return y
}