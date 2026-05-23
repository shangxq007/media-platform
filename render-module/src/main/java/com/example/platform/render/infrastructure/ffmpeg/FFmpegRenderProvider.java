package com.example.platform.render.infrastructure.ffmpeg;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.render.domain.RenderProfile;
import com.example.platform.render.domain.timeline.TimelineClip;
import com.example.platform.render.app.timeline.SegmentRenderSlice;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.platform.render.infrastructure.RenderPreset;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "render.providers.ffmpeg", name = "enabled", havingValue = "true")
public class FFmpegRenderProvider implements RenderProvider {

    private static final Logger log = LoggerFactory.getLogger(FFmpegRenderProvider.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    private final ProcessToolRunner processToolRunner;
    private final FFmpegCommandFactory commandFactory;
    private final TimelineScriptParser timelineScriptParser;

    public FFmpegRenderProvider(ProcessToolRunner processToolRunner,
            FFmpegCommandFactory commandFactory,
            TimelineScriptParser timelineScriptParser) {
        this.processToolRunner = processToolRunner;
        this.commandFactory = commandFactory;
        this.timelineScriptParser = timelineScriptParser;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    @Override
    public RenderResult render(String jobId, String aiScript, String profile) {
        log.info("FFmpegRenderProvider: rendering job={}, profile={}", jobId, profile);

        try {
            Path outputDir = Path.of(storageRoot, "artifacts", jobId);
            outputDir.toFile().mkdirs();
            String outputPath = outputDir.resolve("output.mp4").toString();
            RenderProfile renderProfile = toRenderProfile(profile);

            JsonNode scriptRoot = parseRoot(aiScript);
            SegmentRenderSlice segmentSlice = SegmentRenderSlice.fromJson(scriptRoot);

            List<FFmpegCommandFactory.ResolvedClip> resolvedClips = resolveClips(aiScript);
            if (resolvedClips.isEmpty()) {
                throw new PlatformException(
                        new ConfigurableErrorCode("RENDER-400-003", 500403,
                                Map.of("en", "No renderable clips in timeline", "zh", "时间线中没有可渲染片段"),
                                "render", 400),
                        "Timeline has no local media files",
                        Map.of("jobId", jobId, "provider", "ffmpeg"),
                        "en");
            }

            List<String> args = commandFactory.buildRenderFromResolvedClips(
                    resolvedClips, outputPath, renderProfile, segmentSlice);
            ToolExecutionRequest request = ToolExecutionRequest.withTimeout("ffmpeg", args, 600_000);
            ToolExecutionResult result = processToolRunner.execute(request);

            if (!result.isSuccess()) {
                throw new PlatformException(
                        new ConfigurableErrorCode("RENDER-500-002", 500502,
                                Map.of("en", "FFmpeg rendering failed", "zh", "FFmpeg渲染失败"),
                                "render", 500),
                        "ffmpeg failed: " + result.stderr(),
                        Map.of("jobId", jobId, "provider", "ffmpeg",
                                "exitCode", String.valueOf(result.exitCode())),
                        "en");
            }

            String artifactId = Ids.newId("art");
            RenderPreset preset = RenderPreset.fromProfile(profile);
            String resolution = preset.width() + "x" + preset.height();
            log.info("FFmpegRenderProvider: render complete job={} artifact={}", jobId, artifactId);

            return new RenderResult(
                    artifactId,
                    "localFsStorageProvider://artifacts/" + jobId + "/output.mp4",
                    (long) preset.frameRate() * 5,
                    "mp4",
                    resolution);
        } catch (PlatformException e) {
            throw e;
        } catch (Exception e) {
            log.error("FFmpegRenderProvider: render failed for job={}", jobId, e);
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-500-001", 500101,
                            Map.of("en", "FFmpeg render failed", "zh", "FFmpeg渲染失败"),
                            "render", 500),
                    e.getMessage(),
                    Map.of("jobId", jobId, "provider", "ffmpeg"),
                    "en");
        }
    }

    private static JsonNode parseRoot(String aiScript) {
        try {
            return MAPPER.readTree(aiScript);
        } catch (Exception e) {
            return MAPPER.createObjectNode();
        }
    }

    private List<FFmpegCommandFactory.ResolvedClip> resolveClips(String aiScript) {
        List<FFmpegCommandFactory.ResolvedClip> resolved = new ArrayList<>();
        Optional<TimelineSpec> timeline = timelineScriptParser.parse(aiScript);
        if (timeline.isEmpty()) {
            return resolved;
        }
        for (TimelineClip clip : timelineScriptParser.videoClipsInOrder(timeline.get())) {
            if (clip.assetRef() == null) {
                continue;
            }
            String uri = clip.assetRef().storageUri();
            if (!timelineScriptParser.mediaFileExists(uri, storageRoot)) {
                log.warn("FFmpegRenderProvider: skipping missing media: {}", uri);
                continue;
            }
            String localPath = timelineScriptParser.resolveLocalPath(uri, storageRoot);
            resolved.add(new FFmpegCommandFactory.ResolvedClip(
                    localPath, clip.assetInPoint(), clip.clipDuration()));
        }
        return resolved;
    }

    private RenderProfile toRenderProfile(String profile) {
        RenderPreset preset = RenderPreset.fromProfile(profile);
        return new RenderProfile(
                profile,
                profile,
                null,
                preset.width() + "x" + preset.height(),
                preset.videoCodec().replace("lib", "").replace("x264", "h264"),
                preset.videoBitrateKbps(),
                preset.audioCodec(),
                preset.sampleRate(),
                Map.of());
    }

    @Override
    public List<String> getSupportedProfiles() {
        return List.of("social_1080p", "social_720p", "default_1080p", "default_720p",
                "broadcast_4k", "proxy_480p");
    }

    @Override
    public boolean supports(String capability) {
        return switch (capability) {
            case "h264", "h265", "mp4", "watermark", "subtitle-burn", "thumbnail", "probe",
                    "4k", "hdr", "dash", "hls", "concat", "transcode" -> true;
            default -> getSupportedProfiles().contains(capability);
        };
    }

    @Override
    public EnvironmentValidationResult validateEnvironment() {
        try {
            ToolExecutionRequest request = ToolExecutionRequest.withTimeout(
                    "ffmpeg", List.of("-version"), 10_000);
            ToolExecutionResult result = processToolRunner.execute(request);
            if (result.isSuccess()) {
                return EnvironmentValidationResult.ok();
            }
            return EnvironmentValidationResult.failed("FFmpeg returned non-zero exit code");
        } catch (Exception e) {
            return EnvironmentValidationResult.failed("FFmpeg not available: " + e.getMessage());
        }
    }
}
