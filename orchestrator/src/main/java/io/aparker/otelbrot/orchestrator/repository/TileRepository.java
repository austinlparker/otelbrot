package io.aparker.otelbrot.orchestrator.repository;

import io.aparker.otelbrot.commons.model.TileResult;
import io.aparker.otelbrot.commons.model.TileStatus;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Repository for storing and retrieving Tile information in Redis
 */
@Repository
public class TileRepository {
    private static final String TILE_KEY_PREFIX = "tile:";
    private static final String TILE_DATA_KEY_PREFIX = "tiledata:";
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisTemplate<String, byte[]> byteRedisTemplate;

    public TileRepository(
            RedisTemplate<String, Object> redisTemplate,
            RedisTemplate<String, byte[]> byteRedisTemplate) {
        this.redisTemplate = redisTemplate;
        this.byteRedisTemplate = byteRedisTemplate;
    }

    /**
     * Save a tile result to Redis
     */
    public void saveTileResult(TileResult result) {
        // Save metadata
        String metadataKey = getTileKey(result.getJobId(), result.getTileId());
        Map<String, String> tileMap = new HashMap<>();
        
        tileMap.put("jobId", result.getJobId());
        tileMap.put("tileId", result.getTileId());
        tileMap.put("width", String.valueOf(result.getWidth()));
        tileMap.put("height", String.valueOf(result.getHeight()));
        tileMap.put("pixelStartX", String.valueOf(result.getPixelStartX()));
        tileMap.put("pixelStartY", String.valueOf(result.getPixelStartY()));
        tileMap.put("calculationTimeMs", String.valueOf(result.getCalculationTimeMs()));
        tileMap.put("status", result.getStatus().name());
        
        redisTemplate.opsForHash().putAll(metadataKey, tileMap);
        
        // Save image data separately
        String dataKey = getTileDataKey(result.getJobId(), result.getTileId());
        byteRedisTemplate.opsForValue().set(dataKey, result.getImageData());
    }

    /**
     * Find a tile result by jobId and tileId
     */
    public Optional<TileResult> findByJobIdAndTileId(String jobId, String tileId) {
        String metadataKey = getTileKey(jobId, tileId);
        Map<Object, Object> tileMap = redisTemplate.opsForHash().entries(metadataKey);
        
        if (tileMap == null || tileMap.isEmpty()) {
            return Optional.empty();
        }
        
        // Get image data
        String dataKey = getTileDataKey(jobId, tileId);
        byte[] imageData = byteRedisTemplate.opsForValue().get(dataKey);
        
        return Optional.of(mapToTileResult(tileMap, imageData));
    }

    /**
     * Find all tiles for a specific job
     */
    public List<TileResult> findTilesByJobId(String jobId) {
        // Get all keys matching the pattern
        String pattern = TILE_KEY_PREFIX + jobId + ":*";
        List<String> keys = scanKeys(pattern);
        
        return keys.stream()
                .map(key -> {
                    String[] parts = key.split(":");
                    String tileId = parts[2];
                    return findByJobIdAndTileId(jobId, tileId);
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /**
     * Get image data for a specific tile
     */
    public byte[] getTileImage(String jobId, String tileId) {
        String dataKey = getTileDataKey(jobId, tileId);
        return byteRedisTemplate.opsForValue().get(dataKey);
    }

    private String getTileKey(String jobId, String tileId) {
        return TILE_KEY_PREFIX + jobId + ":" + tileId;
    }

    private String getTileDataKey(String jobId, String tileId) {
        return TILE_DATA_KEY_PREFIX + jobId + ":" + tileId;
    }

    private List<String> scanKeys(String pattern) {
        // This is a simplified implementation
        // In a production environment, you would use scan command with cursor
        return byteRedisTemplate.keys(pattern).stream()
                .map(Object::toString)
                .collect(Collectors.toList());
    }

    private TileResult mapToTileResult(Map<Object, Object> tileMap, byte[] imageData) {
        return new TileResult.Builder()
                .jobId(getString(tileMap, "jobId"))
                .tileId(getString(tileMap, "tileId"))
                .width(getInteger(tileMap, "width"))
                .height(getInteger(tileMap, "height"))
                .imageData(imageData)
                .pixelStartX(getInteger(tileMap, "pixelStartX"))
                .pixelStartY(getInteger(tileMap, "pixelStartY"))
                .calculationTimeMs(getLong(tileMap, "calculationTimeMs"))
                .status(TileStatus.valueOf(getString(tileMap, "status")))
                .build();
    }

    private String getString(Map<Object, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getInteger(Map<Object, Object> map, String key) {
        String value = getString(map, key);
        return value != null ? Integer.parseInt(value) : null;
    }

    private Long getLong(Map<Object, Object> map, String key) {
        String value = getString(map, key);
        return value != null ? Long.parseLong(value) : null;
    }
}