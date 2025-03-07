package io.aparker.otelbrot.worker.service;

import io.aparker.otelbrot.commons.model.TileResult;
import io.aparker.otelbrot.commons.model.TileStatus;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResultSenderTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;
    
    @Mock
    private SpanContext spanContext;
    
    @Mock
    private TextMapPropagator propagator;

    @InjectMocks
    private ResultSender resultSender;

    @BeforeEach
    void setUp() {
        // Mock the tracer behavior
        SpanBuilder mockSpanBuilder = mock(SpanBuilder.class);
        when(tracer.spanBuilder(anyString())).thenReturn(mockSpanBuilder);
        
        // Mock the SpanBuilder's setParent method to return itself
        when(mockSpanBuilder.setParent(any(Context.class))).thenReturn(mockSpanBuilder);
        
        // Mock the SpanBuilder's setAttribute methods to return itself
        when(mockSpanBuilder.setAttribute(anyString(), anyString())).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setAttribute(anyString(), anyLong())).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setAttribute(anyString(), anyDouble())).thenReturn(mockSpanBuilder);
        when(mockSpanBuilder.setAttribute(anyString(), anyBoolean())).thenReturn(mockSpanBuilder);
        
        when(mockSpanBuilder.startSpan()).thenReturn(span);
        
        // Mock span methods to avoid NullPointerException
        when(span.setAttribute(anyString(), anyString())).thenReturn(span);
        when(span.setAttribute(anyString(), anyLong())).thenReturn(span);
        when(span.setAttribute(anyString(), anyDouble())).thenReturn(span);
        when(span.setAttribute(anyString(), anyBoolean())).thenReturn(span);
        when(span.setStatus(any(StatusCode.class), anyString())).thenReturn(span);
        
        // Mock SpanContext for span
        when(span.getSpanContext()).thenReturn(spanContext);
        when(spanContext.getTraceId()).thenReturn("fake-trace-id");
        when(spanContext.getSpanId()).thenReturn("fake-span-id");
        
        // Set the orchestrator URL for testing
        ReflectionTestUtils.setField(resultSender, "orchestratorUrl", "http://localhost:8080");
    }

    @Test
    void testSendResult_Success() {
        // Setup
        TileResult result = createTestTileResult();
        
        ResponseEntity<Object> responseEntity = new ResponseEntity<>(HttpStatus.ACCEPTED);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class))).thenReturn(responseEntity);
        
        // Execute
        boolean success = resultSender.sendResult(result);
        
        // Verify
        assertTrue(success);
        
        verify(restTemplate).exchange(eq("http://localhost:8080/api/fractal/tile-result"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class));
        verify(span).end();
    }

    @Test
    void testSendResult_NonAcceptedResponse() {
        // Setup
        TileResult result = createTestTileResult();
        
        ResponseEntity<Object> responseEntity = new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class))).thenReturn(responseEntity);
        
        // Execute
        boolean success = resultSender.sendResult(result);
        
        // Verify
        assertFalse(success);
        
        verify(restTemplate).exchange(eq("http://localhost:8080/api/fractal/tile-result"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class));
        verify(span).setStatus(eq(StatusCode.ERROR), anyString());
        verify(span).end();
    }

    @Test
    void testSendResult_Exception() {
        // Setup
        TileResult result = createTestTileResult();
        
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class)))
                .thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));
        
        // Execute
        boolean success = resultSender.sendResult(result);
        
        // Verify
        assertFalse(success);
        
        verify(restTemplate).exchange(eq("http://localhost:8080/api/fractal/tile-result"), eq(HttpMethod.POST), any(HttpEntity.class), eq(Object.class));
        verify(span).recordException(any(Exception.class));
        verify(span).setStatus(eq(StatusCode.ERROR), anyString());
        verify(span).end();
    }

    private TileResult createTestTileResult() {
        return new TileResult.Builder()
                .jobId("test-job-id")
                .tileId("test-tile-id")
                .width(256)
                .height(256)
                .pixelStartX(0)
                .pixelStartY(0)
                .imageData(new byte[1024])
                .calculationTimeMs(100)
                .status(TileStatus.COMPLETED)
                .build();
    }
}