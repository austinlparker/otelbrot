package io.aparker.otelbrot.orchestrator.controller;

import io.aparker.otelbrot.commons.model.TileResult;
import io.aparker.otelbrot.orchestrator.model.FractalJob;
import io.aparker.otelbrot.orchestrator.model.RenderRequest;
import io.aparker.otelbrot.orchestrator.service.OrchestrationService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * REST controller for fractal rendering operations
 */
@RestController
@RequestMapping("/api/fractal")
public class FractalController {
    private static final Logger logger = LoggerFactory.getLogger(FractalController.class);
    
    private final OrchestrationService orchestrationService;
    private final Tracer tracer;
    private final TextMapPropagator propagator;
    
    // Getter for extracting context from HTTP headers
    private static final TextMapGetter<HttpHeaders> GETTER = 
        new TextMapGetter<>() {
            @Override
            public Iterable<String> keys(HttpHeaders carrier) {
                return carrier.keySet();
            }
            
            @Override
            public String get(HttpHeaders carrier, String key) {
                if (carrier.containsKey(key)) {
                    return carrier.getFirst(key);
                }
                return null;
            }
        };

    public FractalController(OrchestrationService orchestrationService, Tracer tracer, TextMapPropagator propagator) {
        this.orchestrationService = orchestrationService;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    /**
     * Initiate a fractal rendering job
     */
    @PostMapping("/render")
    public ResponseEntity<Map<String, String>> initiateRender(@Valid @RequestBody RenderRequest request) {
        Span span = tracer.spanBuilder("FractalController.initiateRender")
                .setParent(Context.current())
                .setAttribute("service.name", "otelbrot-orchestrator")
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            logger.info("Received render request: centerX={}, centerY={}, zoom={}", 
                request.getCenterX(), request.getCenterY(), request.getZoom());
            
            span.setAttribute("request.centerX", request.getCenterX());
            span.setAttribute("request.centerY", request.getCenterY());
            span.setAttribute("request.zoom", request.getZoom());
            
            FractalJob job = orchestrationService.createRenderJob(request);
            span.setAttribute("job.id", job.getJobId());
            
            return ResponseEntity.ok(Map.of(
                "jobId", job.getJobId(),
                "status", job.getStatus().name()
            ));
        } finally {
            span.end();
        }
    }

    /**
     * Receive a tile result from a worker
     */
    @PostMapping("/tile-result")
    public ResponseEntity<Map<String, String>> receiveTileResult(
            @RequestBody TileResult result,
            @RequestHeader HttpHeaders headers) {
            
        // Extract context from headers if present
        Context extractedContext = propagator.extract(Context.current(), headers, GETTER);
        
        // Create span as child of extracted context
        Span span = tracer.spanBuilder("FractalController.receiveTileResult")
                .setParent(extractedContext)
                .startSpan();
                
        try (Scope scope = span.makeCurrent()) {
            logger.info("Received tile result for job: {}, tile: {}", 
                result.getJobId(), result.getTileId());
            
            // Process with propagated context
            orchestrationService.processTileResult(result);
            
            return ResponseEntity.accepted().body(Map.of(
                "status", "accepted",
                "message", "Tile result processed successfully"
            ));
        } finally {
            span.end();
        }
    }

    /**
     * Get the status of a job
     */
    @GetMapping("/job/{jobId}")
    public ResponseEntity<FractalJob> getStatus(@PathVariable String jobId) {
        Span span = tracer.spanBuilder("FractalController.getStatus").startSpan();
        try {
            logger.info("Getting status for job: {}", jobId);
            
            return orchestrationService.getJobStatus(jobId)
                    .map(ResponseEntity::ok)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Job not found: " + jobId));
        } finally {
            span.end();
        }
    }

    /**
     * Cancel a job
     */
    @PostMapping("/job/{jobId}/cancel")
    public ResponseEntity<Map<String, String>> cancelJob(@PathVariable String jobId) {
        Span span = tracer.spanBuilder("FractalController.cancelJob").startSpan();
        try {
            logger.info("Cancelling job: {}", jobId);
            
            boolean cancelled = orchestrationService.cancelJob(jobId);
            
            if (cancelled) {
                return ResponseEntity.ok(Map.of(
                    "status", "cancelled",
                    "message", "Job cancelled successfully"
                ));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", "Failed to cancel job"
                ));
            }
        } finally {
            span.end();
        }
    }
}