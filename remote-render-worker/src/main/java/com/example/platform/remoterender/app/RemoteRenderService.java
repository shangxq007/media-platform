package com.example.platform.remoterender.app;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.remoterender.domain.RemoteRenderJob;
import com.example.platform.remoterender.domain.WorkerStatus;
import com.example.platform.render.domain.timeline.TimelineClip;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineTrack;
import com.example.platform.render.infrastructure.RenderPreset;
import com.example.platform.render.infrastructure.ffmpeg.FFmpegCommandFactory;
import com.example.platform.render.infrastructure.media.MediaAssetResolver;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RemoteRenderService {

    private static final Logger log = LoggerFactory.getLogger(RemoteRenderService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    @Value("${app.remote-worker.callback-url:}")
    private String callbackUrl;

    private final ProcessToolRunner processToolRunner;
    private final FFmpegCommandFactory commandFactory;
    private final TimelineScriptParser timelineScriptParser;
    private final MediaAssetResolver assetResolver;
    private final WorkerRegistryService workerRegistry;
    private final Map<String, RemoteRenderJob> activeJobs = new ConcurrentHashMap<>();
    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public RemoteRenderService(ProcessToolRunner processToolRunner,
                                FFmpegCommandFactory commandFactory,
                                TimelineScriptParser timelineScriptParser,
                                MediaAssetResolver assetResolver,
                                WorkerRegistryService workerRegistry) {
        this.processToolRunner = processToolRunner;
        this.commandFactory = commandFactory;
        this.timelineScriptParser = timelineScriptParser;
        this.assetResolver = assetResolver;
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

            String profile = job.profile();
            String timelineJson = job.timelineJson();
            RenderPreset preset = RenderPreset.fromProfile(profile);

            Path outputDir = Path.of(storageRoot, "artifacts", "remote-" + jobId);
            Files.createDirectories(outputDir);
            String outputPath = outputDir.resolve("output.mp4").toString();

            // Step 1: Parse timeline
            Optional<TimelineSpec> timelineOpt = timelineScriptParser.parse(timelineJson);
            if (timelineOpt.isEmpty()) {
                throw new IllegalStateException("Failed to parse timeline JSON for job " + jobId);
            }
            TimelineSpec timeline = timelineOpt.get();

            updateProgress(job, 10);

            // Step 2: Resolve video clips (download remote assets if needed)
            List<FFmpegCommandFactory.ResolvedClip> videoClips = new ArrayList<>();
            for (TimelineClip clip : timelineScriptParser.videoClipsInOrder(timeline)) {
                if (clip.assetRef() == null) continue;
                String uri = clip.assetRef().storageUri();
                String localPath = resolveToLocalPath(uri);
                if (localPath == null) {
                    log.warn("Skipping unreachable media: {}", uri);
                    continue;
                }
                videoClips.add(new FFmpegCommandFactory.ResolvedClip(
                        localPath, clip.assetInPoint(), clip.clipDuration()));
            }

            if (videoClips.isEmpty()) {
                throw new IllegalStateException("No renderable clips found in timeline for job " + jobId);
            }

            updateProgress(job, 30);

            // Step 3: Resolve audio tracks
            List<List<FFmpegCommandFactory.ResolvedClip>> audioTracks = new ArrayList<>();
            if (timeline.tracks() != null) {
                for (TimelineTrack track : timeline.tracks()) {
                    if (track.type() != TimelineTrack.TrackType.AUDIO || track.muted()) continue;
                    List<FFmpegCommandFactory.ResolvedClip> trackClips = new ArrayList<>();
                    if (track.clips() == null) continue;
                    for (TimelineClip clip : track.clips()) {
                        if (clip.assetRef() == null) continue;
                        String uri = clip.assetRef().storageUri();
                        String localPath = resolveToLocalPath(uri);
                        if (localPath == null) continue;
                        trackClips.add(new FFmpegCommandFactory.ResolvedClip(
                                localPath, clip.assetInPoint(), clip.clipDuration()));
                    }
                    if (!trackClips.isEmpty()) audioTracks.add(trackClips);
                }
            }

            updateProgress(job, 40);

            // Step 4: Build FFmpeg command
            com.example.platform.render.domain.RenderProfile renderProfile = toRenderProfile(preset);
            List<String> args;
            if (!audioTracks.isEmpty()) {
                args = commandFactory.buildMultiTrackCommand(
                        videoClips, audioTracks, outputPath, renderProfile);
            } else {
                JsonNode scriptRoot = parseRoot(timelineJson);
                var segmentSlice = com.example.platform.render.app.timeline.SegmentRenderSlice.fromJson(scriptRoot);
                args = commandFactory.buildRenderFromResolvedClips(
                        videoClips, outputPath, renderProfile, segmentSlice);
            }

            updateProgress(job, 50);

            // Step 5: Execute FFmpeg
            log.info("Executing FFmpeg for job={} args={}", jobId, args);
            ToolExecutionRequest request = ToolExecutionRequest.withTimeout("ffmpeg", args, 600_000);
            ToolExecutionResult result = processToolRunner.execute(request);

            if (!result.isSuccess()) {
                throw new IllegalStateException("FFmpeg failed (exit=" + result.exitCode() + "): " + result.stderr());
            }

            updateProgress(job, 90);

            // Step 6: Verify output
            if (!Files.exists(Path.of(outputPath))) {
                throw new IllegalStateException("FFmpeg produced no output file: " + outputPath);
            }
            long fileSize = Files.size(Path.of(outputPath));
            log.info("FFmpeg output: {} ({} bytes)", outputPath, fileSize);

            // Step 7: Complete
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

    private void updateProgress(RemoteRenderJob job, int progress) {
        job = job.withStatus("RUNNING");
        activeJobs.put(job.jobId(), job);
        notifyCallback(job, progress);
    }

    private String resolveToLocalPath(String uri) {
        // Try local filesystem first
        if (uri == null || uri.isBlank()) return null;
        if (uri.startsWith("file://") || uri.startsWith("/")) {
            String path = uri.startsWith("file://") ? uri.substring(7) : uri;
            if (Files.isRegularFile(Path.of(path))) return path;
        }
        if (uri.startsWith("localFsStorageProvider://")) {
            String relative = uri.substring("localFsStorageProvider://".length());
            Path localPath = Path.of(storageRoot, relative);
            if (Files.isRegularFile(localPath)) return localPath.toString();
        }
        // Try asset resolver for remote URIs
        String resolved = assetResolver.resolveToLocalPath(uri);
        if (resolved != null) return resolved;
        return null;
    }

    private com.example.platform.render.domain.RenderProfile toRenderProfile(RenderPreset preset) {
        return com.example.platform.render.domain.RenderProfile.of(
                preset.key(),
                preset.width() + "x" + preset.height(),
                preset.videoCodec().replace("lib", "").replace("x264", "h264"));
    }

    private JsonNode parseRoot(String json) {
        try { return MAPPER.readTree(json); }
        catch (Exception e) { return MAPPER.createObjectNode(); }
    }

    private void notifyCallback(RemoteRenderJob job) {
        int progress = "COMPLETED".equals(job.status()) ? 100
                : ("RUNNING".equals(job.status()) ? 50 : 0);
        notifyCallback(job, progress);
    }

    private void notifyCallback(RemoteRenderJob job, int progress) {
        if (callbackUrl == null || callbackUrl.isBlank()) return;
        try {
            String payload = String.format(
                    "{\"jobId\":\"%s\",\"status\":\"%s\",\"progress\":%d,\"artifactId\":\"%s\",\"storageUri\":\"%s\",\"errorCode\":\"%s\",\"errorMessage\":\"%s\"}",
                    job.jobId(), job.status(), progress,
                    job.artifactId() != null ? job.artifactId() : "",
                    job.storageUri() != null ? job.storageUri() : "",
                    job.errorCode() != null ? job.errorCode() : "",
                    job.errorMessage() != null ? job.errorMessage().replace("\"", "'") : "");

            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(callbackUrl + "/api/v1/remote-worker/jobs/" + job.jobId() + "/callback"))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            httpClient.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString())
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
                    "Job not found: " + jobId, Map.of("jobId", jobId), "en");
        }
        job = job.withStatus("CANCELLED");
        activeJobs.put(jobId, job);
        notifyCallback(job);
        return job;
    }
}
