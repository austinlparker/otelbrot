package io.aparker.otelbrot.worker.service;

import io.aparker.otelbrot.commons.model.TileResult;
import io.aparker.otelbrot.commons.model.TileSpec;
import io.aparker.otelbrot.commons.model.TileStatus;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Service for calculating fractal tiles
 */
@Service
public class FractalCalculator {
    private static final Logger logger = LoggerFactory.getLogger(FractalCalculator.class);
    private static final int MAX_ESCAPE = 4; // Escape radius squared
    
    private final ColorMapper colorMapper;
    private final Tracer tracer;

    public FractalCalculator(ColorMapper colorMapper, Tracer tracer) {
        this.colorMapper = colorMapper;
        this.tracer = tracer;
    }

    /**
     * Calculate a tile of the Mandelbrot set
     */
    public TileResult calculateTile(TileSpec spec) {
        // Explicitly get current context to ensure we capture the parent span
        io.opentelemetry.context.Context currentContext = io.opentelemetry.context.Context.current();
        
        // Get the current span to log its trace ID
        Span currentSpan = Span.current();
        if (currentSpan != null && !currentSpan.equals(Span.getInvalid())) {
            logger.info("Current span before calculateTile: traceId={}, spanId={}", 
                currentSpan.getSpanContext().getTraceId(), 
                currentSpan.getSpanContext().getSpanId());
        } else {
            logger.warn("No current span available in calculateTile");
        }
        
        // Create a child span from the current context
        Span span = tracer.spanBuilder("FractalCalculator.calculateTile")
                .setParent(currentContext)
                .setAttribute("service.name", "otelbrot-worker")
                .setAttribute("jobId", spec.getJobId())
                .setAttribute("tileId", spec.getTileId())
                .setAttribute("width", spec.getWidth())
                .setAttribute("height", spec.getHeight())
                .setAttribute("maxIterations", spec.getMaxIterations())
                .startSpan();
                
        // Log the new span's trace ID
        logger.info("Created calculateTile span: traceId={}, spanId={}", 
            span.getSpanContext().getTraceId(),
            span.getSpanContext().getSpanId());
        
        long startTime = System.currentTimeMillis();
        
        try (io.opentelemetry.context.Scope scope = span.makeCurrent()) {
            logger.info("Calculating tile for job: {}, tile: {}", spec.getJobId(), spec.getTileId());
            
            // Create the image to hold the result
            BufferedImage image = new BufferedImage(spec.getWidth(), spec.getHeight(), BufferedImage.TYPE_INT_RGB);
            
            // Calculate the pixel size in fractal coordinates
            double pixelWidth = (spec.getXMax() - spec.getXMin()) / spec.getWidth();
            double pixelHeight = (spec.getYMax() - spec.getYMin()) / spec.getHeight();
            
            // For each pixel in the tile
            for (int y = 0; y < spec.getHeight(); y++) {
                for (int x = 0; x < spec.getWidth(); x++) {
                    // Convert pixel coordinates to fractal coordinates
                    double cx = spec.getXMin() + x * pixelWidth;
                    double cy = spec.getYMin() + y * pixelHeight;
                    
                    // Calculate the number of iterations
                    int iterations = calculateMandelbrot(cx, cy, spec.getMaxIterations());
                    
                    // Map the iteration count to a color
                    Color color = colorMapper.applyColorMap(iterations, spec.getMaxIterations(), spec.getColorScheme());
                    
                    // Set the pixel color
                    image.setRGB(x, y, color.getRGB());
                }
            }
            
            // Convert the image to a byte array
            byte[] imageData = imageToByteArray(image);
            
            long calculationTime = System.currentTimeMillis() - startTime;
            logger.info("Calculated tile in {} ms", calculationTime);
            
            // Record metrics
            span.setAttribute("calculationTimeMs", calculationTime);
            
            return TileResult.fromCalculation(spec, imageData, calculationTime);
        } catch (Exception e) {
            logger.error("Error calculating tile", e);
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return TileResult.failedResult(spec, "Error calculating tile: " + e.getMessage());
        } finally {
            span.end();
        }
    }

    /**
     * Calculate the Mandelbrot set iteration count for a point
     */
    private int calculateMandelbrot(double cx, double cy, int maxIterations) {
        double x = 0;
        double y = 0;
        int iteration = 0;
        
        // Cardioid and period-2 bulb check (optimization)
        double q = (cx - 0.25) * (cx - 0.25) + cy * cy;
        if (q * (q + (cx - 0.25)) < 0.25 * cy * cy) {
            return maxIterations;
        }
        
        // Period doubling bulb
        if ((cx + 1) * (cx + 1) + cy * cy < 0.0625) {
            return maxIterations;
        }
        
        while (x * x + y * y < MAX_ESCAPE && iteration < maxIterations) {
            double xtemp = x * x - y * y + cx;
            y = 2 * x * y + cy;
            x = xtemp;
            iteration++;
        }
        
        return iteration;
    }

    /**
     * Convert a BufferedImage to a byte array
     */
    private byte[] imageToByteArray(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }
}