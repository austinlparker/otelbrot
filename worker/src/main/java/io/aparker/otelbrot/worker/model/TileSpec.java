package io.aparker.otelbrot.worker.model;

/**
 * Specification for a fractal tile to be computed
 */
public class TileSpec {
    private final String jobId;
    private final String tileId;
    private final double xMin;
    private final double yMin;
    private final double xMax;
    private final double yMax;
    private final int width;
    private final int height;
    private final int maxIterations;
    private final String colorScheme;
    private final int pixelStartX;
    private final int pixelStartY;

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
        return new Builder()
                .jobId(System.getenv("TILE_SPEC_JOB_ID"))
                .tileId(System.getenv("TILE_SPEC_TILE_ID"))
                .xMin(Double.parseDouble(System.getenv("TILE_SPEC_X_MIN")))
                .yMin(Double.parseDouble(System.getenv("TILE_SPEC_Y_MIN")))
                .xMax(Double.parseDouble(System.getenv("TILE_SPEC_X_MAX")))
                .yMax(Double.parseDouble(System.getenv("TILE_SPEC_Y_MAX")))
                .width(Integer.parseInt(System.getenv("TILE_SPEC_WIDTH")))
                .height(Integer.parseInt(System.getenv("TILE_SPEC_HEIGHT")))
                .maxIterations(Integer.parseInt(System.getenv("TILE_SPEC_MAX_ITERATIONS")))
                .colorScheme(System.getenv("TILE_SPEC_COLOR_SCHEME"))
                .pixelStartX(Integer.parseInt(System.getenv("TILE_SPEC_PIXEL_START_X")))
                .pixelStartY(Integer.parseInt(System.getenv("TILE_SPEC_PIXEL_START_Y")))
                .build();
    }

    // Getters
    public String getJobId() {
        return jobId;
    }

    public String getTileId() {
        return tileId;
    }

    public double getXMin() {
        return xMin;
    }

    public double getYMin() {
        return yMin;
    }

    public double getXMax() {
        return xMax;
    }

    public double getYMax() {
        return yMax;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public String getColorScheme() {
        return colorScheme;
    }

    public int getPixelStartX() {
        return pixelStartX;
    }

    public int getPixelStartY() {
        return pixelStartY;
    }

    // Builder pattern
    public static class Builder {
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