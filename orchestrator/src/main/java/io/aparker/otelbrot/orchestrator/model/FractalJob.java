package io.aparker.otelbrot.orchestrator.model;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a fractal rendering job
 */
public class FractalJob {
    private final String jobId;
    private final double centerX;
    private final double centerY;
    private final double zoom;
    private final int maxIterations;
    private final int width;
    private final int height;
    private final String colorScheme;
    private JobStatus status;
    private final ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private int completedTiles;
    private int totalTiles;

    private FractalJob(Builder builder) {
        this.jobId = builder.jobId;
        this.centerX = builder.centerX;
        this.centerY = builder.centerY;
        this.zoom = builder.zoom;
        this.maxIterations = builder.maxIterations;
        this.width = builder.width;
        this.height = builder.height;
        this.colorScheme = builder.colorScheme;
        this.status = builder.status;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
        this.completedTiles = builder.completedTiles;
        this.totalTiles = builder.totalTiles;
    }

    // Static factory method from RenderRequest
    public static FractalJob fromRenderRequest(RenderRequest request) {
        return new Builder()
                .jobId(UUID.randomUUID().toString())
                .centerX(request.getCenterX())
                .centerY(request.getCenterY())
                .zoom(request.getZoom())
                .maxIterations(request.getMaxIterations())
                .width(request.getWidth())
                .height(request.getHeight())
                .colorScheme(request.getColorScheme())
                .status(JobStatus.CREATED)
                .createdAt(ZonedDateTime.now())
                .updatedAt(ZonedDateTime.now())
                .completedTiles(0)
                .totalTiles(0)
                .build();
    }

    // Getters
    public String getJobId() {
        return jobId;
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterY() {
        return centerY;
    }

    public double getZoom() {
        return zoom;
    }

    public int getMaxIterations() {
        return maxIterations;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getColorScheme() {
        return colorScheme;
    }

    public JobStatus getStatus() {
        return status;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public int getCompletedTiles() {
        return completedTiles;
    }

    public int getTotalTiles() {
        return totalTiles;
    }

    // Setters for mutable properties
    public void setStatus(JobStatus status) {
        this.status = status;
        this.updatedAt = ZonedDateTime.now();
    }

    public void setCompletedTiles(int completedTiles) {
        this.completedTiles = completedTiles;
        this.updatedAt = ZonedDateTime.now();
    }

    public void setTotalTiles(int totalTiles) {
        this.totalTiles = totalTiles;
        this.updatedAt = ZonedDateTime.now();
    }

    public void incrementCompletedTiles() {
        this.completedTiles++;
        this.updatedAt = ZonedDateTime.now();
        
        // Update job status based on completion
        if (this.completedTiles == this.totalTiles && this.totalTiles > 0) {
            this.status = JobStatus.COMPLETED;
        }
    }

    // Calculate progress as a percentage
    public double getProgress() {
        if (totalTiles == 0) {
            return 0.0;
        }
        return (double) completedTiles / totalTiles * 100.0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FractalJob that = (FractalJob) o;
        return Objects.equals(jobId, that.jobId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId);
    }

    // Builder pattern
    public static class Builder {
        private String jobId;
        private double centerX;
        private double centerY;
        private double zoom;
        private int maxIterations;
        private int width;
        private int height;
        private String colorScheme;
        private JobStatus status = JobStatus.CREATED;
        private ZonedDateTime createdAt = ZonedDateTime.now();
        private ZonedDateTime updatedAt = ZonedDateTime.now();
        private int completedTiles = 0;
        private int totalTiles = 0;

        public Builder jobId(String jobId) {
            this.jobId = jobId;
            return this;
        }

        public Builder centerX(double centerX) {
            this.centerX = centerX;
            return this;
        }

        public Builder centerY(double centerY) {
            this.centerY = centerY;
            return this;
        }

        public Builder zoom(double zoom) {
            this.zoom = zoom;
            return this;
        }

        public Builder maxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
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

        public Builder colorScheme(String colorScheme) {
            this.colorScheme = colorScheme;
            return this;
        }

        public Builder status(JobStatus status) {
            this.status = status;
            return this;
        }

        public Builder createdAt(ZonedDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(ZonedDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder completedTiles(int completedTiles) {
            this.completedTiles = completedTiles;
            return this;
        }

        public Builder totalTiles(int totalTiles) {
            this.totalTiles = totalTiles;
            return this;
        }

        public FractalJob build() {
            return new FractalJob(this);
        }
    }
}