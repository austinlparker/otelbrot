package io.aparker.otelbrot.orchestrator.service;

import io.aparker.otelbrot.commons.model.TileResult;
import io.aparker.otelbrot.commons.model.TileSpec;
import io.aparker.otelbrot.commons.model.TileStatus;
import io.aparker.otelbrot.orchestrator.model.FractalJob;
import io.aparker.otelbrot.orchestrator.model.JobStatus;
import io.aparker.otelbrot.orchestrator.model.RenderRequest;
import io.aparker.otelbrot.orchestrator.repository.JobRepository;
import io.aparker.otelbrot.orchestrator.repository.TileRepository;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for orchestrating fractal rendering jobs
 */
@Service
public class OrchestrationService {
    private static final Logger logger = LoggerFactory.getLogger(OrchestrationService.class);
    
    private final KubernetesClient kubernetesClient;
    private final JobRepository jobRepository;
    private final TileRepository tileRepository;
    private final WebSocketService webSocketService;
    private final Tracer tracer;
    private final TextMapPropagator propagator;
    
    @Value("${kubernetes.namespace:otelbrot}")
    private String namespace;
    
    // Configurable values
    @Value("${app.worker.image:otel-monte/worker:latest}")
    private String workerImage;
    
    @Value("${app.worker.cpu.request:1000m}")
    private String workerCpuRequest;
    
    @Value("${app.worker.memory.request:1024Mi}")
    private String workerMemoryRequest;
    
    @Value("${app.tile.max-size:256}")
    private int maxTileSize;
    
    @Value("${app.worker.max-concurrent:2}")
    private int maxConcurrentWorkers;

    @Value("${app.worker.enable-agent:otelbrot/java-agent}")
    private String enableAgent;
    
    // Keep track of active worker count
    private int activeWorkerCount = 0;
    
    // Flag to indicate if cleanup is enabled
    @Value("${app.worker.cleanup-completed:true}")
    private boolean cleanupCompletedJobs;

    public OrchestrationService(
            KubernetesClient kubernetesClient,
            JobRepository jobRepository, 
            TileRepository tileRepository,
            WebSocketService webSocketService,
            Tracer tracer,
            TextMapPropagator propagator) {
        this.kubernetesClient = kubernetesClient;
        this.jobRepository = jobRepository;
        this.tileRepository = tileRepository;
        this.webSocketService = webSocketService;
        this.tracer = tracer;
        this.propagator = propagator;
    }
    
    /**
     * Get the number of available CPU cores in the Kubernetes cluster
     * or use a default value if unable to determine
     */
    private int getAvailableCores() {
        try {
            // Get the available CPU resources from the Kubernetes cluster
            return Math.max(Runtime.getRuntime().availableProcessors() - 1, 1);
        } catch (Exception e) {
            logger.warn("Unable to determine available CPU cores, using default value", e);
            return 2; // Default to 2 cores if we can't determine
        }
    }

