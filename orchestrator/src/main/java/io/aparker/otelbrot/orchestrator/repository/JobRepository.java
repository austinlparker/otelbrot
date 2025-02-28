package io.aparker.otelbrot.orchestrator.repository;

import io.aparker.otelbrot.orchestrator.model.FractalJob;
import io.aparker.otelbrot.orchestrator.model.JobStatus;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Repository for storing and retrieving FractalJob information in Redis
 */
@Repository
public class JobRepository {
    private static final String JOB_KEY_PREFIX = "job:";
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ISO_ZONED_DATE_TIME;
    
    private final RedisTemplate<String, Object> redisTemplate;

    public JobRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Save or update a job in Redis
     */
    public void save(FractalJob job) {
        String key = getJobKey(job.getJobId());
        Map<String, String> jobMap = new HashMap<>();
        
        jobMap.put("jobId", job.getJobId());
        jobMap.put("centerX", String.valueOf(job.getCenterX()));
        jobMap.put("centerY", String.valueOf(job.getCenterY()));
        jobMap.put("zoom", String.valueOf(job.getZoom()));
        jobMap.put("maxIterations", String.valueOf(job.getMaxIterations()));
        jobMap.put("width", String.valueOf(job.getWidth()));
        jobMap.put("height", String.valueOf(job.getHeight()));
        jobMap.put("colorScheme", job.getColorScheme());
        jobMap.put("status", job.getStatus().name());
        jobMap.put("createdAt", DATETIME_FORMATTER.format(job.getCreatedAt()));
        jobMap.put("updatedAt", DATETIME_FORMATTER.format(job.getUpdatedAt()));
        jobMap.put("completedTiles", String.valueOf(job.getCompletedTiles()));
        jobMap.put("totalTiles", String.valueOf(job.getTotalTiles()));
        
        redisTemplate.opsForHash().putAll(key, jobMap);
    }

    /**
     * Find a job by its ID
     */
    public Optional<FractalJob> findById(String jobId) {
        String key = getJobKey(jobId);
        Map<Object, Object> jobMap = redisTemplate.opsForHash().entries(key);
        
        if (jobMap == null || jobMap.isEmpty()) {
            return Optional.empty();
        }
        
        return Optional.of(mapToJob(jobMap));
    }

    /**
     * Update the progress of a job
     */
    public void updateProgress(String jobId, int completedTiles, int totalTiles) {
        Optional<FractalJob> optionalJob = findById(jobId);
        if (optionalJob.isPresent()) {
            FractalJob job = optionalJob.get();
            job.setCompletedTiles(completedTiles);
            job.setTotalTiles(totalTiles);
            save(job);
        }
    }

    /**
     * Update the status of a job
     */
    public void updateStatus(String jobId, JobStatus status) {
        Optional<FractalJob> optionalJob = findById(jobId);
        if (optionalJob.isPresent()) {
            FractalJob job = optionalJob.get();
            job.setStatus(status);
            save(job);
        }
    }

    /**
     * Increment completed tiles count
     */
    public void incrementCompletedTiles(String jobId) {
        Optional<FractalJob> optionalJob = findById(jobId);
        if (optionalJob.isPresent()) {
            FractalJob job = optionalJob.get();
            job.incrementCompletedTiles();
            save(job);
        }
    }

    private String getJobKey(String jobId) {
        return JOB_KEY_PREFIX + jobId;
    }

    private FractalJob mapToJob(Map<Object, Object> jobMap) {
        return new FractalJob.Builder()
                .jobId(getString(jobMap, "jobId"))
                .centerX(getDouble(jobMap, "centerX"))
                .centerY(getDouble(jobMap, "centerY"))
                .zoom(getDouble(jobMap, "zoom"))
                .maxIterations(getInteger(jobMap, "maxIterations"))
                .width(getInteger(jobMap, "width"))
                .height(getInteger(jobMap, "height"))
                .colorScheme(getString(jobMap, "colorScheme"))
                .status(JobStatus.valueOf(getString(jobMap, "status")))
                .createdAt(ZonedDateTime.parse(getString(jobMap, "createdAt"), DATETIME_FORMATTER))
                .updatedAt(ZonedDateTime.parse(getString(jobMap, "updatedAt"), DATETIME_FORMATTER))
                .completedTiles(getInteger(jobMap, "completedTiles"))
                .totalTiles(getInteger(jobMap, "totalTiles"))
                .build();
    }

    private String getString(Map<Object, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Double getDouble(Map<Object, Object> map, String key) {
        String value = getString(map, key);
        return value != null ? Double.parseDouble(value) : null;
    }

    private Integer getInteger(Map<Object, Object> map, String key) {
        String value = getString(map, key);
        return value != null ? Integer.parseInt(value) : null;
    }
}