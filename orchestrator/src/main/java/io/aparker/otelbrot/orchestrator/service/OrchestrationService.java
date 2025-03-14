package io.aparker.otelbrot.orchestrator.service;

import io.aparker.otelbrot.commons.model.TileResult;
import io.aparker.otelbrot.commons.model.TileSpec;
import io.aparker.otelbrot.orchestrator.model.FractalJob;
import io.aparker.otelbrot.orchestrator.model.JobStatus;
import io.aparker.otelbrot.orchestrator.model.RenderRequest;
import io.aparker.otelbrot.orchestrator.repository.JobRepository;
import io.aparker.otelbrot.orchestrator.repository.TileRepository;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for orchestrating fractal rendering jobs
 */
@Service
public class OrchestrationService {

    private static final Logger logger = LoggerFactory.getLogger(
        OrchestrationService.class
    );

    private final KubernetesClient kubernetesClient;
    private final JobRepository jobRepository;
    private final TileRepository tileRepository;
    private final WebSocketService webSocketService;
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

    @Value("${app.worker.image-pull-policy:Never}")
    private String imagePullPolicy;

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
        TextMapPropagator propagator
    ) {
        this.kubernetesClient = kubernetesClient;
        this.jobRepository = jobRepository;
        this.tileRepository = tileRepository;
        this.webSocketService = webSocketService;
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
            logger.warn(
                "Unable to determine available CPU cores, using default value",
                e
            );
            return 2; // Default to 2 cores if we can't determine
        }
    }

    /**
     * Create a new fractal rendering job
     */
    @WithSpan("OrchestrationService.createRenderJob")
    public FractalJob createRenderJob(
        @SpanAttribute("request") RenderRequest request
    ) {
        // Create and save the job
        FractalJob job = FractalJob.fromRenderRequest(request);
        jobRepository.save(job);

        // Set current span attributes
        Span.current().setAttribute("job.id", job.getJobId());
        logger.info("Created new fractal job: {}", job.getJobId());

        // Initialize preview job
        createPreviewJob(job);

        // Initialize detailed tiles
        createDetailJobs(job);

        // Send initial progress update
        webSocketService.sendProgressUpdate(job, 0);

        return job;
    }

    /**
     * Process a completed tile result
     */
    @WithSpan("OrchestrationService.processTileResult")
    public void processTileResult(
        @SpanAttribute("job.id") String jobId,
        @SpanAttribute("tile.id") String tileId,
        TileResult result
    ) {
        logger.info(
            "Processing tile result for job: {}, tile: {}",
            result.getJobId(),
            result.getTileId()
        );

        // Save the tile result
        tileRepository.saveTileResult(result);

        // Update job progress
        jobRepository.incrementCompletedTiles(jobId);

        // Decrement active worker count and clean up the K8s job if needed
        decrementActiveWorkerCount(jobId, tileId);

        // Get updated job state
        Optional<FractalJob> jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isPresent()) {
            FractalJob job = jobOpt.get();

            // Update job status if needed
            if (
                job.getCompletedTiles() == 1 &&
                job.getStatus() == JobStatus.PROCESSING
            ) {
                // First tile completed - set to preview ready
                job.setStatus(JobStatus.PREVIEW_READY);
                jobRepository.save(job);
                Span.current().setAttribute("job.status", "PREVIEW_READY");
            } else if (
                job.getCompletedTiles() == job.getTotalTiles() &&
                job.getTotalTiles() > 0
            ) {
                // All tiles completed - mark job as completed
                job.setStatus(JobStatus.COMPLETED);
                jobRepository.save(job);
                Span.current().setAttribute("job.status", "COMPLETED");
                Span.current()
                    .setAttribute("job.tiles.total", job.getTotalTiles());
                logger.info(
                    "Job {} is now complete. All {} tiles received.",
                    jobId,
                    job.getTotalTiles()
                );

                // Clean up all Kubernetes jobs for this completed job
                if (cleanupCompletedJobs) {
                    cleanupKubernetesJobs(jobId);
                }
            }

            // Calculate elapsed time
            long elapsedTimeMs = ChronoUnit.MILLIS.between(
                job.getCreatedAt(),
                ZonedDateTime.now()
            );
            Span.current().setAttribute("job.elapsed_ms", elapsedTimeMs);

            // Send WebSocket updates - send tile update first, then progress
            webSocketService.sendTileUpdate(result);
            webSocketService.sendProgressUpdate(job, elapsedTimeMs);
        }
    }

    /**
     * Clean up Kubernetes jobs for a completed fractal job
     */
    @WithSpan("OrchestrationService.cleanupKubernetesJobs")
    private void cleanupKubernetesJobs(@SpanAttribute("job.id") String jobId) {
        try {
            logger.info(
                "Cleaning up Kubernetes jobs for completed job: {}",
                jobId
            );

            // Delete all Kubernetes jobs with this fractal job ID
            kubernetesClient
                .batch()
                .v1()
                .jobs()
                .inNamespace(namespace)
                .withLabel("fractal-job-id", jobId)
                .delete();

            logger.info(
                "Successfully cleaned up Kubernetes jobs for job: {}",
                jobId
            );
        } catch (Exception e) {
            logger.warn(
                "Error cleaning up Kubernetes jobs: {}",
                e.getMessage()
            );
            Span.current().recordException(e);
            Span.current().setStatus(StatusCode.ERROR, e.getMessage());
        }
    }

    /**
     * Decrement the active worker count when a worker completes its task
     * and clean up the Kubernetes job if enabled
     */
    @WithSpan("OrchestrationService.decrementActiveWorkerCount")
    private synchronized void decrementActiveWorkerCount(
        @SpanAttribute("job.id") String jobId,
        @SpanAttribute("tile.id") String tileId
    ) {
        activeWorkerCount = Math.max(0, activeWorkerCount - 1);
        Span.current().setAttribute("workers.active", activeWorkerCount);
        logger.debug(
            "Decremented active worker count to {} after completion of tile {}",
            activeWorkerCount,
            tileId
        );

        // Clean up the individual Kubernetes job if enabled
        if (cleanupCompletedJobs) {
            cleanupTileJob(jobId, tileId);
        }

        // Process any queued jobs now that we have capacity
        processJobQueue();

        Span.current().addEvent("Worker count decremented and queue processed");
    }

    /**
     * Clean up a specific Kubernetes job for a tile
     */
    @WithSpan("Kubernetes.cleanupJob")
    private void cleanupTileJob(
        @SpanAttribute("kubernetes.job.id") String jobId,
        @SpanAttribute("kubernetes.tile.id") String tileId
    ) {
        try {
            kubernetesClient
                .batch()
                .v1()
                .jobs()
                .inNamespace(namespace)
                .withLabel("fractal-job-id", jobId)
                .withLabel("fractal-tile-id", tileId)
                .delete();

            logger.debug("Cleaned up Kubernetes job for tile: {}", tileId);
            Span.current().addEvent("Kubernetes job deleted");
        } catch (Exception e) {
            logger.warn(
                "Error cleaning up Kubernetes job for tile {}: {}",
                tileId,
                e.getMessage()
            );
            Span.current().recordException(e);
            Span.current().setStatus(StatusCode.ERROR, e.getMessage());
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
    @WithSpan("OrchestrationService.cancelJob")
    public boolean cancelJob(@SpanAttribute("job.id") String jobId) {
        logger.info("Cancelling job: {}", jobId);

        // Update job status
        jobRepository.updateStatus(jobId, JobStatus.CANCELLED);

        // Delete Kubernetes jobs
        kubernetesClient
            .batch()
            .v1()
            .jobs()
            .inNamespace(namespace)
            .withLabel("fractal-job-id", jobId)
            .delete();

        logger.info("Deleted Kubernetes jobs for jobId {}", jobId);
        return true;
    }

    /**
     * Create a low-resolution preview job
     */
    @WithSpan("OrchestrationService.createPreviewJob")
    private void createPreviewJob(
        @SpanAttribute("job.id") String jobId,
        FractalJob job
    ) {
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
        Span.current().setAttribute("tile.spec", previewSpec.toString());
        Span.current().setAttribute("tile.id", "preview");
        Span.current().setAttribute("tile.priority", "high");

        // Create and launch a worker pod
        createWorkerJob(previewSpec, true);

        // Update job status and count
        jobRepository.updateStatus(job.getJobId(), JobStatus.PROCESSING);
        jobRepository.updateProgress(
            job.getJobId(),
            0,
            1 + calculateTileCount(job)
        );

        logger.info("Created preview job for fractal job: {}", job.getJobId());
        Span.current().addEvent("Preview job created");
    }

    /**
     * Create a low-resolution preview job - convenience method
     */
    private void createPreviewJob(FractalJob job) {
        createPreviewJob(job.getJobId(), job);
    }

    /**
     * Create detailed tile jobs
     */
    @WithSpan("OrchestrationService.createDetailJobs")
    private void createDetailJobs(
        @SpanAttribute("job.id") String jobId,
        FractalJob job
    ) {
        // Partition the rendering area into tiles
        List<TileSpec> tiles = partitionIntoTiles(job);
        Span.current().setAttribute("tiles.count", tiles.size());

        // Create a worker pod for each tile
        for (TileSpec tile : tiles) {
            createDetailTile(job.getJobId(), tile);
        }

        logger.info(
            "Created {} detail jobs for fractal job: {}",
            tiles.size(),
            job.getJobId()
        );
        Span.current().addEvent("All detail jobs created");
    }

    /**
     * Create a single detail tile job
     */
    @WithSpan("OrchestrationService.createDetailTile")
    private void createDetailTile(
        @SpanAttribute("job.id") String jobId,
        @SpanAttribute("tile.id") String tileId,
        TileSpec tile
    ) {
        createWorkerJob(tile, false);
        Span.current().addEvent("Detail tile job scheduled");
    }

    /**
     * Create a single detail tile job - convenience method
     */
    private void createDetailTile(String jobId, TileSpec tile) {
        createDetailTile(jobId, tile.getTileId(), tile);
    }

    /**
     * Create detailed tile jobs - convenience method
     */
    private void createDetailJobs(FractalJob job) {
        createDetailJobs(job.getJobId(), job);
    }

    /**
     * Create a Kubernetes job for a worker, with concurrency control
     */
    @WithSpan("OrchestrationService.createWorkerJob")
    private synchronized void createWorkerJob(
        @SpanAttribute("job.id") String jobId,
        @SpanAttribute("tile.id") String tileId,
        @SpanAttribute("tile.priority") boolean isPriority,
        TileSpec tileSpec
    ) {
        // Check if we're at maximum worker capacity and this is not a priority job
        int maxWorkers = Math.min(getAvailableCores(), maxConcurrentWorkers);

        if (activeWorkerCount >= maxWorkers && !isPriority) {
            logger.info(
                "Deferring worker job for tile {} due to reaching max concurrency ({}/{})",
                tileSpec.getTileId(),
                activeWorkerCount,
                maxWorkers
            );

            // For non-priority jobs, defer creation and return - they'll be created
            // as workers complete and capacity becomes available
            addToJobQueue(tileSpec);
            return;
        }

        // Generate unique name for the job
        // Safely handle strings that might be shorter than 8 characters
        String jobIdPrefix = jobId.length() >= 8
            ? jobId.substring(0, 8)
            : jobId;
        String tileIdPrefix = tileId.length() >= 8
            ? tileId.substring(0, 8)
            : tileId;
        String name = "fractal-" + jobIdPrefix + "-" + tileIdPrefix;

        // Create labels
        Map<String, String> labels = new HashMap<>();
        labels.put("app", "otelbrot-worker");
        labels.put("fractal-job-id", jobId);
        labels.put("fractal-tile-id", tileId);
        labels.put("priority", isPriority ? "high" : "normal");

        // Add a TTL for automatic cleanup if we're not manually cleaning up
        Integer ttlSecondsAfterFinished = cleanupCompletedJobs ? null : 300; // 5 minutes TTL

        // Create the job
        Job job = new JobBuilder()
            .withNewMetadata()
            .withName(name)
            .withLabels(labels)
            .endMetadata()
            .withNewSpec()
            .withBackoffLimit(2)
            .withTtlSecondsAfterFinished(ttlSecondsAfterFinished)
            .withNewTemplate()
            .withNewMetadata()
            .withLabels(labels)
            // No OpenTelemetry annotation - Go instrumentation is built-in
            .endMetadata()
            .withNewSpec()
            .withRestartPolicy("Never")
            // Add the OpenTelemetry config volume to the pod
            .addNewVolume()
            .withName("go-worker-otel-config")
            .withNewConfigMap()
            .withName("go-worker-otel-config")
            .endConfigMap()
            .endVolume()
            .addNewContainer()
            .withName("worker")
            .withImage(workerImage)
            .withImagePullPolicy(imagePullPolicy)
            .addNewEnv()
            .withName("ORCHESTRATOR_URL")
            .withValue("http://orchestrator.otelbrot.svc.cluster.local:8080")
            .endEnv()
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
            // Add OpenTelemetry trace context using W3C standard environment variables
            .addNewEnv()
            .withName("TRACEPARENT")
            .withValue(getTraceparent())
            .endEnv()
            .addNewEnv()
            .withName("TRACESTATE")
            .withValue(getCurrentTraceState())
            .endEnv()
            // Mount the OpenTelemetry config
            .addNewVolumeMount()
            .withName("go-worker-otel-config")
            .withMountPath("/app/config")
            .withReadOnly(true)
            .endVolumeMount()
            // Set environment variable for OpenTelemetry config file
            .addNewEnv()
            .withName("OTEL_CONFIG_FILE")
            .withValue("/app/config/otel-config.yaml")
            .endEnv()
            .withNewResources()
            .addToRequests(
                "cpu",
                new io.fabric8.kubernetes.api.model.Quantity(workerCpuRequest)
            )
            .addToRequests(
                "memory",
                new io.fabric8.kubernetes.api.model.Quantity(
                    workerMemoryRequest
                )
            )
            .addToLimits(
                "cpu",
                new io.fabric8.kubernetes.api.model.Quantity("1000m")
            )
            .addToLimits(
                "memory",
                new io.fabric8.kubernetes.api.model.Quantity("1024Mi")
            )
            .endResources()
            .endContainer()
            .endSpec()
            .endTemplate()
            .endSpec()
            .build();

        // Create the job in Kubernetes with correct namespace
        kubernetesClient
            .batch()
            .v1()
            .jobs()
            .inNamespace(namespace)
            .resource(job)
            .create();

        // Increment active worker count
        activeWorkerCount++;

        logger.info(
            "Created worker job: {} for tile: {} (active workers: {})",
            name,
            tileId,
            activeWorkerCount
        );
    }

    /**
     * Create a Kubernetes job for a worker - convenience method
     */
    private void createWorkerJob(TileSpec tileSpec, boolean isPriority) {
        createWorkerJob(
            tileSpec.getJobId(),
            tileSpec.getTileId(),
            isPriority,
            tileSpec
        );
    }

    // Queue for pending jobs waiting for resources
    private final List<TileSpec> pendingJobs = new ArrayList<>();

    /**
     * Add a job to the queue for later execution
     */
    private void addToJobQueue(TileSpec tileSpec) {
        pendingJobs.add(tileSpec);
        logger.debug(
            "Added tile {} to pending queue. Queue size: {}",
            tileSpec.getTileId(),
            pendingJobs.size()
        );
    }

    /**
     * Process queued jobs when resources become available
     * Called when a worker completes
     */
    @WithSpan("OrchestrationService.processJobQueue")
    private synchronized void processJobQueue() {
        // Only process if we have pending jobs and capacity
        int maxWorkers = Math.min(getAvailableCores(), maxConcurrentWorkers);
        Span.current().setAttribute("workers.max", maxWorkers);
        Span.current().setAttribute("workers.active", activeWorkerCount);
        Span.current().setAttribute("queue.size", pendingJobs.size());

        // Check if we have capacity to process more jobs
        if (!pendingJobs.isEmpty() && activeWorkerCount < maxWorkers) {
            int availableSlots = maxWorkers - activeWorkerCount;
            int jobsToProcess = Math.min(availableSlots, pendingJobs.size());
            Span.current()
                .setAttribute("queue.slots_available", availableSlots);
            Span.current().setAttribute("queue.jobs_to_process", jobsToProcess);

            logger.info(
                "Processing {} jobs from queue (queue size: {}, available slots: {})",
                jobsToProcess,
                pendingJobs.size(),
                availableSlots
            );

            for (int i = 0; i < jobsToProcess; i++) {
                TileSpec nextJob = pendingJobs.remove(0);
                processQueuedJob(nextJob);
            }

            Span.current().addEvent("Queue processing completed");
        } else {
            if (pendingJobs.isEmpty()) {
                Span.current().addEvent("No pending jobs to process");
            } else {
                Span.current().addEvent("No available capacity for processing");
            }
        }
    }

    /**
     * Process a single queued job
     */
    @WithSpan("OrchestrationService.processQueuedJob")
    private void processQueuedJob(
        @SpanAttribute("job.id") String jobId,
        @SpanAttribute("tile.id") String tileId,
        TileSpec tileSpec
    ) {
        createWorkerJob(tileSpec, false);
        Span.current().addEvent("Queued job processed");
    }

    /**
     * Process a single queued job - convenience method
     */
    private void processQueuedJob(TileSpec tileSpec) {
        processQueuedJob(tileSpec.getJobId(), tileSpec.getTileId(), tileSpec);
    }

    /**
     * Calculate how many tiles a job will require
     */
    private int calculateTileCount(FractalJob job) {
        // Use the job's tile size if specified, otherwise fall back to maxTileSize
        int tileSize = (job.getTileSize() != null && job.getTileSize() > 0)
            ? job.getTileSize()
            : maxTileSize;

        // Validate tile size is within bounds
        tileSize = Math.min(Math.max(tileSize, 64), 512);

        int tilesX = (int) Math.ceil((double) job.getWidth() / tileSize);
        int tilesY = (int) Math.ceil((double) job.getHeight() / tileSize);
        return tilesX * tilesY;
    }

    /**
     * Get the W3C traceparent from the current context
     */
    private String getTraceparent() {
        // Create a carrier for context propagation
        Map<String, String> carrier = new HashMap<>();

        try {
            // Get the current span to check if it's valid
            Span currentSpan = Span.current();
            if (currentSpan == null || currentSpan.equals(Span.getInvalid())) {
                logger.warn(
                    "No valid span found when getting trace context - creating a new one"
                );

                // When no current span exists, create a default traceparent manually
                // Format: 00-traceId-spanId-01 (sampled)
                String traceId = generateRandomHexString(32); // 16 bytes trace ID
                String spanId = generateRandomHexString(16); // 8 bytes span ID
                return "00-" + traceId + "-" + spanId + "-01";
            } else {
                logger.info(
                    "Current span to extract context from: traceId={}, spanId={}",
                    currentSpan.getSpanContext().getTraceId(),
                    currentSpan.getSpanContext().getSpanId()
                );
            }

            // Use the standard OTel propagator to inject the current context
            Context currentContext = Context.current();
            propagator.inject(currentContext, carrier, (c, k, v) -> c.put(k, v)
            );

            String traceparent = carrier.getOrDefault("traceparent", "");
            if (traceparent.isEmpty()) {
                logger.warn(
                    "No traceparent found in carrier after injection! Creating a fallback value"
                );

                // Fallback - get trace and span ID directly from current span
                String traceId = currentSpan.getSpanContext().getTraceId();
                String spanId = currentSpan.getSpanContext().getSpanId();
                boolean sampled = currentSpan.getSpanContext().isSampled();

                // Create a valid W3C traceparent
                traceparent =
                    "00-" +
                    traceId +
                    "-" +
                    spanId +
                    "-" +
                    (sampled ? "01" : "00");
                logger.info("Created fallback traceparent: {}", traceparent);
            } else {
                logger.info(
                    "Successfully extracted traceparent: {}",
                    traceparent
                );
            }

            // Log what we're injecting for debugging
            logger.debug("Full carrier contents: {}", carrier);

            // Return the traceparent value (standard W3C key)
            return traceparent;
        } catch (Exception e) {
            logger.error("Error getting traceparent", e);
            return "";
        }
    }

    /**
     * Generate a random hex string of specified length
     */
    private String generateRandomHexString(int length) {
        // Generate random bytes
        byte[] randomBytes = new byte[length / 2];
        new java.util.Random().nextBytes(randomBytes);

        // Convert to hex
        StringBuilder sb = new StringBuilder();
        for (byte b : randomBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Extract trace parent from a specific span using the OpenTelemetry API
     * Creates standard W3C traceparent format from the span context
     */
    private String getTraceParentFromSpan(Span span) {
        if (span == null || span.equals(Span.getInvalid())) {
            logger.warn("Invalid span when extracting trace parent");
            return "";
        }

        // Create a carrier to extract the trace context
        Map<String, String> carrier = new HashMap<>();

        // Create a context with this specific span and inject it into the carrier
        Context spanContext = Context.current().with(span);
        propagator.inject(spanContext, carrier, (c, k, v) -> c.put(k, v));

        String traceparent = carrier.get("traceparent");
        logger.debug("Extracted traceparent: {}", traceparent);

        return traceparent != null ? traceparent : "";
    }

    /**
     * Extract trace state from a specific span using the OpenTelemetry API
     */
    private String getTraceStateFromSpan(Span span) {
        if (span == null || span.equals(Span.getInvalid())) {
            logger.warn("Invalid span when extracting trace state");
            return "";
        }

        // Create a carrier to extract the trace context
        Map<String, String> carrier = new HashMap<>();

        // Create a context with this specific span and inject it into the carrier
        Context spanContext = Context.current().with(span);
        propagator.inject(spanContext, carrier, (c, k, v) -> c.put(k, v));

        String tracestate = carrier.get("tracestate");
        logger.debug("Extracted tracestate: {}", tracestate);

        return tracestate != null ? tracestate : "";
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
            logger.info(
                "Current span when extracting trace parent: traceId={}, spanId={}",
                traceId,
                spanId
            );
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

        // Get tile size from job or use default - use same logic as calculateTileCount method
        Integer requestedTileSize = job.getTileSize();
        int tileSize = (requestedTileSize != null && requestedTileSize > 0)
            ? requestedTileSize
            : maxTileSize;

        // Validate tile size is within bounds
        tileSize = Math.min(Math.max(tileSize, 64), 512);

        // Calculate the boundaries of the view
        double xMin = centerX - zoom;
        double xMax = centerX + zoom;
        double yMin = centerY - zoom;
        double yMax = centerY + zoom;

        // Determine how many tiles we need in each dimension
        int tilesX = (int) Math.ceil((double) width / tileSize);
        int tilesY = (int) Math.ceil((double) height / tileSize);

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
                int actualTileHeight = Math.min(
                    tileHeight,
                    height - pixelStartY
                );

                // Create tile with trace propagation data
                TileSpec.Builder tileSpecBuilder = new TileSpec.Builder()
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
                    .pixelStartY(pixelStartY);

                // Not setting trace context in TileSpec anymore - using environment variables
                // Context propagation happens when the Kubernetes job is created

                TileSpec tileSpec = tileSpecBuilder.build();

                tiles.add(tileSpec);
            }
        }

        return tiles;
    }
}
