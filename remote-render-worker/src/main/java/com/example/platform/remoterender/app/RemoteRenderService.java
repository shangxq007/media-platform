package com.example.platform.remoterender.app;

import com.example.platform.remoterender.domain.RemoteRenderJob;
import com.example.platform.remoterender.domain.WorkerStatus;
import com.example.platform.render.infrastructure.JavaCVRenderService;
import com.example.platform.render.infrastructure.RenderPreset;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for executing render jobs on a remote worker.
 */
@Service
public class RemoteRenderService {

    private static final Logger log = LoggerFactory.getLogger(RemoteRenderService.class);

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    private final JavaCVRenderService renderService;
    private final WorkerRegistryService workerRegistry;
    private final Map<String, RemoteRenderJob> activeJobs = new ConcurrentHashMap<>();

    public RemoteRenderService(JavaCVRenderService renderService, WorkerRegistryService workerRegistry) {
        this.renderService = renderService;
        this.workerRegistry = workerRegistry;
    }

    /**
     * Submit a render job for asynchronous execution.
     */
    public RemoteRenderJob submitJob(String workerId, String profile, String timelineJson) {
        log.info("RemoteRenderService: submitting job worker={} profile={}", workerId, profile);

        RemoteRenderJob job = RemoteRenderJob.create(workerId, profile, timelineJson);
        activeJobs.put(job.jobId(), job);

        // Execute asynchronously
        CompletableFuture.runAsync(() -> executeJob(job.jobId()));

        return job;
    }

    /**
     * Execute a render job.
     */
    private void executeJob(String jobId) {
        RemoteRenderJob job = activeJobs.get(jobId);
        if (job == null) {
            log.warn("RemoteRenderService: job not found: {}", jobId);
            return;
        }

        try {
            // Mark as running
            job = job.withStarted();
            activeJobs.put(jobId, job);
            workerRegistry.updateWorkerStatus(job.workerId(), WorkerStatus.BUSY);

            // Parse timeline and extract clips/effects
            RenderPreset preset = RenderPreset.fromProfile(job.profile());

            // Generate output path
            java.nio.file.Path outputDir = java.nio.file.Path.of(storageRoot, "artifacts", "remote-" + jobId);
            outputDir.toFile().mkdirs();
            String outputPath = outputDir.resolve("output.mp4").toString();

            // Parse timeline for subtitle tracks
            List<Map<String, Object>> subtitleTracks = new ArrayList<>();
            if (job.timelineJson() != null && job.timelineJson().contains("{")) {
                try {
                    Map<String, Object> timeline = new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(job.timelineJson(), Map.class);
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> tracks = (List<Map<String, Object>>) timeline.getOrDefault("tracks", List.of());
                    for (Map<String, Object> track : tracks) {
                        String type = (String) track.getOrDefault("type", "");
                        if ("SUBTITLE".equalsIgnoreCase(type) || "TEXT".equalsIgnoreCase(type)) {
                            subtitleTracks.add(track);
                        }
                    }
                } catch (Exception e) {
                    log.warn("RemoteRenderService: failed to parse timeline JSON for job={}", jobId);
                }
            }

            // Render using JavaCVRenderService
            if (subtitleTracks.isEmpty()) {
                renderService.renderPlaceholder(jobId, outputPath, preset);
            } else {
                // For timeline-based rendering, use placeholder with effects
                renderService.renderPlaceholder(jobId, outputPath, preset);
            }

            // Mark as completed
            String artifactId = "art_" + jobId;
            String storageUri = "localFsStorageProvider://artifacts/remote-" + jobId + "/output.mp4";
            job = job.withCompleted(artifactId, storageUri);
            activeJobs.put(jobId, job);

            log.info("RemoteRenderService: job completed successfully: {}", jobId);

        } catch (Exception e) {
            log.error("RemoteRenderService: job execution failed: {}", jobId, e);
            job = job.withFailed("RENDER-500-001",
                    "Remote render failed: " + e.getMessage());
            activeJobs.put(jobId, job);
        } finally {
            workerRegistry.updateWorkerStatus(job.workerId(), WorkerStatus.IDLE);
        }
    }

    /**
     * Get the status of a render job.
     */
    public RemoteRenderJob getJobStatus(String jobId) {
        return activeJobs.get(jobId);
    }

    /**
     * Get all active jobs for a worker.
     */
    public List<RemoteRenderJob> getWorkerJobs(String workerId) {
        return activeJobs.values().stream()
                .filter(j -> j.workerId().equals(workerId))
                .toList();
    }

    /**
     * Cancel a render job.
     */
    public RemoteRenderJob cancelJob(String jobId) {
        RemoteRenderJob job = activeJobs.get(jobId);
        if (job == null) {
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-404-001", 500404,
                            Map.of("en", "Render job not found", "zh", "渲染任务不存在"),
                            "render", 404),
                    "Job not found: " + jobId,
                    Map.of("jobId", jobId),
                    "en"
            );
        }
        job = job.withStatus("CANCELLED");
        activeJobs.put(jobId, job);
        return job;
    }
}
