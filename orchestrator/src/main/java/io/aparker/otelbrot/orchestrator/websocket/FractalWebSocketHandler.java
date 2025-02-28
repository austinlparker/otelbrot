package io.aparker.otelbrot.orchestrator.websocket;

import io.aparker.otelbrot.orchestrator.service.WebSocketService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Handler for WebSocket connections
 */
@Component
public class FractalWebSocketHandler extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(FractalWebSocketHandler.class);
    
    private final WebSocketService webSocketService;
    private final Tracer tracer;

    public FractalWebSocketHandler(WebSocketService webSocketService, Tracer tracer) {
        this.webSocketService = webSocketService;
        this.tracer = tracer;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Span span = tracer.spanBuilder("websocket.connection.established")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("websocket.session_id", session.getId())
                .setAttribute("messaging.system", "websocket")
                .setAttribute("messaging.operation", "connect")
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            logger.info("WebSocket connection established: {}", session.getId());
            webSocketService.registerSession(session.getId(), session);
        } finally {
            span.end();
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Span span = tracer.spanBuilder("websocket.message.received")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("websocket.session_id", session.getId())
                .setAttribute("messaging.system", "websocket")
                .setAttribute("messaging.operation", "receive")
                .setAttribute("messaging.message_payload_size_bytes", message.getPayloadLength())
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            logger.debug("Received message from: {}", session.getId());
            // Extract any trace context from the message if present
            // For now, pass the current context to the service
            webSocketService.handleMessage(session, message.getPayload());
        } finally {
            span.end();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Span span = tracer.spanBuilder("websocket.connection.closed")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("websocket.session_id", session.getId())
                .setAttribute("websocket.close_status", status.getCode())
                .setAttribute("websocket.close_reason", status.getReason() != null ? status.getReason() : "")
                .setAttribute("messaging.system", "websocket")
                .setAttribute("messaging.operation", "disconnect")
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            logger.info("WebSocket connection closed: {}, status: {}", session.getId(), status);
            webSocketService.removeSession(session.getId());
        } finally {
            span.end();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Span span = tracer.spanBuilder("websocket.transport.error")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("websocket.session_id", session.getId())
                .setAttribute("messaging.system", "websocket")
                .setAttribute("messaging.operation", "error")
                .setAttribute("error", true)
                .setAttribute("error.type", exception.getClass().getName())
                .setAttribute("error.message", exception.getMessage() != null ? exception.getMessage() : "")
                .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            logger.error("WebSocket transport error for session: {}", session.getId(), exception);
            span.recordException(exception);
            
            try {
                session.close(CloseStatus.SERVER_ERROR);
            } catch (Exception e) {
                logger.error("Error closing WebSocket session after transport error", e);
                span.recordException(e);
            }
            
            webSocketService.removeSession(session.getId());
        } finally {
            span.end();
        }
    }
}