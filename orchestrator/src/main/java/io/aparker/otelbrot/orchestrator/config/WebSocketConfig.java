package io.aparker.otelbrot.orchestrator.config;

import io.aparker.otelbrot.orchestrator.websocket.FractalWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WebSocket configuration
 */
@Configuration
@EnableWebSocket
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "websocket.enabled", matchIfMissing = true)
public class WebSocketConfig implements WebSocketConfigurer {

    private final FractalWebSocketHandler fractalWebSocketHandler;

    public WebSocketConfig(FractalWebSocketHandler fractalWebSocketHandler) {
        this.fractalWebSocketHandler = fractalWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(fractalWebSocketHandler, "/ws/fractal")
                .setAllowedOriginPatterns("*") // Use pattern instead of exact origin
                .withSockJS(); // Add SockJS fallback
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // Set websocket settings
        container.setMaxTextMessageBufferSize(16384);
        container.setMaxBinaryMessageBufferSize(16384);
        // Increase timeouts for better stability
        container.setAsyncSendTimeout(30000L);
        container.setMaxSessionIdleTimeout(120000L);
        return container;
    }
}