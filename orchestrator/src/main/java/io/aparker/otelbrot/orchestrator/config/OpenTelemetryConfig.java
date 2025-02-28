package io.aparker.otelbrot.orchestrator.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry configuration
 * Note: In a Kubernetes environment, the actual instrumentation
 * will be provided by an OpenTelemetry agent running as a sidecar
 */
@Configuration
public class OpenTelemetryConfig {

    @Value("${spring.application.name}")
    private String serviceName;

    @Bean
    public TextMapPropagator w3cPropagator() {
        return W3CTraceContextPropagator.getInstance();
    }
    
    @Bean
    public ContextPropagators contextPropagators(TextMapPropagator propagator) {
        return ContextPropagators.create(propagator);
    }
    
    @Bean
    public OpenTelemetry openTelemetry(ContextPropagators propagators) {
        // Use the GlobalOpenTelemetry instance which will be configured
        // by the Java agent in production environments
        return io.opentelemetry.api.GlobalOpenTelemetry.get();
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(serviceName);
    }
}