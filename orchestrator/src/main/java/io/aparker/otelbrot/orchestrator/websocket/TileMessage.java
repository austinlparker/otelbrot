package io.aparker.otelbrot.orchestrator.websocket;

import io.aparker.otelbrot.commons.model.TileResult;
import java.util.Base64;

/**
 * WebSocket message for a completed tile
 */
public class TileMessage extends BaseMessage {
    private final String tileId;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final String imageDataBase64;

    public TileMessage(String jobId, String tileId, int x, int y, int width, int height, byte[] imageData) {
        super("tile", jobId);
        this.tileId = tileId;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.imageDataBase64 = Base64.getEncoder().encodeToString(imageData);
    }

    // Factory method to create from TileResult
    public static TileMessage fromTileResult(TileResult result) {
        return new TileMessage(
            result.getJobId(),
            result.getTileId(),
            result.getPixelStartX(),
            result.getPixelStartY(),
            result.getWidth(),
            result.getHeight(),
            result.getImageData()
        );
    }

    // Getters
    public String getTileId() {
        return tileId;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getImageDataBase64() {
        return imageDataBase64;
    }
}