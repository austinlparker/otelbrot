package io.aparker.otelbrot.worker.service;

import io.aparker.otelbrot.commons.model.TileResult;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Service for sending tile results back to the orchestrator
 */
@Service
public class ResultSender {
    private static final Logger logger = LoggerFactory.getLogger(ResultSender.class);
    
    private final RestTemplate restTemplate;
    private final Tracer tracer;
    private final TextMapPropagator propagator;
    
    @Value("${app.orchestrator.url}")
    private String orchestratorUrl;
    
    // TextMapSetter for HTTP headers
    private static final TextMapSetter<HttpHeaders> SETTER = 
        (carrier, key, value) -> carrier.set(key, value);

    public ResultSender(RestTemplate restTemplate, Tracer tracer, TextMapPropagator propagator) {
        this.restTemplate = restTemplate;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    /**
     * Send a tile result to the orchestrator
     */
    public boolean sendResult(TileResult result) {
        Span span = tracer.spanBuilder("ResultSender.sendResult")
                .setParent(Context.current())
                .setAttribute("service.name", "otelbrot-worker")
                .setAttribute("jobId", result.getJobId())
                .setAttribute("tileId", result.getTileId())
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            String endpoint = orchestratorUrl + "/api/fractal/tile-result";
            logger.info("Sending tile result to {}: job {}, tile {}", 
                endpoint, result.getJobId(), result.getTileId());
                
            // Create headers and inject trace context
            HttpHeaders headers = new HttpHeaders();
            propagator.inject(Context.current(), headers, SETTER);
            
            // Create entity with headers and result body
            HttpEntity<TileResult> entity = new HttpEntity<>(result, headers);
            
            // Use exchange instead of postForEntity to include headers
            ResponseEntity<Object> response = restTemplate.exchange(
                endpoint, 
                HttpMethod.POST, 
                entity, 
                Object.class
            );
            
            if (response.getStatusCode() == HttpStatus.ACCEPTED) {
                logger.info("Successfully sent tile result");
                return true;
            } else {
                logger.error("Failed to send tile result: {}", response.getStatusCode());
                span.setStatus(StatusCode.ERROR, "HTTP status: " + response.getStatusCode());
                return false;
            }
        } catch (Exception e) {
            logger.error("Error sending tile result", e);
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            return false;
        } finally {
            span.end();
        }
    }
}