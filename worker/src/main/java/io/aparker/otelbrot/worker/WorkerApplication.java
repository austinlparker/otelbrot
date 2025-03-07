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
        
        // Use Context.current() instead of Context.root() as the starting point
        Context extractedContext = propagator.extract(Context.current(), carrier, getter);
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
            logger.info("Parent context: {}", parentContext);
            // Create span with parent context if available
            logger.info("Tracer={}", tracer);
            Span rootSpan = tracer.spanBuilder("WorkerApplication.process")
                    .setParent(parentContext)
                    .startSpan();
                    
            logger.info("Created root span: traceId={}, spanId={}, isRemote={}", 
                rootSpan.getSpanContext().getTraceId(),
                rootSpan.getSpanContext().getSpanId(),
                rootSpan.getSpanContext().isRemote());
            
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
                    rootSpan.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, "Failed to send tile result");
                }
            } catch (Exception e) {
                logger.error("Error processing tile", e);
                rootSpan.recordException(e);
                rootSpan.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, e.getMessage());
            } finally {
                rootSpan.end();
                logger.info("Root span ended");
            }
        };
    }
}
