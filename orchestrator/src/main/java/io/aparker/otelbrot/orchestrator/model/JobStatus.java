package io.aparker.otelbrot.orchestrator.model;

/**
 * Represents the status of a fractal rendering job
 */
public enum JobStatus {
    CREATED,
    PROCESSING,
    PREVIEW_READY,
    COMPLETED,
    FAILED,
    CANCELLED
}