    /**
     * Create a new fractal rendering job
     */
    public FractalJob createRenderJob(RenderRequest request) {
        Span span = tracer.spanBuilder("OrchestrationService.createRenderJob")
                .setParent(Context.current())
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            // Create and save the job
            FractalJob job = FractalJob.fromRenderRequest(request);
            jobRepository.save(job);
            span.setAttribute("job.id", job.getJobId());
            logger.info("Created new fractal job: {}", job.getJobId());
            
            // Initialize preview job (low resolution for quick feedback)
            createPreviewJob(job);
            
            // Initialize detailed tiles
            createDetailJobs(job);
            
            // Send initial progress update
            webSocketService.sendProgressUpdate(job, 0);
            
            return job;
        } finally {
            span.end();
        }
    }

    /**
     * Process a completed tile result
     */
    public void processTileResult(TileResult result) {
        Span span = tracer.spanBuilder("OrchestrationService.processTileResult")
                .setParent(Context.current())
                .setAttribute("service.name", "otelbrot-orchestrator")
                .setAttribute("job.id", result.getJobId())
                .setAttribute("tile.id", result.getTileId())
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            logger.info("Processing tile result for job: {}, tile: {}", 
                result.getJobId(), result.getTileId());
            
            // Save the tile result
            tileRepository.saveTileResult(result);
            
            // Update job progress
            String jobId = result.getJobId();
            jobRepository.incrementCompletedTiles(jobId);
            
            // Decrement active worker count and clean up the K8s job if needed
            decrementActiveWorkerCount(result.getJobId(), result.getTileId());
            
            // Get updated job state
            Optional<FractalJob> jobOpt = jobRepository.findById(jobId);
            if (jobOpt.isPresent()) {
                FractalJob job = jobOpt.get();
                
                // Update job status if needed
                if (job.getCompletedTiles() == 1 && job.getStatus() == JobStatus.PROCESSING) {
                    // First tile completed - set to preview ready
                    job.setStatus(JobStatus.PREVIEW_READY);
                    jobRepository.save(job);
                    span.setAttribute("job.status", "PREVIEW_READY");
                } else if (job.getCompletedTiles() == job.getTotalTiles() && job.getTotalTiles() > 0) {
                    // All tiles completed - mark job as completed
                    job.setStatus(JobStatus.COMPLETED);
                    jobRepository.save(job);
                    span.setAttribute("job.status", "COMPLETED");
                    span.setAttribute("job.tiles.total", job.getTotalTiles());
                    logger.info("Job {} is now complete. All {} tiles received.", jobId, job.getTotalTiles());
                    
                    // Clean up all Kubernetes jobs for this completed job
                    if (cleanupCompletedJobs) {
                        cleanupKubernetesJobs(jobId);
                    }
                }
                
                // Calculate elapsed time
                long elapsedTimeMs = ChronoUnit.MILLIS.between(job.getCreatedAt(), ZonedDateTime.now());
                span.setAttribute("job.elapsed_ms", elapsedTimeMs);
                
                // Send WebSocket updates - send tile update first, then progress
                webSocketService.sendTileUpdate(result);
                webSocketService.sendProgressUpdate(job, elapsedTimeMs);
            }
        } finally {
            span.end();
        }
    }
    
    /**
     * Clean up Kubernetes jobs for a completed fractal job
     */
    private void cleanupKubernetesJobs(String jobId) {
        try {
            logger.info("Cleaning up Kubernetes jobs for completed job: {}", jobId);
            
            // Delete all Kubernetes jobs with this fractal job ID
            kubernetesClient.batch().v1().jobs()
                    .inNamespace(namespace)
                    .withLabel("fractal-job-id", jobId)
                    .delete();
                    
            logger.info("Successfully cleaned up Kubernetes jobs for job: {}", jobId);
        } catch (Exception e) {
            logger.warn("Error cleaning up Kubernetes jobs: {}", e.getMessage());
        }
    }
    
    /**
     * Decrement the active worker count when a worker completes its task
     * and clean up the Kubernetes job if enabled
     */
    private synchronized void decrementActiveWorkerCount(String jobId, String tileId) {
        Span span = tracer.spanBuilder("OrchestrationService.decrementActiveWorkerCount")
                .setParent(Context.current())
                .setAttribute("service.name", "otelbrot-orchestrator")
                .setAttribute("job.id", jobId)
                .setAttribute("tile.id", tileId)
                .startSpan();
                
        try (Scope scope = span.makeCurrent()) {
            activeWorkerCount = Math.max(0, activeWorkerCount - 1);
            span.setAttribute("workers.active", activeWorkerCount);
            logger.debug("Decremented active worker count to {} after completion of tile {}", activeWorkerCount, tileId);
            
            // Clean up the individual Kubernetes job if enabled
            if (cleanupCompletedJobs) {
                Span k8sSpan = tracer.spanBuilder("Kubernetes.cleanupJob")
                        .setParent(Context.current())
                        .setAttribute("service.name", "otelbrot-orchestrator")
                        .setAttribute("kubernetes.namespace", namespace)
                        .setAttribute("kubernetes.job.id", jobId)
                        .setAttribute("kubernetes.tile.id", tileId)
                        .startSpan();
                        
                try (Scope k8sScope = k8sSpan.makeCurrent()) {
                    kubernetesClient.batch().v1().jobs()
                            .inNamespace(namespace)
                            .withLabel("fractal-job-id", jobId)
                            .withLabel("fractal-tile-id", tileId)
                            .delete();
                            
                    logger.debug("Cleaned up Kubernetes job for tile: {}", tileId);
                    k8sSpan.addEvent("Kubernetes job deleted");
                } catch (Exception e) {
                    logger.warn("Error cleaning up Kubernetes job for tile {}: {}", tileId, e.getMessage());
                    k8sSpan.recordException(e);
                    k8sSpan.setStatus(StatusCode.ERROR, e.getMessage());
                } finally {
                    k8sSpan.end();
                }
            }
            
            // Process any queued jobs now that we have capacity
            // Capture the current context to use in the queue processing
            Context currentContext = Context.current();
            
            // Pass the context to processJobQueue to maintain the trace
            try (Scope ignored = currentContext.makeCurrent()) {
                processJobQueue();
            }
            
            span.addEvent("Worker count decremented and queue processed");
        } finally {
            span.end();
        }
    }

    /**
     * Get job status
     */
    public Optional<FractalJob> getJobStatus(String jobId) {
        return jobRepository.findById(jobId);
    }

    /**
     * Cancel a job
     */
    public boolean cancelJob(String jobId) {
        Span span = tracer.spanBuilder("OrchestrationService.cancelJob").startSpan();
        try {
            logger.info("Cancelling job: {}", jobId);
            
            // Update job status
            jobRepository.updateStatus(jobId, JobStatus.CANCELLED);
            
            // Delete Kubernetes jobs
            kubernetesClient.batch().v1().jobs()
                    .inNamespace(namespace)
                    .withLabel("fractal-job-id", jobId)
                    .delete();
            
            logger.info("Deleted Kubernetes jobs for jobId {}", jobId);
            return true;
        } finally {
            span.end();
        }
    }

    /**
     * Create a low-resolution preview job
     */
    private void createPreviewJob(FractalJob job) {
        Span span = tracer.spanBuilder("OrchestrationService.createPreviewJob")
                .setParent(Context.current())
                .setAttribute("service.name", "otelbrot-orchestrator")
                .setAttribute("job.id", job.getJobId())
                .startSpan();
                
        try (Scope scope = span.makeCurrent()) {
            // Create the preview tile spec
            TileSpec previewSpec = new TileSpec.Builder()
                    .jobId(job.getJobId())
                    .tileId("preview")
                    .xMin(job.getCenterX() - job.getZoom())
                    .yMin(job.getCenterY() - job.getZoom())
                    .xMax(job.getCenterX() + job.getZoom())
                    .yMax(job.getCenterY() + job.getZoom())
                    .width(Math.min(job.getWidth(), 256)) // Low resolution for preview
                    .height(Math.min(job.getHeight(), 256))
                    .maxIterations(Math.min(job.getMaxIterations(), 100)) // Fewer iterations for speed
                    .colorScheme(job.getColorScheme())
                    .pixelStartX(0)
                    .pixelStartY(0)
                    .build();
            span.setAttribute("tile.spec", previewSpec.toString());
            span.setAttribute("tile.id", "preview");
            span.setAttribute("tile.priority", "high");
            
            // Create and launch a worker pod
            createWorkerJob(previewSpec, true);
            
            // Update job status and count
            jobRepository.updateStatus(job.getJobId(), JobStatus.PROCESSING);
            jobRepository.updateProgress(job.getJobId(), 0, 1 + calculateTileCount(job));
            
            logger.info("Created preview job for fractal job: {}", job.getJobId());
            span.addEvent("Preview job created");
        } finally {
            span.end();
        }
    }

    /**
     * Create detailed tile jobs
     */
    private void createDetailJobs(FractalJob job) {
        Span span = tracer.spanBuilder("OrchestrationService.createDetailJobs")
                .setParent(Context.current())
                .setAttribute("service.name", "otelbrot-orchestrator")
                .setAttribute("job.id", job.getJobId())
                .startSpan();
                
        try (Scope scope = span.makeCurrent()) {
            // Partition the rendering area into tiles
            List<TileSpec> tiles = partitionIntoTiles(job);
            span.setAttribute("tiles.count", tiles.size());
            
            // Create a worker pod for each tile
            for (TileSpec tile : tiles) {
                Span tileSpan = tracer.spanBuilder("OrchestrationService.createDetailTile")
                        .setParent(Context.current())
                        .setAttribute("service.name", "otelbrot-orchestrator")
                        .setAttribute("job.id", job.getJobId())
                        .setAttribute("tile.id", tile.getTileId())
                        .startSpan();
                        
                try (Scope tileScope = tileSpan.makeCurrent()) {
                    createWorkerJob(tile, false);
                    tileSpan.addEvent("Detail tile job scheduled");
                } finally {
                    tileSpan.end();
                }
            }
            
            logger.info("Created {} detail jobs for fractal job: {}", tiles.size(), job.getJobId());
            span.addEvent("All detail jobs created");
        } finally {
            span.end();
        }
    }

    /**
     * Create a Kubernetes job for a worker, with concurrency control
     */
    private synchronized void createWorkerJob(TileSpec tileSpec, boolean isPriority) {
        // First check if we have a current span
        Span currentSpan = Span.current();
        Context currentContext = Context.current();
        
        // Log detailed information about the current context to debug trace propagation
        if (currentSpan != null && !currentSpan.equals(Span.getInvalid())) {
            logger.info("Creating worker job with current span: traceId={}, spanId={}", 
                currentSpan.getSpanContext().getTraceId(),
                currentSpan.getSpanContext().getSpanId());
        } else {
            logger.warn("No current span found when creating worker job");
        }
        
        // Create a span for this operation, ensuring we use the current context
        Span span = tracer.spanBuilder("OrchestrationService.createWorkerJob")
                .setParent(currentContext)
                .setAttribute("service.name", "otelbrot-orchestrator")
                .setAttribute("job.id", tileSpec.getJobId())
                .setAttribute("tile.id", tileSpec.getTileId())
                .setAttribute("tile.priority", isPriority ? "high" : "normal")
                .startSpan();
                
        logger.info("Created worker job span: traceId={}, spanId={}", 
            span.getSpanContext().getTraceId(), 
            span.getSpanContext().getSpanId());
        try {
            // Check if we're at maximum worker capacity and this is not a priority job
            int maxWorkers = Math.min(getAvailableCores(), maxConcurrentWorkers);
            
            if (activeWorkerCount >= maxWorkers && !isPriority) {
                logger.info("Deferring worker job for tile {} due to reaching max concurrency ({}/{})", 
                    tileSpec.getTileId(), activeWorkerCount, maxWorkers);
                
                // For non-priority jobs, defer creation and return - they'll be created
                // as workers complete and capacity becomes available
                addToJobQueue(tileSpec);
                return;
            }
            
            // Generate unique name for the job
            String jobId = tileSpec.getJobId();
            String tileId = tileSpec.getTileId();
            // Safely handle strings that might be shorter than 8 characters
            String jobIdPrefix = jobId.length() >= 8 ? jobId.substring(0, 8) : jobId;
            String tileIdPrefix = tileId.length() >= 8 ? tileId.substring(0, 8) : tileId;
            String name = "fractal-" + jobIdPrefix + "-" + tileIdPrefix;
            
            // Create labels
            Map<String, String> labels = new HashMap<>();
            labels.put("app", "otelbrot-worker");
            labels.put("fractal-job-id", jobId);
            labels.put("fractal-tile-id", tileId);
            labels.put("priority", isPriority ? "high" : "normal");

            Map<String, String> annotations = new HashMap<>();
            annotations.put("instrumentation.opentelemetry.io/inject-java", enableAgent);
            logger.info("Setting OpenTelemetry annotations: {}", annotations);
            
            // Add a TTL for automatic cleanup if we're not manually cleaning up
            Integer ttlSecondsAfterFinished = cleanupCompletedJobs ? null : 300; // 5 minutes TTL
            
            // Create the job
            Job job = new JobBuilder()
                    .withNewMetadata()
                        .withName(name)
                        .withLabels(labels)
                        .withAnnotations(annotations)
                    .endMetadata()
                    .withNewSpec()
                        .withBackoffLimit(2)
                        .withTtlSecondsAfterFinished(ttlSecondsAfterFinished)
                        .withNewTemplate()
                            .withNewMetadata()
                                .withLabels(labels)
                                .withAnnotations(annotations) 
                            .endMetadata()
                            .withNewSpec()
                                .withRestartPolicy("Never")
                                .addNewContainer()
                                    .withName("worker")
                                    .withImage(workerImage)
                                    .withImagePullPolicy("Always")
                                    .addNewEnv()
                                        .withName("TILE_SPEC_JOB_ID")
                                        .withValue(tileSpec.getJobId())
                                    .endEnv()
                                    .addNewEnv()
                                        .withName("TILE_SPEC_TILE_ID")
                                        .withValue(tileSpec.getTileId())
                                    .endEnv()
                                    .addNewEnv()
                                        .withName("TILE_SPEC_X_MIN")
                                        .withValue(String.valueOf(tileSpec.getXMin()))
                                    .endEnv()
                                    .addNewEnv()
                                        .withName("TILE_SPEC_Y_MIN")
                                        .withValue(String.valueOf(tileSpec.getYMin()))
                                    .endEnv()
                                    .addNewEnv()
                                        .withName("TILE_SPEC_X_MAX")
                                        .withValue(String.valueOf(tileSpec.getXMax()))
                                    .endEnv()
                                    .addNewEnv()
                                        .withName("TILE_SPEC_Y_MAX")
                                        .withValue(String.valueOf(tileSpec.getYMax()))
                                    .endEnv()
                                    .addNewEnv()
                                        .withName("TILE_SPEC_WIDTH")
                                        .withValue(String.valueOf(tileSpec.getWidth()))
                                    .endEnv()
                                    .addNewEnv()
                                        .withName("TILE_SPEC_HEIGHT")
                                        .withValue(String.valueOf(tileSpec.getHeight()))
                                    .endEnv()
                                    .addNewEnv()
                                        .withName("TILE_SPEC_MAX_ITERATIONS")
                                        .withValue(String.valueOf(tileSpec.getMaxIterations()))
                                    .endEnv()
                                    .addNewEnv()
                                        .withName("TILE_SPEC_COLOR_SCHEME")
                                        .withValue(tileSpec.getColorScheme())
                                    .endEnv()
                                    .addNewEnv()
                                        .withName("TILE_SPEC_PIXEL_START_X")
                                        .withValue(String.valueOf(tileSpec.getPixelStartX()))
                                    .endEnv()
                                    .addNewEnv()
                                        .withName("TILE_SPEC_PIXEL_START_Y")
                                        .withValue(String.valueOf(tileSpec.getPixelStartY()))
                                    .endEnv()
                                    
                                    // Add OpenTelemetry trace context to enable distributed tracing
                                    // Create a custom traceparent manually to ensure it has the exact span ID we want
                                    .addNewEnv()
                                        .withName("OTEL_TRACE_PARENT")
                                        .withValue(createTraceParentWithSpanId(span.getSpanContext().getTraceId(), span.getSpanContext().getSpanId()))
                                    .endEnv()
                                    .addNewEnv()
                                        .withName("OTEL_TRACE_STATE")
                                        .withValue("")
                                    .endEnv()
                                    // Also set the TileSpec trace context for backwards compatibility
                                    .addNewEnv()
                                        .withName("TILE_SPEC_TRACE_PARENT")
                                        .withValue(createTraceParentWithSpanId(span.getSpanContext().getTraceId(), span.getSpanContext().getSpanId()))
                                    .endEnv()
                                    .addNewEnv()
                                        .withName("TILE_SPEC_TRACE_STATE")
                                        .withValue("")
                                    .endEnv()
                                    .withNewResources()
                                        .addToRequests("cpu", new io.fabric8.kubernetes.api.model.Quantity(workerCpuRequest))
                                        .addToRequests("memory", new io.fabric8.kubernetes.api.model.Quantity(workerMemoryRequest))
                                        .addToLimits("cpu", new io.fabric8.kubernetes.api.model.Quantity("1000m"))
                                        .addToLimits("memory", new io.fabric8.kubernetes.api.model.Quantity("1024Mi"))
                                    .endResources()
                                .endContainer()
                            .endSpec()
                        .endTemplate()
                    .endSpec()
                    .build();
            
            // Create the job in Kubernetes with correct namespace
            kubernetesClient.batch().v1().jobs().inNamespace(namespace).resource(job).create();
            
            // Increment active worker count
            activeWorkerCount++;
            
            logger.info("Created worker job: {} for tile: {} (active workers: {})", 
                name, tileId, activeWorkerCount);
        } finally {
            span.end();
        }
    }
    
    // Queue for pending jobs waiting for resources
    private final List<TileSpec> pendingJobs = new ArrayList<>();
    
    /**
     * Add a job to the queue for later execution
     */
    private void addToJobQueue(TileSpec tileSpec) {
        pendingJobs.add(tileSpec);
        logger.debug("Added tile {} to pending queue. Queue size: {}", tileSpec.getTileId(), pendingJobs.size());
    }
    
    /**
     * Process queued jobs when resources become available
     * Called when a worker completes
     */
    private synchronized void processJobQueue() {
        Span span = tracer.spanBuilder("OrchestrationService.processJobQueue")
                .setParent(Context.current())
                .setAttribute("service.name", "otelbrot-orchestrator")
                .startSpan();
                
        try (Scope scope = span.makeCurrent()) {
            // Only process if we have pending jobs and capacity
            int maxWorkers = Math.min(getAvailableCores(), maxConcurrentWorkers);
            span.setAttribute("workers.max", maxWorkers);
            span.setAttribute("workers.active", activeWorkerCount);
            span.setAttribute("queue.size", pendingJobs.size());
            
            // Check if we have capacity to process more jobs
            if (!pendingJobs.isEmpty() && activeWorkerCount < maxWorkers) {
                int availableSlots = maxWorkers - activeWorkerCount;
                int jobsToProcess = Math.min(availableSlots, pendingJobs.size());
                span.setAttribute("queue.slots_available", availableSlots);
                span.setAttribute("queue.jobs_to_process", jobsToProcess);
                
                logger.info("Processing {} jobs from queue (queue size: {}, available slots: {})", 
                    jobsToProcess, pendingJobs.size(), availableSlots);
                
                for (int i = 0; i < jobsToProcess; i++) {
                    TileSpec nextJob = pendingJobs.remove(0);
                    
                    Span jobSpan = tracer.spanBuilder("OrchestrationService.processQueuedJob")
                            .setParent(Context.current())
                            .setAttribute("service.name", "otelbrot-orchestrator")
                            .setAttribute("job.id", nextJob.getJobId())
                            .setAttribute("tile.id", nextJob.getTileId())
                            .startSpan();
                            
                    try (Scope jobScope = jobSpan.makeCurrent()) {
                        createWorkerJob(nextJob, false);
                        jobSpan.addEvent("Queued job processed");
                    } finally {
                        jobSpan.end();
                    }
                }
                
                span.addEvent("Queue processing completed");
            } else {
                if (pendingJobs.isEmpty()) {
                    span.addEvent("No pending jobs to process");
                } else {
                    span.addEvent("No available capacity for processing");
                }
            }
        } finally {
            span.end();
        }
    }

    /**
     * Calculate how many tiles a job will require
     */
    private int calculateTileCount(FractalJob job) {
        int tilesX = (int) Math.ceil((double) job.getWidth() / maxTileSize);
        int tilesY = (int) Math.ceil((double) job.getHeight() / maxTileSize);
        return tilesX * tilesY;
    }

    /**
     * Create a W3C trace parent header with specific trace ID and span ID
     * This is a much more direct approach that guarantees the exact span ID is used
     */
    private String createTraceParentWithSpanId(String traceId, String spanId) {
        // Format: 00-[trace-id]-[span-id]-01
        String traceParent = String.format("00-%s-%s-01", traceId, spanId);
        
        logger.info("Created custom trace parent: {}", traceParent);
        logger.info("  - Using trace ID: {}", traceId);
        logger.info("  - Using span ID:  {}", spanId);
        
        return traceParent;
    }
    
    /**
     * Extract trace parent from a specific span - this is a key fix for context propagation
     * Instead of using the current span from the thread, we use the specific span that's starting the job
     */
    private String getTraceParentFromSpan(Span span) {
        if (span == null || span.equals(Span.getInvalid())) {
            logger.warn("Invalid span when extracting trace parent");
            return null;
        }
        
        // Create a context with this specific span (not Context.current())
        Context spanContext = Context.current().with(span);
        
        // Create a carrier to extract the trace context
        Map<String, String> carrier = new HashMap<>();
        
        // Use the span-specific context for trace propagation
        propagator.inject(spanContext, carrier, (c, k, v) -> c.put(k, v));
        
        String traceParent = carrier.get("traceparent");
        
        if (traceParent != null) {
            // Parse and log trace parent details
            try {
                if (traceParent.contains("-")) {
                    String[] parts = traceParent.split("-");
                    if (parts.length >= 4) {
                        String version = parts[0];
                        String traceId = parts[1];
                        String parentSpanId = parts[2];
                        String flags = parts[3];
                        
                        logger.info("Span trace parent: version={}, traceId={}, spanId={}, flags={}",
                            version, traceId, parentSpanId, flags);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to parse traceparent: {}", traceParent, e);
            }
        } else {
            logger.warn("Failed to extract traceparent - distributed tracing will be broken");
        }
        
        return traceParent;
    }
    
    /**
     * Extract trace state from a specific span
     */
    private String getTraceStateFromSpan(Span span) {
        if (span == null || span.equals(Span.getInvalid())) {
            logger.warn("Invalid span when extracting trace state");
            return "";
        }
        
        // Create a context with this specific span
        Context spanContext = Context.current().with(span);
        
        // Create a carrier to extract the trace context
        Map<String, String> carrier = new HashMap<>();
        
        // Use the span-specific context for trace propagation
        propagator.inject(spanContext, carrier, (c, k, v) -> c.put(k, v));
        
        String traceState = carrier.get("tracestate");
        logger.info("Span trace state: {}", traceState);
        
        return traceState != null ? traceState : "";
    }
    
    /**
     * Extract the current trace parent from the active span (legacy method)
     */
    private String getCurrentTraceParent() {
        // Get the current span to ensure proper trace context
        Span currentSpan = Span.current();
        if (currentSpan == null || currentSpan.equals(Span.getInvalid())) {
            logger.warn("No active span when extracting trace parent");
        } else {
            String traceId = currentSpan.getSpanContext().getTraceId();
            String spanId = currentSpan.getSpanContext().getSpanId();
            logger.info("Current span when extracting trace parent: traceId={}, spanId={}", traceId, spanId);
        }
        
        // Create a carrier to extract the trace context
        Map<String, String> carrier = new HashMap<>();
        
        // Use the current context for trace propagation
        propagator.inject(Context.current(), carrier, (c, k, v) -> c.put(k, v));
        
        String traceParent = carrier.get("traceparent");
        logger.info("Extracted traceparent: {}", traceParent);
        
        return traceParent;
    }
    
    /**
     * Extract the current trace state from the active span (legacy method)
     */
    private String getCurrentTraceState() {
        // Create a carrier to extract the trace context
        Map<String, String> carrier = new HashMap<>();
        
        // Use the current context for trace propagation
        propagator.inject(Context.current(), carrier, (c, k, v) -> c.put(k, v));
        
        String traceState = carrier.get("tracestate");
        logger.info("Extracted trace state: {}", traceState);
        
        return traceState;
    }
    
    /**
     * Partition a job into tiles
     */
    private List<TileSpec> partitionIntoTiles(FractalJob job) {
        double centerX = job.getCenterX();
        double centerY = job.getCenterY();
        double zoom = job.getZoom();
        int width = job.getWidth();
        int height = job.getHeight();
        int maxIterations = job.getMaxIterations();
        String colorScheme = job.getColorScheme();
        String jobId = job.getJobId();
        
        // Calculate the boundaries of the view
        double xMin = centerX - zoom;
        double xMax = centerX + zoom;
        double yMin = centerY - zoom;
        double yMax = centerY + zoom;
        
        // Determine how many tiles we need in each dimension
        int tilesX = (int) Math.ceil((double) width / maxTileSize);
        int tilesY = (int) Math.ceil((double) height / maxTileSize);
        
        // Calculate tile size in original pixel space
        int tileWidth = (int) Math.ceil((double) width / tilesX);
        int tileHeight = (int) Math.ceil((double) height / tilesY);
        
        // Calculate tile size in coordinate space
        double tileRangeX = (xMax - xMin) / tilesX;
        double tileRangeY = (yMax - yMin) / tilesY;
        
        List<TileSpec> tiles = new ArrayList<>();
        
        // Create tiles
        for (int ty = 0; ty < tilesY; ty++) {
            for (int tx = 0; tx < tilesX; tx++) {
                // Calculate coordinates for this tile
                double tileXMin = xMin + tx * tileRangeX;
                double tileXMax = tileXMin + tileRangeX;
                double tileYMin = yMin + ty * tileRangeY;
                double tileYMax = tileYMin + tileRangeY;
                
                // Calculate pixel offsets for this tile
                int pixelStartX = tx * tileWidth;
                int pixelStartY = ty * tileHeight;
                
                // Calculate actual pixel dimensions for this tile (handling edge cases)
                int actualTileWidth = Math.min(tileWidth, width - pixelStartX);
                int actualTileHeight = Math.min(tileHeight, height - pixelStartY);
                
                TileSpec tileSpec = new TileSpec.Builder()
                        .jobId(jobId)
                        .tileId(UUID.randomUUID().toString())
                        .xMin(tileXMin)
                        .yMin(tileYMin)
                        .xMax(tileXMax)
                        .yMax(tileYMax)
                        .width(actualTileWidth)
                        .height(actualTileHeight)
                        .maxIterations(maxIterations)
                        .colorScheme(colorScheme)
                        .pixelStartX(pixelStartX)
                        .pixelStartY(pixelStartY)
                        .build();
                
                tiles.add(tileSpec);
            }
        }
        
        return tiles;
    }
}