package io.aparker.otelbrot.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS configuration for the application
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        
        // Allow specific origins with credentials for development
        // For production, these should be restricted appropriately
        // When allowCredentials is true, we can't use wildcard origins
        config.addAllowedOriginPattern("*"); // Use pattern instead of exact origin
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        
        // Allow cookies if needed
        config.setAllowCredentials(true);
        
        // Set max age for preflight requests
        config.setMaxAge(3600L);
        
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}