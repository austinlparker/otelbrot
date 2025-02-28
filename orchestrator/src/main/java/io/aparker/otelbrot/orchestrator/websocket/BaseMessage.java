package io.aparker.otelbrot.orchestrator.websocket;

/**
 * Base class for all WebSocket messages
 */
public abstract class BaseMessage {
    private final String type;
    private final String jobId;

    protected BaseMessage(String type, String jobId) {
        this.type = type;
        this.jobId = jobId;
    }

    public String getType() {
        return type;
    }

    public String getJobId() {
        return jobId;
    }
}