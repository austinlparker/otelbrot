package io.aparker.otelbrot.worker.service;

import org.springframework.stereotype.Component;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Maps iteration values to colors for fractal visualization
 */
@Component
public class ColorMapper {
    
    // Color mapping functions
    private final Map<String, BiFunction<Integer, Integer, Color>> colorMaps = new HashMap<>();
    
    public ColorMapper() {
        // Initialize color maps
        colorMaps.put("classic", this::classicColorMap);
        colorMaps.put("fire", this::fireColorMap);
        colorMaps.put("ocean", this::oceanColorMap);
        colorMaps.put("grayscale", this::grayscaleColorMap);
        colorMaps.put("rainbow", this::rainbowColorMap);
    }
    
    /**
     * Apply a color mapping to an iteration value
     */
    public Color applyColorMap(int iterations, int maxIterations, String scheme) {
        BiFunction<Integer, Integer, Color> mapper = colorMaps.getOrDefault(
                scheme.toLowerCase(), this::classicColorMap);
        
        return mapper.apply(iterations, maxIterations);
    }
    
    /**
     * Classic black and white color map
     */
    private Color classicColorMap(int iterations, int maxIterations) {
        if (iterations == maxIterations) {
            return Color.BLACK;
        }
        
        float hue = 0.7f + 0.3f * (float) iterations / maxIterations;
        return Color.getHSBColor(hue, 0.8f, 1.0f);
    }
    
    /**
     * Fire-themed color map
     */
    private Color fireColorMap(int iterations, int maxIterations) {
        if (iterations == maxIterations) {
            return Color.BLACK;
        }
        
        float ratio = (float) iterations / maxIterations;
        int r = (int) (255 * Math.min(1.0f, ratio * 2));
        int g = (int) (255 * Math.min(1.0f, ratio));
        int b = 0;
        
        return new Color(r, g, b);
    }
    
    /**
     * Ocean-themed color map
     */
    private Color oceanColorMap(int iterations, int maxIterations) {
        if (iterations == maxIterations) {
            return Color.BLACK;
        }
        
        float ratio = (float) iterations / maxIterations;
        int r = 0;
        int g = (int) (255 * Math.min(1.0f, ratio));
        int b = (int) (255 * Math.min(1.0f, ratio * 1.5f));
        
        return new Color(r, g, b);
    }
    
    /**
     * Grayscale color map
     */
    private Color grayscaleColorMap(int iterations, int maxIterations) {
        if (iterations == maxIterations) {
            return Color.BLACK;
        }
        
        float brightness = 1.0f - (float) iterations / maxIterations;
        int value = (int) (brightness * 255);
        
        return new Color(value, value, value);
    }
    
    /**
     * Rainbow color map
     */
    private Color rainbowColorMap(int iterations, int maxIterations) {
        if (iterations == maxIterations) {
            return Color.BLACK;
        }
        
        float hue = (float) iterations / maxIterations;
        return Color.getHSBColor(hue, 0.85f, 1.0f);
    }
}