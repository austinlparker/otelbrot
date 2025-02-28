package io.aparker.otelbrot.commons.model;

/**
 * Represents the status of a tile computation
 */
public enum TileStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}