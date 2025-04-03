package io.aparker.otelbrot.orchestrator.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Represents a request to render a fractal with specific parameters
 */
public class RenderRequest {
    
    @NotNull
    private Double centerX;
    
    @NotNull
    private Double centerY;
    
    @NotNull
    @Min(value = 0, message = "Zoom must be positive")
    private Double zoom;
    
    @NotNull
    @Min(value = 10, message = "Max iterations must be at least 10")
    @Max(value = 10000, message = "Max iterations cannot exceed 10000")
    private Integer maxIterations;
    
    @NotNull
    @Min(value = 1, message = "Width must be at least 1")
    @Max(value = 4096, message = "Width cannot exceed 4096")
    private Integer width;
    
    @NotNull
    @Min(value = 1, message = "Height must be at least 1")
    @Max(value = 4096, message = "Height cannot exceed 4096")
    private Integer height;
    
    @NotNull
    @Size(min = 1, max = 50, message = "Color scheme name must be between 1 and 50 characters")
    private String colorScheme;
    
    @Min(value = 64, message = "Tile size must be at least 64")
    @Max(value = 512, message = "Tile size cannot exceed 512")
    private Integer tileSize;
    
    @Min(value = 1, message = "Max concurrency must be at least 1")
    @Max(value = 100, message = "Max concurrency cannot exceed 100")
    private Integer maxConcurrency = 10;
    
    // Constructors
    public RenderRequest() {}
    
    public RenderRequest(Double centerX, Double centerY, Double zoom, Integer maxIterations, 
                        Integer width, Integer height, String colorScheme) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.zoom = zoom;
        this.maxIterations = maxIterations;
        this.width = width;
        this.height = height;
        this.colorScheme = colorScheme;
    }
    
    public RenderRequest(Double centerX, Double centerY, Double zoom, Integer maxIterations, 
                        Integer width, Integer height, String colorScheme, Integer tileSize) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.zoom = zoom;
        this.maxIterations = maxIterations;
        this.width = width;
        this.height = height;
        this.colorScheme = colorScheme;
        this.tileSize = tileSize;
    }
    
    public RenderRequest(Double centerX, Double centerY, Double zoom, Integer maxIterations, 
                        Integer width, Integer height, String colorScheme, Integer tileSize,
                        Integer maxConcurrency) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.zoom = zoom;
        this.maxIterations = maxIterations;
        this.width = width;
        this.height = height;
        this.colorScheme = colorScheme;
        this.tileSize = tileSize;
        this.maxConcurrency = maxConcurrency;
    }
    
    // Getters and Setters
    public Double getCenterX() {
        return centerX;
    }
    
    public void setCenterX(Double centerX) {
        this.centerX = centerX;
    }
    
    public Double getCenterY() {
        return centerY;
    }
    
    public void setCenterY(Double centerY) {
        this.centerY = centerY;
    }
    
    public Double getZoom() {
        return zoom;
    }
    
    public void setZoom(Double zoom) {
        this.zoom = zoom;
    }
    
    public Integer getMaxIterations() {
        return maxIterations;
    }
    
    public void setMaxIterations(Integer maxIterations) {
        this.maxIterations = maxIterations;
    }
    
    public Integer getWidth() {
        return width;
    }
    
    public void setWidth(Integer width) {
        this.width = width;
    }
    
    public Integer getHeight() {
        return height;
    }
    
    public void setHeight(Integer height) {
        this.height = height;
    }
    
    public String getColorScheme() {
        return colorScheme;
    }
    
    public void setColorScheme(String colorScheme) {
        this.colorScheme = colorScheme;
    }
    
    public Integer getTileSize() {
        return tileSize;
    }
    
    public void setTileSize(Integer tileSize) {
        this.tileSize = tileSize;
    }
    
    public Integer getMaxConcurrency() {
        return maxConcurrency;
    }
    
    public void setMaxConcurrency(Integer maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }
}