package io.aparker.otelbrot.commons.model;

import java.util.Objects;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Specification for a fractal tile to be computed
 */
public class TileSpec {
    private String jobId;
    private String tileId;
    private double xMin;
    private double yMin;
    private double xMax;
    private double yMax;
    private int width;
    private int height;
    private int maxIterations;
    private String colorScheme;
    private int pixelStartX;
    private int pixelStartY;
    // Not storing trace context in TileSpec anymore - using environment variables instead

    // Default constructor for Jackson deserialization
    public TileSpec() {
    }
    
    @JsonCreator
    public TileSpec(
            @JsonProperty("jobId") String jobId,
            @JsonProperty("tileId") String tileId,
            @JsonProperty("xMin") double xMin,
            @JsonProperty("yMin") double yMin,
            @JsonProperty("xMax") double xMax,
            @JsonProperty("yMax") double yMax,
            @JsonProperty("width") int width,
            @JsonProperty("height") int height,
            @JsonProperty("maxIterations") int maxIterations,
            @JsonProperty("colorScheme") String colorScheme,
            @JsonProperty("pixelStartX") int pixelStartX,
            @JsonProperty("pixelStartY") int pixelStartY) {
        this.jobId = jobId;
        this.tileId = tileId;
        this.xMin = xMin;
        this.yMin = yMin;
        this.xMax = xMax;
        this.yMax = yMax;
        this.width = width;
        this.height = height;
        this.maxIterations = maxIterations;
        this.colorScheme = colorScheme;
        this.pixelStartX = pixelStartX;
        this.pixelStartY = pixelStartY;
    }
    
    private TileSpec(Builder builder) {
        this.jobId = builder.jobId;
        this.tileId = builder.tileId;
        this.xMin = builder.xMin;
        this.yMin = builder.yMin;
        this.xMax = builder.xMax;
        this.yMax = builder.yMax;
        this.width = builder.width;
        this.height = builder.height;
        this.maxIterations = builder.maxIterations;
        this.colorScheme = builder.colorScheme;
        this.pixelStartX = builder.pixelStartX;
        this.pixelStartY = builder.pixelStartY;
    }

    // Static factory method to create from environment variables
    public static TileSpec fromEnvironment() {
        String xMinStr = System.getenv("TILE_SPEC_X_MIN");
        String yMinStr = System.getenv("TILE_SPEC_Y_MIN");
        String xMaxStr = System.getenv("TILE_SPEC_X_MAX");
        String yMaxStr = System.getenv("TILE_SPEC_Y_MAX");
        String widthStr = System.getenv("TILE_SPEC_WIDTH");
        String heightStr = System.getenv("TILE_SPEC_HEIGHT");
        String maxIterationsStr = System.getenv("TILE_SPEC_MAX_ITERATIONS");
        String pixelStartXStr = System.getenv("TILE_SPEC_PIXEL_START_X");
        String pixelStartYStr = System.getenv("TILE_SPEC_PIXEL_START_Y");
        
        // Check for null values
        if (xMinStr == null || yMinStr == null || xMaxStr == null || yMaxStr == null || 
            widthStr == null || heightStr == null || maxIterationsStr == null || 
            pixelStartXStr == null || pixelStartYStr == null) {
            throw new IllegalArgumentException("Missing required environment variables for TileSpec");
        }
        
        // Build the tile spec
        Builder builder = new Builder()
                .jobId(System.getenv("TILE_SPEC_JOB_ID"))
                .tileId(System.getenv("TILE_SPEC_TILE_ID"))
                .xMin(Double.parseDouble(xMinStr))
                .yMin(Double.parseDouble(yMinStr))
                .xMax(Double.parseDouble(xMaxStr))
                .yMax(Double.parseDouble(yMaxStr))
                .width(Integer.parseInt(widthStr))
                .height(Integer.parseInt(heightStr))
                .maxIterations(Integer.parseInt(maxIterationsStr))
                .colorScheme(System.getenv("TILE_SPEC_COLOR_SCHEME"))
                .pixelStartX(Integer.parseInt(pixelStartXStr))
                .pixelStartY(Integer.parseInt(pixelStartYStr));
                
        // Not adding trace context to TileSpec anymore - using environment variables directly
        
        return builder.build();
    }

    // Getters and setters
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

    public double getXMin() {
        return xMin;
    }
    
    public void setXMin(double xMin) {
        this.xMin = xMin;
    }

    public double getYMin() {
        return yMin;
    }
    
    public void setYMin(double yMin) {
        this.yMin = yMin;
    }

    public double getXMax() {
        return xMax;
    }
    
    public void setXMax(double xMax) {
        this.xMax = xMax;
    }

    public double getYMax() {
        return yMax;
    }
    
    public void setYMax(double yMax) {
        this.yMax = yMax;
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

    public int getMaxIterations() {
        return maxIterations;
    }
    
    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public String getColorScheme() {
        return colorScheme;
    }
    
    public void setColorScheme(String colorScheme) {
        this.colorScheme = colorScheme;
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
    
    // Trace context is now handled via environment variables, not stored in TileSpec

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TileSpec tileSpec = (TileSpec) o;
        return Objects.equals(jobId, tileSpec.jobId) && 
               Objects.equals(tileId, tileSpec.tileId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId, tileId);
    }

    // Builder pattern
    public static class Builder {
        private String jobId;
        private String tileId = UUID.randomUUID().toString();
        private double xMin;
        private double yMin;
        private double xMax;
        private double yMax;
        private int width;
        private int height;
        private int maxIterations;
        private String colorScheme;
        private int pixelStartX;
        private int pixelStartY;
        public Builder jobId(String jobId) {
            this.jobId = jobId;
            return this;
        }

        public Builder tileId(String tileId) {
            this.tileId = tileId;
            return this;
        }

        public Builder xMin(double xMin) {
            this.xMin = xMin;
            return this;
        }

        public Builder yMin(double yMin) {
            this.yMin = yMin;
            return this;
        }

        public Builder xMax(double xMax) {
            this.xMax = xMax;
            return this;
        }

        public Builder yMax(double yMax) {
            this.yMax = yMax;
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

        public Builder maxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }

        public Builder colorScheme(String colorScheme) {
            this.colorScheme = colorScheme;
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

        public TileSpec build() {
            return new TileSpec(this);
        }
    }
}