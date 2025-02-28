package io.aparker.otelbrot.orchestrator.websocket;

/**
 * WebSocket message for error notifications
 */
public class ErrorMessage extends BaseMessage {
    private final String errorCode;
    private final String message;

    public ErrorMessage(String jobId, String errorCode, String message) {
        super("error", jobId);
        this.errorCode = errorCode;
        this.message = message;
    }

    // Getters
    public String getErrorCode() {
        return errorCode;
    }

    public String getMessage() {
        return message;
    }
}