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
        // Register a JVM shutdown hook to ensure proper cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered - waiting for telemetry export...");
            try {
                // Wait a bit to allow any pending telemetry to be exported
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                logger.warn("Shutdown hook sleep interrupted");
            }
            logger.info("Shutdown hook completed");
        }));
        
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

    /**
     * Extract OpenTelemetry context from environment variables
     * Simplified version that uses standard W3C propagator with traceparent environment variable
     */
    private Context extractContextFromEnvironment(TextMapPropagator propagator) {
        // Create a carrier from environment variables
        Map<String, String> carrier = new HashMap<>();
        
        // Look for standard W3C traceparent in the environment variable
        String traceparent = System.getenv("TRACEPARENT");
        if (traceparent != null && !traceparent.isEmpty()) {
            logger.info("Found W3C traceparent: {}", traceparent);
            carrier.put("traceparent", traceparent);
            
            // Also look for tracestate if present
            String tracestate = System.getenv("TRACESTATE");
            if (tracestate != null && !tracestate.isEmpty()) {
                carrier.put("tracestate", tracestate);
                logger.info("Found W3C tracestate: {}", tracestate);
            }
        } else {
            logger.warn("No traceparent found in environment variables");
            return Context.current();
        }
        
        // Extract context using the W3C propagator
        TextMapGetter<Map<String, String>> getter = new TextMapGetter<Map<String, String>>() {
            @Override
            public Iterable<String> keys(Map<String, String> carrier) {
                return carrier.keySet();
            }
            
            @Override
            public String get(Map<String, String> carrier, String key) {
                return carrier.get(key);
            }
        };
        
        Context extractedContext = propagator.extract(Context.root(), carrier, getter);
        logger.info("Context extraction completed from environment variables");
        
        return extractedContext;
    }

    @Bean
    public CommandLineRunner commandLineRunner(
            FractalCalculator calculator,
            ResultSender sender,
            Tracer tracer,
            TextMapPropagator propagator,
            @org.springframework.beans.factory.annotation.Autowired(required = false) TileSpec tileSpec) {
        return args -> {
            // Debug: Log all environment variables to understand what's coming in
            logger.info("Environment variables for trace context debugging:");
            System.getenv().forEach((k, v) -> {
                if (k.contains("TRACE") || k.contains("OTEL") || k.contains("TILE_SPEC_TRACE")) {
                    logger.info("ENV {}={}", k, v);
                }
            });
            
            // Create parent context by extracting from environment variables
            Context parentContext = extractContextFromEnvironment(propagator);
            
            if (tileSpec != null) {
                // Log trace context details for debugging
                logger.info("Using trace context from environment variables");
                
                String traceparent = System.getenv("TRACEPARENT");
                if (traceparent != null) {
                    logger.info("Found traceparent in environment: {}", traceparent);
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
                    // Mark span as failed but don't exit immediately
                    rootSpan.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, "Failed to send tile result");
                    // Let the application exit through finally block without System.exit
                }
            } catch (Exception e) {
                logger.error("Error processing tile", e);
                rootSpan.recordException(e);
                rootSpan.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
                // Let the application exit through finally block without System.exit
            } finally {
                // End the span
                rootSpan.end();
                logger.info("Root span ended");
                
                // Wait for spans to be exported (longer than batch timeout)
                logger.info("Waiting for telemetry to be exported...");
                try {
                    // Wait longer than the batch processor timeout (configured for 10s)
                    Thread.sleep(15000);
                    logger.info("Telemetry export wait completed");
                } catch (InterruptedException ie) {
                    logger.warn("Wait for telemetry export was interrupted");
                }
                
                // Only for testing we return normally, in production we'll exit with success/failure
                if (!"test".equals(System.getProperty("spring.profiles.active"))) {
                    // Add a small delay before exit to ensure logging is flushed
                    try { Thread.sleep(100); } catch (InterruptedException ie) {}
                }
            }
        };
    }
}
