package io.aparker.otelbrot.orchestrator.model;

/**
 * Represents the status of a tile computation
 */
public enum TileStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}