package io.aparker.otelbrot.orchestrator.websocket;

import io.aparker.otelbrot.orchestrator.service.WebSocketService;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
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

    @WithSpan(value = "websocket.connection.established", kind = SpanKind.SERVER)
    @Override
    public void afterConnectionEstablished(
            @SpanAttribute("websocket.session_id") WebSocketSession session) {
        Span.current().setAttribute("messaging.system", "websocket");
        Span.current().setAttribute("messaging.operation", "connect");
        
        logger.info("WebSocket connection established: {}", session.getId());
        webSocketService.registerSession(session.getId(), session);
    }

    @WithSpan(value = "websocket.message.received", kind = SpanKind.SERVER)
    @Override
    protected void handleTextMessage(
            @SpanAttribute("websocket.session_id") WebSocketSession session, 
            TextMessage message) {
        Span.current().setAttribute("messaging.system", "websocket");
        Span.current().setAttribute("messaging.operation", "receive");
        Span.current().setAttribute("messaging.message_payload_size_bytes", message.getPayloadLength());
        
        logger.debug("Received message from: {}", session.getId());
        // Extract any trace context from the message if present
        // For now, pass the current context to the service
        webSocketService.handleMessage(session, message.getPayload());
    }

    @WithSpan(value = "websocket.connection.closed", kind = SpanKind.SERVER)
    @Override
    public void afterConnectionClosed(
            @SpanAttribute("websocket.session_id") WebSocketSession session, 
            @SpanAttribute("websocket.close_status") CloseStatus status) {
        Span.current().setAttribute("websocket.close_reason", status.getReason() != null ? status.getReason() : "");
        Span.current().setAttribute("messaging.system", "websocket");
        Span.current().setAttribute("messaging.operation", "disconnect");
        
        logger.info("WebSocket connection closed: {}, status: {}", session.getId(), status);
        webSocketService.removeSession(session.getId());
    }

    @WithSpan(value = "websocket.transport.error", kind = SpanKind.SERVER)
    @Override
    public void handleTransportError(
            @SpanAttribute("websocket.session_id") WebSocketSession session, 
            Throwable exception) {
        Span.current().setAttribute("messaging.system", "websocket");
        Span.current().setAttribute("messaging.operation", "error");
        Span.current().setAttribute("error", true);
        Span.current().setAttribute("error.type", exception.getClass().getName());
        Span.current().setAttribute("error.message", exception.getMessage() != null ? exception.getMessage() : "");
        Span.current().recordException(exception);
        
        logger.error("WebSocket transport error for session: {}", session.getId(), exception);
        
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (Exception e) {
            logger.error("Error closing WebSocket session after transport error", e);
            Span.current().recordException(e);
        }
        
        webSocketService.removeSession(session.getId());
    }
}