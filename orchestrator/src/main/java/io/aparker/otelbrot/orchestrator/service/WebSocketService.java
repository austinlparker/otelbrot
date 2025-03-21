package io.aparker.otelbrot.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aparker.otelbrot.commons.model.TileResult;
import io.aparker.otelbrot.orchestrator.model.FractalJob;
import io.aparker.otelbrot.orchestrator.model.JobStatus;
import io.aparker.otelbrot.orchestrator.websocket.ErrorMessage;
import io.aparker.otelbrot.orchestrator.websocket.ProgressMessage;
import io.aparker.otelbrot.orchestrator.websocket.TileMessage;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service for managing WebSocket connections and messages
 */
@Service
public class WebSocketService {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketService.class);
    private static final String JOB_UPDATES_CHANNEL = "job_updates:";
    private static final String TILE_UPDATES_CHANNEL = "tile_updates:";
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    
    // Map to store active WebSocket sessions by sessionId
    private final Map<String, WebSocketSession> sessionRegistry = new ConcurrentHashMap<>();
    
    // Map to track which job a session is subscribed to
    private final Map<String, String> sessionJobMap = new ConcurrentHashMap<>();
    
    // Session locks to prevent concurrent writes to the same session
    private final Map<String, Lock> sessionLocks = new ConcurrentHashMap<>();

    public WebSocketService(RedisTemplate<String, String> redisTemplate, 
                           ObjectMapper objectMapper,
                           Tracer tracer) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
    }

    /**
     * Register a new WebSocket session
     */
    public void registerSession(String sessionId, WebSocketSession session) {
        sessionRegistry.put(sessionId, session);
        // Create a lock for this session
        sessionLocks.put(sessionId, new ReentrantLock());
        logger.info("WebSocket session registered: {}", sessionId);
    }

    /**
     * Remove a WebSocket session
     */
    public void removeSession(String sessionId) {
        sessionRegistry.remove(sessionId);
        String jobId = sessionJobMap.remove(sessionId);
        // Remove the session lock
        sessionLocks.remove(sessionId);
        logger.info("WebSocket session removed: {}, was subscribed to job: {}", sessionId, jobId);
    }

    /**
     * Handle incoming WebSocket message from client
     */
    @WithSpan("WebSocketService.handleMessage")
    public void handleMessage(
            @SpanAttribute("websocket.session_id") String sessionId,
            WebSocketSession session, 
            String message) {
        Span.current().setAttribute("messaging.system", "websocket");
        Span.current().setAttribute("messaging.operation", "process");
        
        try {
            Map<String, Object> messageMap = objectMapper.readValue(message, Map.class);
            String type = (String) messageMap.get("type");
            Span.current().setAttribute("websocket.message.type", type);
            
            if ("subscribe".equals(type)) {
                String jobId = (String) messageMap.get("jobId");
                if (jobId != null) {
                    Span.current().setAttribute("job.id", jobId);
                    // Associate this session with the job
                    sessionJobMap.put(sessionId, jobId);
                    logger.info("Session {} subscribed to job updates for {}", sessionId, jobId);
                    Span.current().addEvent("Session subscribed to job updates");
                }
            }
        } catch (Exception e) {
            logger.error("Error handling WebSocket message", e);
            Span.current().recordException(e);
            Span.current().setStatus(StatusCode.ERROR, e.getMessage());
            
            try {
                ErrorMessage errorMessage = new ErrorMessage("", "WEBSOCKET_ERROR", e.getMessage());
                sendMessageToSession(session, objectMapper.writeValueAsString(errorMessage));
            } catch (IOException ex) {
                logger.error("Failed to send error message", ex);
                Span.current().recordException(ex);
            }
        }
    }
    
    /**
     * Convenience method for handling messages
     */
    public void handleMessage(WebSocketSession session, String message) {
        handleMessage(session.getId(), session, message);
    }

    /**
     * Send a tile update message via WebSocket
     */
    @WithSpan("WebSocketService.sendTileUpdate")
    public void sendTileUpdate(
            @SpanAttribute("job.id") String jobId,
            @SpanAttribute("tile.id") String tileId,
            TileResult tileResult) {
        Span.current().setAttribute("service.name", "otelbrot-orchestrator");
        Span.current().setAttribute("messaging.system", "websocket");
        Span.current().setAttribute("messaging.operation", "send");
        
        try {
            TileMessage message = TileMessage.fromTileResult(tileResult);
            
            // Convert to JSON
            String json = objectMapper.writeValueAsString(message);
            Span.current().setAttribute("messaging.message_payload_size_bytes", json.length());
            
            // Publish to Redis
            publishToRedis(jobId, tileId, TILE_UPDATES_CHANNEL + jobId, json);
            
            // Also send directly to connected sessions
            sendToSubscribedSessions(jobId, json);
            
            logger.debug("Sent tile update for job: {}, tile: {}", jobId, tileId);
            Span.current().addEvent("Tile update sent");
        } catch (Exception e) {
            logger.error("Failed to send tile update", e);
            Span.current().recordException(e);
            Span.current().setStatus(StatusCode.ERROR, e.getMessage());
        }
    }
    
    /**
     * Convenience method for sending tile updates
     */
    public void sendTileUpdate(TileResult tileResult) {
        sendTileUpdate(tileResult.getJobId(), tileResult.getTileId(), tileResult);
    }
    
    /**
     * Publish a message to Redis with tracing
     */
    @WithSpan("Redis.publish")
    private void publishToRedis(
            @SpanAttribute("job.id") String jobId,
            @SpanAttribute("tile.id") String tileId,
            @SpanAttribute("messaging.destination") String channel,
            String message) {
        Span.current().setAttribute("messaging.system", "redis");
        Span.current().setAttribute("messaging.operation", "publish");
        
        redisTemplate.convertAndSend(channel, message);
        Span.current().addEvent("Redis message published");
    }

    /**
     * Send a progress update message via WebSocket
     */
    @WithSpan("WebSocketService.sendProgressUpdate")
    public void sendProgressUpdate(
            @SpanAttribute("job.id") String jobId,
            @SpanAttribute("job.status") String status,
            @SpanAttribute("job.progress") double progress,
            @SpanAttribute("job.elapsed_ms") long elapsedTimeMs,
            FractalJob job) {
        Span.current().setAttribute("service.name", "otelbrot-orchestrator");
        Span.current().setAttribute("messaging.system", "websocket");
        Span.current().setAttribute("messaging.operation", "send");
        
        try {
            ProgressMessage message = ProgressMessage.fromFractalJob(job, elapsedTimeMs);
            
            // Convert to JSON
            String json = objectMapper.writeValueAsString(message);
            Span.current().setAttribute("messaging.message_payload_size_bytes", json.length());
            
            // Publish to Redis
            publishToRedis(jobId, null, JOB_UPDATES_CHANNEL + jobId, json);
            
            // Also send directly to connected sessions
            sendToSubscribedSessions(jobId, json);
            
            // Check if job is complete and log it
            if (job.getStatus() == JobStatus.COMPLETED) {
                logger.info("Job {} is now complete. Tiles: {}/{} (Completed/Total)", 
                    jobId, job.getCompletedTiles(), job.getTotalTiles());
                Span.current().addEvent("Job completed");
            } else {
                double actualProgress = Math.min(100.0, (double) job.getCompletedTiles() / Math.max(1, job.getTotalTiles()) * 100.0);
                logger.debug("Sent progress update for job: {}, progress: {}%, Tiles: {}/{}", 
                    jobId, String.format("%.2f", actualProgress), job.getCompletedTiles(), job.getTotalTiles());
                Span.current().addEvent("Progress update sent");
            }
        } catch (Exception e) {
            logger.error("Failed to send progress update", e);
            Span.current().recordException(e);
            Span.current().setStatus(StatusCode.ERROR, e.getMessage());
        }
    }
    
    /**
     * Convenience method for sending progress updates
     */
    public void sendProgressUpdate(FractalJob job, long elapsedTimeMs) {
        sendProgressUpdate(
            job.getJobId(), 
            job.getStatus().toString(), 
            job.getProgress(), 
            elapsedTimeMs, 
            job
        );
    }

    /**
     * Send an error message via WebSocket
     */
    public void sendErrorMessage(String jobId, String errorCode, String errorMessage) {
        Span span = tracer.spanBuilder("WebSocketService.sendErrorMessage")
                .setParent(Context.current())
                .setAttribute("service.name", "otelbrot-orchestrator")
                .setAttribute("messaging.system", "websocket")
                .setAttribute("messaging.operation", "send")
                .setAttribute("job.id", jobId)
                .setAttribute("error.code", errorCode)
                .setAttribute("error", true)
                .startSpan();
                
        try (Scope scope = span.makeCurrent()) {
            ErrorMessage message = new ErrorMessage(jobId, errorCode, errorMessage);
            
            // Convert to JSON
            String json = objectMapper.writeValueAsString(message);
            span.setAttribute("messaging.message_payload_size_bytes", json.length());
            
            // Publish to Redis channel for real-time updates
            Span redisSpan = tracer.spanBuilder("Redis.publish.errorMessage")
                    .setParent(Context.current())
                    .setAttribute("messaging.system", "redis")
                    .setAttribute("messaging.destination", JOB_UPDATES_CHANNEL + jobId)
                    .setAttribute("messaging.operation", "publish")
                    .setAttribute("job.id", jobId)
                    .setAttribute("error.code", errorCode)
                    .setAttribute("error", true)
                    .startSpan();
                    
            try (Scope redisScope = redisSpan.makeCurrent()) {
                redisTemplate.convertAndSend(JOB_UPDATES_CHANNEL + jobId, json);
                redisSpan.addEvent("Redis error message published");
            } finally {
                redisSpan.end();
            }
            
            // Also send directly to connected sessions
            sendToSubscribedSessions(jobId, json);
            
            logger.debug("Sent error message for job: {}, code: {}", jobId, errorCode);
            span.addEvent("Error message sent");
        } catch (Exception e) {
            logger.error("Failed to send error message", e);
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
        } finally {
            span.end();
        }
    }

    /**
     * Send a message to all sessions subscribed to a specific job
     */
    @WithSpan("WebSocketService.sendToSubscribedSessions")
    private void sendToSubscribedSessions(
            @SpanAttribute("job.id") String jobId, 
            String message) {
        Span.current().setAttribute("messaging.system", "websocket");
        Span.current().setAttribute("messaging.operation", "multicast");
        
        // Create a single TextMessage object to reuse
        TextMessage textMessage = new TextMessage(message);
        int sentCount = 0;
        
        // Find all sessions subscribed to this job and send the message
        for (Map.Entry<String, String> entry : sessionJobMap.entrySet()) {
            String sessionId = entry.getKey();
            String subscribedJobId = entry.getValue();
            
            if (jobId.equals(subscribedJobId)) {
                WebSocketSession session = sessionRegistry.get(sessionId);
                if (session != null && session.isOpen()) {
                    sendWebSocketMessage(sessionId, jobId, session, textMessage);
                    sentCount++;
                }
            }
        }
        
        Span.current().setAttribute("messaging.sent_count", sentCount);
        Span.current().addEvent("Completed sending to all subscribed sessions");
    }
    
    /**
     * Send a message to a specific WebSocket session with tracing
     */
    @WithSpan("WebSocket.sendMessage")
    private void sendWebSocketMessage(
            @SpanAttribute("websocket.session_id") String sessionId,
            @SpanAttribute("job.id") String jobId,
            WebSocketSession session,
            TextMessage message) {
        Span.current().setAttribute("messaging.system", "websocket");
        Span.current().setAttribute("messaging.operation", "send");
        
        sendMessageToSession(session, message);
        Span.current().addEvent("Message sent to session");
    }
    
    /**
     * Safely send a message to a WebSocket session using locks to prevent concurrent writes
     */
    private void sendMessageToSession(WebSocketSession session, String message) {
        try {
            sendMessageToSession(session, new TextMessage(message));
        } catch (Exception e) {
            logger.error("Failed to send message to session: {}", session.getId(), e);
        }
    }
    
    /**
     * Safely send a TextMessage to a WebSocket session using locks to prevent concurrent writes
     */
    private void sendMessageToSession(WebSocketSession session, TextMessage textMessage) {
        String sessionId = session.getId();
        Lock lock = sessionLocks.computeIfAbsent(sessionId, k -> new ReentrantLock());
        
        lock.lock();
        try {
            if (session.isOpen()) {
                session.sendMessage(textMessage);
            }
        } catch (IOException e) {
            logger.error("Failed to send message to session: {}", sessionId, e);
        } finally {
            lock.unlock();
        }
    }
}