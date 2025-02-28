package io.aparker.otelbrot.worker.service;

import io.aparker.otelbrot.commons.model.TileResult;
import io.aparker.otelbrot.commons.model.TileSpec;
import io.aparker.otelbrot.commons.model.TileStatus;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FractalCalculatorTest {

    @Mock
    private ColorMapper colorMapper;

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    @InjectMocks
    private FractalCalculator fractalCalculator;

    @BeforeEach
    void setUp() {
        // Mock the tracer behavior
        SpanBuilder mockSpanBuilder = mock(SpanBuilder.class);
        when(tracer.spanBuilder(anyString())).thenReturn(mockSpanBuilder);
        
        // Mock the SpanBuilder's setAttribute methods to return itself
        when(mockSpanBuilder.setAttribute(anyString(), anyString())).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setAttribute(anyString(), anyLong())).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setAttribute(anyString(), anyDouble())).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setAttribute(anyString(), anyBoolean())).thenReturn(mockSpanBuilder);
        
        when(mockSpanBuilder.startSpan()).thenReturn(span);
        
        // Need to mock the Span setAttribute methods to avoid NullPointerException and for verification
        when(span.setAttribute(anyString(), anyString())).thenReturn(span);
        when(span.setAttribute(anyString(), anyLong())).thenReturn(span);
        when(span.setAttribute(anyString(), anyDouble())).thenReturn(span);
        when(span.setAttribute(anyString(), anyBoolean())).thenReturn(span);
        
        // Set up the color mapper to return a simple color for testing
        when(colorMapper.applyColorMap(anyInt(), anyInt(), anyString()))
                .thenReturn(Color.BLACK);
    }

    @Test
    void testCalculateTile() {
        // Setup
        TileSpec spec = createTestTileSpec();
        
        // Execute
        TileResult result = fractalCalculator.calculateTile(spec);
        
        // Verify
        assertNotNull(result);
        assertEquals("test-job-id", result.getJobId());
        assertEquals("test-tile-id", result.getTileId());
        assertEquals(10, result.getWidth());
        assertEquals(10, result.getHeight());
        assertEquals(0, result.getPixelStartX());
        assertEquals(0, result.getPixelStartY());
        assertEquals(TileStatus.COMPLETED, result.getStatus());
        assertTrue(result.getCalculationTimeMs() > 0);
        assertNotNull(result.getImageData());
        assertTrue(result.getImageData().length > 0);
        
        // Verify OpenTelemetry instrumentation
        verify(span).setAttribute("calculationTimeMs", result.getCalculationTimeMs());
        verify(span).end();
    }
    
    @Test
    void testCalculateTile_WithError() {
        // Setup
        TileSpec spec = createTestTileSpec();
        
        // Mock ColorMapper to throw an exception
        when(colorMapper.applyColorMap(anyInt(), anyInt(), anyString()))
                .thenThrow(new RuntimeException("Test exception"));
        
        // Execute
        TileResult result = fractalCalculator.calculateTile(spec);
        
        // Verify
        assertNotNull(result);
        assertEquals("test-job-id", result.getJobId());
        assertEquals("test-tile-id", result.getTileId());
        assertEquals(TileStatus.FAILED, result.getStatus());
        
        // Verify error handling in OpenTelemetry
        verify(span).recordException(any(Exception.class));
        verify(span).setStatus(any(), anyString());
        verify(span).end();
    }

    private TileSpec createTestTileSpec() {
        return new TileSpec.Builder()
                .jobId("test-job-id")
                .tileId("test-tile-id")
                .xMin(-2.0)
                .yMin(-1.5)
                .xMax(-1.5)
                .yMax(-1.0)
                .width(10) // Small size for fast test
                .height(10)
                .maxIterations(100)
                .colorScheme("classic")
                .pixelStartX(0)
                .pixelStartY(0)
                .build();
    }
}