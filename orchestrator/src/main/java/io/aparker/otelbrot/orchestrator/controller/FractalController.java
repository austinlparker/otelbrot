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
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
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
    @WithSpan("FractalController.initiateRender")
    @PostMapping("/render")
    public ResponseEntity<Map<String, String>> initiateRender(
            @Valid @RequestBody 
            @SpanAttribute("request") RenderRequest request) {
        Span.current().setAttribute("service.name", "otelbrot-orchestrator");
        
        logger.info("Received render request: centerX={}, centerY={}, zoom={}", 
            request.getCenterX(), request.getCenterY(), request.getZoom());
        
        Span.current().setAttribute("request.centerX", request.getCenterX());
        Span.current().setAttribute("request.centerY", request.getCenterY());
        Span.current().setAttribute("request.zoom", request.getZoom());
        
        FractalJob job = orchestrationService.createRenderJob(request);
        Span.current().setAttribute("job.id", job.getJobId());
        
        return ResponseEntity.ok(Map.of(
            "jobId", job.getJobId(),
            "status", job.getStatus().name()
        ));
    }

    /**
     * Receive a tile result from a worker
     */
    @WithSpan("FractalController.receiveTileResult")
    @PostMapping("/tile-result")
    public ResponseEntity<Map<String, String>> receiveTileResult(
            @RequestBody TileResult result,
            @RequestHeader HttpHeaders headers) {
            
        // Extract context from headers if present
        Context extractedContext = propagator.extract(Context.current(), headers, GETTER);
        
        // Switch to the extracted context to maintain trace continuity
        try (Scope scope = extractedContext.makeCurrent()) {
            logger.info("Received tile result for job: {}, tile: {}", 
                result.getJobId(), result.getTileId());
            
            // Set attributes on the current span
            Span.current().setAttribute("job.id", result.getJobId());
            Span.current().setAttribute("tile.id", result.getTileId());
            
            // Process with propagated context
            orchestrationService.processTileResult(result.getJobId(), result.getTileId(), result);
            
            return ResponseEntity.accepted().body(Map.of(
                "status", "accepted",
                "message", "Tile result processed successfully"
            ));
        }
    }

    /**
     * Get the status of a job
     */
    @WithSpan("FractalController.getStatus")
    @GetMapping("/job/{jobId}")
    public ResponseEntity<FractalJob> getStatus(@PathVariable @SpanAttribute("job.id") String jobId) {
        logger.info("Getting status for job: {}", jobId);
        
        return orchestrationService.getJobStatus(jobId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Job not found: " + jobId));
    }

    /**
     * Cancel a job
     */
    @WithSpan("FractalController.cancelJob")
    @PostMapping("/job/{jobId}/cancel")
    public ResponseEntity<Map<String, String>> cancelJob(@PathVariable @SpanAttribute("job.id") String jobId) {
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
    }
}