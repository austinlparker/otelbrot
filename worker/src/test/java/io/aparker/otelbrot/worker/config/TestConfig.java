package io.aparker.otelbrot.worker.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Test configuration for the worker application.
 * Provides mock beans for testing.
 */
@TestConfiguration
@Profile("test")
public class TestConfig {

    /**
     * Provides a mock OpenTelemetry for testing.
     */
    @Bean
    @Primary
    public OpenTelemetry openTelemetry() {
        return OpenTelemetry.noop();
    }

    /**
     * Provides a mock Tracer for testing.
     */
    @Bean
    @Primary
    public Tracer tracer() {
        Tracer mockTracer = Mockito.mock(Tracer.class);
        SpanBuilder mockSpanBuilder = Mockito.mock(SpanBuilder.class);
        Span mockSpan = Mockito.mock(Span.class);
        
        // Important: Return the builder itself for chained calls
        when(mockTracer.spanBuilder(anyString())).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setAttribute(anyString(), anyString())).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setAttribute(anyString(), Mockito.anyInt())).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setAttribute(anyString(), Mockito.anyLong())).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.startSpan()).thenReturn(mockSpan);
        
        return mockTracer;
    }
    
    /**
     * Provides a bean for TileSpec to be used in tests.
     * @return a test TileSpec
     */
    @Bean
    @Primary
    public io.aparker.otelbrot.commons.model.TileSpec testTileSpec() {
        return new io.aparker.otelbrot.commons.model.TileSpec.Builder()
                .jobId("test-job-id")
                .tileId("test-tile-id")
                .xMin(-2.0)
                .yMin(-1.5)
                .xMax(1.0)
                .yMax(1.5)
                .width(800)
                .height(600)
                .maxIterations(100)
                .colorScheme("classic")
                .pixelStartX(0)
                .pixelStartY(0)
                .build();
    }
}