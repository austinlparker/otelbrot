package io.aparker.otelbrot.orchestrator.websocket;

import io.aparker.otelbrot.orchestrator.model.FractalJob;

/**
 * WebSocket message for job progress updates
 */
public class ProgressMessage extends BaseMessage {
    private final double progress;
    private final int completedTiles;
    private final int totalTiles;
    private final long elapsedTimeMs;

    public ProgressMessage(String jobId, double progress, int completedTiles, int totalTiles, long elapsedTimeMs) {
        super("progress", jobId);
        this.progress = progress;
        this.completedTiles = completedTiles;
        this.totalTiles = totalTiles;
        this.elapsedTimeMs = elapsedTimeMs;
    }

    // Factory method to create from FractalJob
    public static ProgressMessage fromFractalJob(FractalJob job, long elapsedTimeMs) {
        return new ProgressMessage(
            job.getJobId(),
            job.getProgress(),
            job.getCompletedTiles(),
            job.getTotalTiles(),
            elapsedTimeMs
        );
    }

    // Getters
    public double getProgress() {
        return progress;
    }

    public int getCompletedTiles() {
        return completedTiles;
    }

    public int getTotalTiles() {
        return totalTiles;
    }

    public long getElapsedTimeMs() {
        return elapsedTimeMs;
    }
}