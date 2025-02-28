package io.aparker.otelbrot.orchestrator.model;

import java.util.Arrays;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the result of a tile computation
 */
public class TileResult {
    private String jobId;
    private String tileId;
    private int width;
    private int height;
    private byte[] imageData;
    private int pixelStartX;
    private int pixelStartY;
    private long calculationTimeMs;
    private TileStatus status;

    // Default constructor for Jackson deserialization
    public TileResult() {
    }
    
    @JsonCreator
    public TileResult(
            @JsonProperty("jobId") String jobId,
            @JsonProperty("tileId") String tileId,
            @JsonProperty("width") int width,
            @JsonProperty("height") int height,
            @JsonProperty("imageData") byte[] imageData,
            @JsonProperty("pixelStartX") int pixelStartX,
            @JsonProperty("pixelStartY") int pixelStartY,
            @JsonProperty("calculationTimeMs") long calculationTimeMs,
            @JsonProperty("status") String status) {
        this.jobId = jobId;
        this.tileId = tileId;
        this.width = width;
        this.height = height;
        this.imageData = imageData;
        this.pixelStartX = pixelStartX;
        this.pixelStartY = pixelStartY;
        this.calculationTimeMs = calculationTimeMs;
        this.status = status != null ? TileStatus.valueOf(status) : TileStatus.COMPLETED;
    }

    private TileResult(Builder builder) {
        this.jobId = builder.jobId;
        this.tileId = builder.tileId;
        this.width = builder.width;
        this.height = builder.height;
        this.imageData = builder.imageData;
        this.pixelStartX = builder.pixelStartX;
        this.pixelStartY = builder.pixelStartY;
        this.calculationTimeMs = builder.calculationTimeMs;
        this.status = builder.status;
    }

    // Getters and setters for Jackson serialization/deserialization
    public String getJobId() {
        return jobId;
    }
    
    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getTileId() {
        return tileId;
    }
    
    public void setTileId(String tileId) {
        this.tileId = tileId;
    }

    public int getWidth() {
        return width;
    }
    
    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }
    
    public void setHeight(int height) {
        this.height = height;
    }

    public byte[] getImageData() {
        return imageData;
    }
    
    public void setImageData(byte[] imageData) {
        this.imageData = imageData;
    }

    public int getPixelStartX() {
        return pixelStartX;
    }
    
    public void setPixelStartX(int pixelStartX) {
        this.pixelStartX = pixelStartX;
    }

    public int getPixelStartY() {
        return pixelStartY;
    }
    
    public void setPixelStartY(int pixelStartY) {
        this.pixelStartY = pixelStartY;
    }

    public long getCalculationTimeMs() {
        return calculationTimeMs;
    }
    
    public void setCalculationTimeMs(long calculationTimeMs) {
        this.calculationTimeMs = calculationTimeMs;
    }

    public TileStatus getStatus() {
        return status;
    }
    
    public void setStatus(TileStatus status) {
        this.status = status;
    }
    
    public void setStatus(String status) {
        if (status != null) {
            try {
                this.status = TileStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                this.status = TileStatus.COMPLETED;
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TileResult that = (TileResult) o;
        return Objects.equals(jobId, that.jobId) && 
               Objects.equals(tileId, that.tileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId, tileId);
    }

    // Create a failed result for a tile spec
    public static TileResult failedResult(TileSpec spec, String errorMessage) {
        return new Builder()
                .jobId(spec.getJobId())
                .tileId(spec.getTileId())
                .width(spec.getWidth())
                .height(spec.getHeight())
                .imageData(errorMessage.getBytes())
                .pixelStartX(spec.getPixelStartX())
                .pixelStartY(spec.getPixelStartY())
                .calculationTimeMs(0)
                .status(TileStatus.FAILED)
                .build();
    }

    // Builder pattern
    public static class Builder {
        private String jobId;
        private String tileId;
        private int width;
        private int height;
        private byte[] imageData;
        private int pixelStartX;
        private int pixelStartY;
        private long calculationTimeMs;
        private TileStatus status = TileStatus.COMPLETED;

        public Builder jobId(String jobId) {
            this.jobId = jobId;
            return this;
        }

        public Builder tileId(String tileId) {
            this.tileId = tileId;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder height(int height) {
            this.height = height;
            return this;
        }

        public Builder imageData(byte[] imageData) {
            this.imageData = imageData;
            return this;
        }

        public Builder pixelStartX(int pixelStartX) {
            this.pixelStartX = pixelStartX;
            return this;
        }

        public Builder pixelStartY(int pixelStartY) {
            this.pixelStartY = pixelStartY;
            return this;
        }

        public Builder calculationTimeMs(long calculationTimeMs) {
            this.calculationTimeMs = calculationTimeMs;
            return this;
        }

        public Builder status(TileStatus status) {
            this.status = status;
            return this;
        }

        public TileResult build() {
            return new TileResult(this);
        }
    }
}