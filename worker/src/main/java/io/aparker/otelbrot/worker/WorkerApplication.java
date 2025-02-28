package io.aparker.otelbrot.worker;

import io.aparker.otelbrot.commons.model.TileResult;
import io.aparker.otelbrot.commons.model.TileSpec;
import io.aparker.otelbrot.worker.service.FractalCalculator;
import io.aparker.otelbrot.worker.service.ResultSender;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Worker application that calculates a single fractal tile and then exits
 */
@SpringBootApplication
public class WorkerApplication {
    private static final Logger logger = LoggerFactory.getLogger(WorkerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    
    @Bean
    public TileSpec tileSpec() {
        // In test profile, we'll provide a mock TileSpec bean
        if ("test".equals(System.getProperty("spring.profiles.active"))) {
            logger.info("Test profile active, returning null TileSpec from bean method");
            return null;
        }
        
        try {
            return TileSpec.fromEnvironment();
        } catch (Exception e) {
            logger.error("Failed to create TileSpec from environment variables", e);
            // In development/testing environments, provide a default TileSpec
            if (System.getenv("ENVIRONMENT") == null || "dev".equals(System.getenv("ENVIRONMENT"))) {
                logger.info("Creating default TileSpec for development/testing");
                return new TileSpec.Builder()
                        .jobId("dev-job")
                        .tileId("dev-tile")
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
            return null;
        }
    }

    @Bean
    public CommandLineRunner commandLineRunner(
            FractalCalculator calculator,
            ResultSender sender,
            Tracer tracer,
            TextMapPropagator propagator,
            @org.springframework.beans.factory.annotation.Autowired(required = false) TileSpec tileSpec) {
        return args -> {
            // Create parent context from trace context if available
            Context parentContext = Context.current();
            
            if (tileSpec != null) {
                // Log all environment variables to understand what's coming in
                logger.info("All environment variables (for trace context debugging):");
                System.getenv().forEach((k, v) -> {
                    if (k.contains("TRACE") || k.contains("OTEL") || k.contains("TILE_SPEC_TRACE")) {
                        logger.info("ENV {}={}", k, v);
                    }
                });
                
                // Get raw trace parent from environment variables
                String otelTraceParent = System.getenv("OTEL_TRACE_PARENT");
                String tileSpecTraceParent = System.getenv("TILE_SPEC_TRACE_PARENT");
                String traceParentFromTileSpec = tileSpec.getTraceParent();
                
                logger.info("ENVIRONMENT: OTEL_TRACE_PARENT={}", otelTraceParent);
                logger.info("ENVIRONMENT: TILE_SPEC_TRACE_PARENT={}", tileSpecTraceParent);
                logger.info("TileSpec.getTraceParent()={}", traceParentFromTileSpec);
                
                if (traceParentFromTileSpec != null) {
                    try {
                        logger.info("Trace parsing: Raw trace parent: {}", traceParentFromTileSpec);
                        
                        // Parse the traceparent manually to understand its structure
                        if (traceParentFromTileSpec.contains("-")) {
                            String[] parts = traceParentFromTileSpec.split("-");
                            if (parts.length >= 4) {
                                String version = parts[0];
                                String traceId = parts[1];
                                String spanId = parts[2];
                                String flags = parts[3];
                                
                                logger.info("Trace format analysis:");
                                logger.info("  - Version:     {}", version);
                                logger.info("  - Trace ID:    {}", traceId);
                                logger.info("  - Parent ID:   {}", spanId);
                                logger.info("  - Flags:       {}", flags);
                                logger.info("  - Full trace:  {}", traceParentFromTileSpec);
                                
                                // Create carrier with trace context from environment variables
                                Map<String, String> carrier = new HashMap<>();
                                carrier.put("traceparent", traceParentFromTileSpec);
                                
                                if (tileSpec.getTraceState() != null) {
                                    logger.info("Trace state from tileSpec: {}", tileSpec.getTraceState());
                                    carrier.put("tracestate", tileSpec.getTraceState());
                                }
                                
                                // Log what's in our carrier
                                logger.info("Carrier contents: {}", carrier);
                                
                                // Extract the context using a TextMapGetter with detailed logging
                                TextMapGetter<Map<String, String>> getter = new TextMapGetter<Map<String, String>>() {
                                    @Override
                                    public Iterable<String> keys(Map<String, String> carrier) {
                                        logger.info("TextMapGetter keys() called, returning: {}", carrier.keySet());
                                        return carrier.keySet();
                                    }
                                    
                                    @Override
                                    public String get(Map<String, String> carrier, String key) {
                                        String value = carrier.get(key);
                                        logger.info("TextMapGetter get() called with key: '{}', returning value: '{}'", key, value);
                                        return value;
                                    }
                                };
                                
                                // Start with root context to avoid any existing context contamination
                                logger.info("Extracting context with propagator: {}", propagator.getClass().getName());
                                parentContext = propagator.extract(Context.root(), carrier, getter);
                                
                                logger.info("Context extraction successful. Using parent span ID: {}", spanId);
                            } else {
                                logger.warn("Trace parent doesn't have expected format. Parts: {}", parts.length);
                            }
                        } else {
                            logger.warn("Trace parent doesn't contain expected delimiter '-': {}", traceParentFromTileSpec);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to extract trace context", e);
                        // Fall back to the current context if extraction fails
                        parentContext = Context.current(); 
                    }
                } else {
                    logger.warn("No trace parent available in TileSpec");
                }
            }
            
            // Log what we're about to do for debugging
            logger.info("About to create root span with parentContext: {}", 
                parentContext != Context.root() ? "custom context" : "root context");
            
            // Create span with parent context if available
            Span rootSpan = tracer.spanBuilder("WorkerApplication.process")
                    .setParent(parentContext)
                    .setAttribute("service.name", "otelbrot-worker")
                    .startSpan();
                    
            logger.info("Created root span: traceId={}, spanId={}", 
                rootSpan.getSpanContext().getTraceId(),
                rootSpan.getSpanContext().getSpanId());
            
            try (Scope scope = rootSpan.makeCurrent()) {
                // TileSpec should come from the Bean method, which tries environment variables
                if (tileSpec == null) {
                    throw new IllegalStateException("TileSpec could not be created. Environment variables may be missing.");
                }
                logger.info("Processing tile: job={}, tile={}", tileSpec.getJobId(), tileSpec.getTileId());
                rootSpan.setAttribute("jobId", tileSpec.getJobId());
                rootSpan.setAttribute("tileId", tileSpec.getTileId());
                
                // Calculate the fractal tile
                TileResult result = calculator.calculateTile(tileSpec);
                
                // Send the result back to the orchestrator
                boolean sent = sender.sendResult(result);
                
                if (sent) {
                    logger.info("Successfully completed tile processing");
                } else {
                    logger.error("Failed to send tile result");
                    if (!"test".equals(System.getProperty("spring.profiles.active"))) {
                        System.exit(1);
                    }
                }
            } catch (Exception e) {
                logger.error("Error processing tile", e);
                rootSpan.recordException(e);
                if (!"test".equals(System.getProperty("spring.profiles.active"))) {
                    System.exit(1);
                }
            } finally {
                rootSpan.end();
                // Only exit in production mode, not during tests
                if (!"test".equals(System.getProperty("spring.profiles.active"))) {
                    System.exit(0);
                }
            }
        };
    }
}
