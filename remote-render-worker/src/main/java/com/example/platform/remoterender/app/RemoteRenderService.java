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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RemoteRenderService {

    private static final Logger log = LoggerFactory.getLogger(RemoteRenderService.class);

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    @Value("${app.remote-worker.callback-url:}")
    private String callbackUrl;

    private final JavaCVRenderService renderService;
    private final WorkerRegistryService workerRegistry;
    private final Map<String, RemoteRenderJob> activeJobs = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public RemoteRenderService(JavaCVRenderService renderService, WorkerRegistryService workerRegistry) {
        this.renderService = renderService;
        this.workerRegistry = workerRegistry;
    }

    public RemoteRenderJob submitJob(String workerId, String profile, String timelineJson) {
        log.info("RemoteRenderService: submitting job worker={} profile={}", workerId, profile);

        RemoteRenderJob job = RemoteRenderJob.create(workerId, profile, timelineJson);
        activeJobs.put(job.jobId(), job);

        CompletableFuture.runAsync(() -> executeJob(job.jobId()));

        return job;
    }

    private void executeJob(String jobId) {
        RemoteRenderJob job = activeJobs.get(jobId);
        if (job == null) {
            log.warn("RemoteRenderService: job not found: {}", jobId);
            return;
        }

        try {
            job = job.withStarted();
            activeJobs.put(jobId, job);
            workerRegistry.updateWorkerStatus(job.workerId(), WorkerStatus.BUSY);
            notifyCallback(job);

            RenderPreset preset = RenderPreset.fromProfile(job.profile());

            Path outputDir = Path.of(storageRoot, "artifacts", "remote-" + jobId);
            Files.createDirectories(outputDir);
            String outputPath = outputDir.resolve("output.mp4").toString();

            renderService.renderPlaceholder(jobId, outputPath, preset);

            if (!Files.exists(Path.of(outputPath))) {
                throw new IllegalStateException("Render produced no output file: " + outputPath);
            }

            String artifactId = "art_remote_" + jobId;
            String storageUri = "localFsStorageProvider://artifacts/remote-" + jobId + "/output.mp4";

            job = job.withCompleted(artifactId, storageUri);
            activeJobs.put(jobId, job);
            notifyCallback(job);

            log.info("RemoteRenderService: job completed: {} artifact={}", jobId, artifactId);

        } catch (Exception e) {
            log.error("RemoteRenderService: job execution failed: {}", jobId, e);
            job = job.withFailed("RENDER-500-001", "Remote render failed: " + e.getMessage());
            activeJobs.put(jobId, job);
            notifyCallback(job);
        } finally {
            workerRegistry.updateWorkerStatus(job.workerId(), WorkerStatus.IDLE);
        }
    }

    private void notifyCallback(RemoteRenderJob job) {
        if (callbackUrl == null || callbackUrl.isBlank()) return;
        try {
            String payload = String.format(
                    "{\"jobId\":\"%s\",\"status\":\"%s\",\"progress\":%d,\"artifactId\":\"%s\",\"storageUri\":\"%s\",\"errorCode\":\"%s\",\"errorMessage\":\"%s\"}",
                    job.jobId(), job.status(), "COMPLETED".equals(job.status()) ? 100 : ("RUNNING".equals(job.status()) ? 50 : 0),
                    job.artifactId() != null ? job.artifactId() : "",
                    job.storageUri() != null ? job.storageUri() : "",
                    job.errorCode() != null ? job.errorCode() : "",
                    job.errorMessage() != null ? job.errorMessage().replace("\"", "'") : "");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(callbackUrl + "/api/v1/remote-worker/jobs/" + job.jobId() + "/callback"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(resp -> {
                        if (resp.statusCode() >= 400) {
                            log.warn("Callback failed for job={}: HTTP {}", job.jobId(), resp.statusCode());
                        }
                    })
                    .exceptionally(ex -> {
                        log.warn("Callback error for job={}: {}", job.jobId(), ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.warn("Failed to send callback for job={}: {}", job.jobId(), e.getMessage());
        }
    }

    public RemoteRenderJob getJobStatus(String jobId) {
        return activeJobs.get(jobId);
    }

    public List<RemoteRenderJob> getWorkerJobs(String workerId) {
        return activeJobs.values().stream()
                .filter(j -> j.workerId().equals(workerId))
                .toList();
    }

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
        notifyCallback(job);
        return job;
    }
}
