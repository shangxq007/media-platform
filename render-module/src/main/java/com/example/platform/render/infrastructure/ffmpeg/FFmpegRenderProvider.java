package com.example.platform.render.infrastructure.ffmpeg;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.render.infrastructure.subtitle.SubtitlePathSanitizer;
import com.example.platform.render.infrastructure.media.MediaAssetResolver;
import com.example.platform.render.domain.RenderProfile;
import com.example.platform.render.domain.timeline.TimelineClip;
import com.example.platform.render.domain.timeline.TimelineTrack;
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
    private final MediaAssetResolver assetResolver;

    public FFmpegRenderProvider(ProcessToolRunner processToolRunner,
            FFmpegCommandFactory commandFactory,
            TimelineScriptParser timelineScriptParser,
            MediaAssetResolver assetResolver) {
        this.processToolRunner = processToolRunner;
        this.commandFactory = commandFactory;
        this.timelineScriptParser = timelineScriptParser;
        this.assetResolver = assetResolver;
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

            var parseResult = timelineScriptParser.parse(aiScript);

            List<FFmpegCommandFactory.ResolvedClip> resolvedClips = resolveClips(aiScript);
            if (resolvedClips.isEmpty()) {
                String snippet = aiScript.length() > 300 ? aiScript.substring(0, 300) + "..." : aiScript;
                throw new PlatformException(
                        new ConfigurableErrorCode("RENDER-400-003", 500403,
                                Map.of("en", "No renderable clips in timeline", "zh", "时间线中没有可渲染片段"),
                                "render", 400),
                        "Timeline has no local media files. storageRoot=" + storageRoot + " script=" + snippet,
                        Map.of("jobId", jobId, "provider", "ffmpeg"),
                        "en");
            }

            Optional<TimelineSpec> timeline = timelineScriptParser.parse(aiScript);
            List<List<FFmpegCommandFactory.ResolvedClip>> audioTracks =
                    timeline.map(this::resolveAudioTracks).orElse(List.of());

            // Extract subtitle, watermark, fade, transition, and spatial plan from timeline JSON
            String subtitlePath = extractSubtitlePath(scriptRoot);
            String watermarkPath = extractWatermarkPath(scriptRoot);
            double fadeDuration = extractFadeDuration(scriptRoot);
            double transitionDuration = extractTransitionDuration(scriptRoot);

            // Load spatial plan from metadata path (if provided by timeline)
            com.example.platform.render.domain.spatial.SpatialPlan spatialPlan = null;
            String spatialPlanPath = extractSpatialPlanPath(scriptRoot);
            if (spatialPlanPath != null && !spatialPlanPath.isBlank()) {
                Path spPath = Path.of(spatialPlanPath);
                if (java.nio.file.Files.isRegularFile(spPath)) {
                    spatialPlan = com.example.platform.render.domain.spatial.SpatialPlanLoader
                            .loadFromFile(spPath).orElse(null);
                }
            }

            List<String> args;
            if (!audioTracks.isEmpty()) {
                args = commandFactory.buildMultiTrackCommand(
                        resolvedClips, audioTracks, outputPath, renderProfile,
                        subtitlePath, watermarkPath, fadeDuration, transitionDuration, spatialPlan);
            } else if (subtitlePath != null || watermarkPath != null || fadeDuration > 0 || transitionDuration > 0
                    || spatialPlan != null) {
                args = commandFactory.buildMultiTrackCommand(
                        resolvedClips, List.of(), outputPath, renderProfile,
                        subtitlePath, watermarkPath, fadeDuration, transitionDuration, spatialPlan);
            } else {
                args = commandFactory.buildRenderFromResolvedClips(
                        resolvedClips, outputPath, renderProfile, segmentSlice);
            }
            ToolExecutionRequest request = ToolExecutionRequest.withTimeout("ffmpeg", args, 600_000);
            ToolExecutionResult result = processToolRunner.execute(request);

            if (!result.isSuccess()) {
                String errSnippet = result.stderr() != null ? result.stderr().substring(0, Math.min(1000, result.stderr().length())) : "null";
                log.error("FFmpegRenderProvider: ffmpeg failed: exit={} stderr={}", result.exitCode(), errSnippet);
                throw new PlatformException(
                        new ConfigurableErrorCode("RENDER-500-002", 500502,
                                Map.of("en", "FFmpeg rendering failed", "zh", "FFmpeg渲染失败"),
                                "render", 500),
                        "ffmpeg failed (exit=" + result.exitCode() + "): " + errSnippet,
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
            log.warn("FFmpegRenderProvider: timeline parse returned empty");
            return resolved;
        }
        List<TimelineClip> videoClips = timelineScriptParser.videoClipsInOrder(timeline.get());
        log.info("FFmpegRenderProvider: found {} video clips", videoClips.size());
        for (TimelineClip clip : videoClips) {
            if (clip.assetRef() == null) {
                log.warn("FFmpegRenderProvider: clip {} has null assetRef", clip.id());
                continue;
            }
            String uri = clip.assetRef().storageUri();
            String localPath = resolveToLocalPath(uri);
            if (localPath == null) {
                log.warn("FFmpegRenderProvider: skipping unreachable media: {}", uri);
                continue;
            }
            resolved.add(new FFmpegCommandFactory.ResolvedClip(
                    localPath, clip.assetInPoint(), clip.clipDuration()));
        }
        log.info("FFmpegRenderProvider: resolved {} clips", resolved.size());
        return resolved;
    }

    private List<List<FFmpegCommandFactory.ResolvedClip>> resolveAudioTracks(TimelineSpec timeline) {
        List<List<FFmpegCommandFactory.ResolvedClip>> audioTracks = new ArrayList<>();
        if (timeline.tracks() == null) return audioTracks;

        for (TimelineTrack track : timeline.tracks()) {
            if (track.type() != TimelineTrack.TrackType.AUDIO || track.muted()) continue;
            List<FFmpegCommandFactory.ResolvedClip> trackClips = new ArrayList<>();
            if (track.clips() == null) continue;
            for (TimelineClip clip : track.clips()) {
                if (clip.assetRef() == null) continue;
                String uri = clip.assetRef().storageUri();
                String localPath = resolveToLocalPath(uri);
                if (localPath == null) {
                    log.warn("FFmpegRenderProvider: skipping unreachable audio: {}", uri);
                    continue;
                }
                trackClips.add(new FFmpegCommandFactory.ResolvedClip(
                        localPath, clip.assetInPoint(), clip.clipDuration()));
            }
            if (!trackClips.isEmpty()) {
                audioTracks.add(trackClips);
            }
        }
        return audioTracks;
    }

    private String resolveToLocalPath(String uri) {
        if (timelineScriptParser.mediaFileExists(uri, storageRoot)) {
            return timelineScriptParser.resolveLocalPath(uri, storageRoot);
        }
        String downloaded = assetResolver.resolveToLocalPath(uri);
        if (downloaded != null) {
            return downloaded;
        }
        return null;
    }

    private RenderProfile toRenderProfile(String profile) {
        RenderPreset preset = RenderPreset.fromProfile(profile);
        // For video-only rendering (no audio streams in input), do not set audio codec
        // to avoid ffmpeg errors like "Stream map '0:a:0' matches no streams"
        return new RenderProfile(
                profile,
                profile,
                null,
                preset.width() + "x" + preset.height(),
                preset.videoCodec().replace("lib", "").replace("x264", "h264"),
                preset.videoBitrateKbps(),
                null,  // no audio codec for video-only
                0,     // no audio sample rate
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

    /**
     * Extract subtitle file path from the timeline JSON root.
     * Looks for "metadata" -> "subtitlePath" or "subtitle" -> "path".
     * The path is validated via {@link SubtitlePathSanitizer} to prevent path injection.
     */
    private static String extractSubtitlePath(JsonNode root) {
        if (root == null) return null;
        String rawPath = null;

        // Check metadata.subtitlePath
        JsonNode meta = root.get("metadata");
        if (meta != null) {
            JsonNode subPath = meta.get("subtitlePath");
            if (subPath != null && !subPath.asText().isBlank()) {
                rawPath = subPath.asText();
            }
        }
        // Check subtitle.path
        if (rawPath == null) {
            JsonNode sub = root.get("subtitle");
            if (sub != null) {
                JsonNode subPath = sub.get("path");
                if (subPath != null && !subPath.asText().isBlank()) {
                    rawPath = subPath.asText();
                }
            }
        }

        if (rawPath == null) return null;

        // Strip file:// prefix if present
        String path = rawPath.startsWith("file://") ? rawPath.substring("file://".length()) : rawPath;

        // Validate path to prevent injection
        String sanitized = SubtitlePathSanitizer.sanitize(path, null);
        if (sanitized == null) {
            log.warn("Rejected unsafe subtitle path: {}", path);
            return null;
        }

        // Verify the file actually exists
        if (!java.nio.file.Files.isRegularFile(java.nio.file.Path.of(sanitized))) {
            log.warn("Subtitle file does not exist: {}", sanitized);
            return null;
        }

        return sanitized;
    }

    /**
     * Extract watermark image path from the timeline JSON root.
     * Looks for "metadata" -> "watermarkPath" or "watermark" -> "path".
     */
    private static String extractWatermarkPath(JsonNode root) {
        if (root == null) return null;
        // Check metadata.watermarkPath
        JsonNode meta = root.get("metadata");
        if (meta != null) {
            JsonNode wmPath = meta.get("watermarkPath");
            if (wmPath != null && !wmPath.asText().isBlank()) {
                String p = wmPath.asText();
                return p.startsWith("file://") ? p.substring("file://".length()) : p;
            }
        }
        // Check watermark.path
        JsonNode wm = root.get("watermark");
        if (wm != null) {
            JsonNode wmPath = wm.get("path");
            if (wmPath != null && !wmPath.asText().isBlank()) {
                String p = wmPath.asText();
                return p.startsWith("file://") ? p.substring("file://".length()) : p;
            }
        }
        return null;
    }

    /**
     * Extract fade in/out duration from the timeline JSON root.
     * Looks for "metadata" -> "fadeDuration" (in seconds).
     * Default is 0 (disabled).
     */
    private static double extractFadeDuration(JsonNode root) {
        if (root == null) return 0;
        JsonNode meta = root.get("metadata");
        if (meta != null) {
            JsonNode fade = meta.get("fadeDuration");
            if (fade != null && fade.isNumber()) {
                return fade.asDouble();
            }
        }
        // Also check top-level fade.duration
        JsonNode fade = root.get("fade");
        if (fade != null) {
            JsonNode dur = fade.get("duration");
            if (dur != null && dur.isNumber()) {
                return dur.asDouble();
            }
        }
        return 0;
    }

    /**
     * Extract cross-dissolve transition duration from the timeline JSON root.
     * Looks for "metadata" -> "transitionDuration" (in seconds).
     * Default is 0 (disabled, uses simple concat).
     */
    private static double extractTransitionDuration(JsonNode root) {
        if (root == null) return 0;
        JsonNode meta = root.get("metadata");
        if (meta != null) {
            JsonNode xfade = meta.get("transitionDuration");
            if (xfade != null && xfade.isNumber()) {
                return xfade.asDouble();
            }
        }
        // Also check top-level transition.duration
        JsonNode xfade = root.get("transition");
        if (xfade != null) {
            JsonNode dur = xfade.get("duration");
            if (dur != null && dur.isNumber()) {
                return dur.asDouble();
            }
        }
        return 0;
    }

    /**
     * Extract spatial plan file path from the timeline JSON root.
     * Looks for "metadata" -> "spatialPlanPath".
     */
    private static String extractSpatialPlanPath(JsonNode root) {
        if (root == null) return null;
        JsonNode meta = root.get("metadata");
        if (meta != null) {
            JsonNode spPath = meta.get("spatialPlanPath");
            if (spPath != null && !spPath.asText().isBlank()) {
                String p = spPath.asText();
                return p.startsWith("file://") ? p.substring("file://".length()) : p;
            }
        }
        return null;
    }
}
