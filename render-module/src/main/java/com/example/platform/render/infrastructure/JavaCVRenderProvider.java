package com.example.platform.render.infrastructure;

import com.example.platform.shared.Ids;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegFrameFilter;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JavaCV-based render provider for video editing and transcoding.
 *
 * <p>Uses JavaCV (FFmpeg JNI bindings) for:</p>
 * <ul>
 *   <li>Video clipping (start/end time trimming)</li>
 *   <li>Transcoding with multiple codecs: H.264, H.265, VP9</li>
 *   <li>Filter/effect application (color, fade, subtitle burn-in)</li>
 *   <li>Watermark overlay</li>
 *   <li>Audio track mixing</li>
 * </ul>
 *
 * <p>Supports presets: DEFAULT, H265, VP9, PREVIEW_720P, HQ_1080P</p>
 */
@Component
public class JavaCVRenderProvider implements RenderProvider {
    private static final Logger log = LoggerFactory.getLogger(JavaCVRenderProvider.class);

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    private final JavaCVRenderService renderService;
    private final JavaCVTranscodeService transcodeService;

    public JavaCVRenderProvider(JavaCVRenderService renderService, JavaCVTranscodeService transcodeService) {
        this.renderService = renderService;
        this.transcodeService = transcodeService;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public String getProviderKey() {
        return "javacv";
    }

    public RenderProviderCapability getCapability() {
        return new RenderProviderCapability(
                "javacv",
                Set.of("mp4", "ogg", "webm", "mov"),
                Set.of("h264", "h265", "vp9", "aac", "mp3"),
                Set.of("video.fade_in", "video.fade_out", "video.cross_dissolve",
                        "video.blur", "video.sharpen", "video.brightness", "video.contrast",
                        "video.grayscale", "video.sepia", "video.watermark",
                        "text.subtitle_burn_in", "audio.volume"),
                Set.of("dissolve", "fade_in", "fade_out", "cross_dissolve"),
                Set.of("burn_in"),
                "3840x2160",
                false,
                false,
                false,
                Set.of("default_1080p", "default_720p", "social_1080p", "social_720p",
                        "mobile_480p", "4k_2160p", "free_720p_watermarked",
                        "pro_1080p", "team_4k",
                        "preview_720p", "hq_1080p", "h265", "vp9")
        );
    }

    @Override
    public RenderResult render(String jobId, String aiScript, String profile) {
        log.info("JavaCVRenderProvider: rendering job={}, profile={}", jobId, profile);

        try {
            Path outputDir = Path.of(storageRoot, "artifacts", jobId);
            outputDir.toFile().mkdirs();
            String outputPath = outputDir.resolve("output.mp4").toString();

            RenderPreset preset = RenderPreset.fromProfile(profile);

            if (aiScript != null && aiScript.contains("{")) {
                renderFromTimeline(jobId, aiScript, preset, outputPath);
            } else {
                renderFromAiScript(jobId, aiScript, preset, outputPath);
            }

            String artifactId = Ids.newId("art");
            String resolution = preset.width() + "x" + preset.height();

            log.info("JavaCVRenderProvider: render complete, artifact={}, resolution={}", artifactId, resolution);

            return new RenderResult(
                    artifactId,
                    "localFsStorageProvider://artifacts/" + jobId + "/output.mp4",
                    30L,
                    "mp4",
                    resolution
            );
        } catch (PlatformException e) {
            throw e;
        } catch (Exception e) {
            log.error("JavaCVRenderProvider: render failed for job={}", jobId, e);
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-500-001", 500101,
                            Map.of("en", "Render execution failed", "zh", "渲染执行失败"),
                            "render", 500),
                    e.getMessage(),
                    Map.of("jobId", jobId, "error", e.getClass().getSimpleName()),
                    "en"
            );
        }
    }

    private void renderFromTimeline(String jobId, String aiScript, RenderPreset preset, String outputPath) throws Exception {
        log.info("JavaCVRenderProvider: rendering from OTIO timeline, job={}, preset={}", jobId, preset.key());

        Map<String, Object> timeline = parseOtiOTimeline(aiScript);
        List<Map<String, Object>> tracks = (List<Map<String, Object>>) timeline.getOrDefault("tracks", List.of());

        if (tracks.isEmpty()) {
            log.warn("JavaCVRenderProvider: empty timeline, generating placeholder");
            renderService.renderPlaceholder(jobId, outputPath, preset);
            return;
        }

        Map<String, Object> firstTrack = tracks.get(0);
        List<Map<String, Object>> clips = (List<Map<String, Object>>) firstTrack.getOrDefault("children", List.of());

        if (clips.isEmpty()) {
            renderService.renderPlaceholder(jobId, outputPath, preset);
            return;
        }

        Map<String, Object> firstClip = clips.get(0);
        Map<String, Object> sourceRange = (Map<String, Object>) firstClip.getOrDefault("source_range", Map.of());
        double startTime = ((Number) sourceRange.getOrDefault("start_time", 0.0)).doubleValue();
        double duration = ((Number) sourceRange.getOrDefault("duration", 30.0)).doubleValue();

        String sourceUrl = (String) firstClip.getOrDefault("media_reference", "");
        if (sourceUrl == null || sourceUrl.isEmpty() || sourceUrl.startsWith("file://")) {
            renderService.renderPlaceholder(jobId, outputPath, preset);
            return;
        }

        List<Map<String, Object>> subtitleTracks = extractSubtitleTracks(timeline);
        boolean hasBurnInSubtitles = subtitleTracks.stream()
                .anyMatch(st -> Boolean.TRUE.equals(st.get("burnIn")));

        String sourcePath = sourceUrl.replace("file://", "");
        if (hasBurnInSubtitles) {
            renderService.renderWithSubtitleBurnIn(jobId, sourcePath, outputPath, preset, startTime, duration, subtitleTracks);
        } else {
            renderService.transcodeWithClipping(jobId, sourcePath, outputPath, preset, startTime, duration);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractSubtitleTracks(Map<String, Object> timeline) {
        List<Map<String, Object>> subtitleTracks = new ArrayList<>();
        List<Map<String, Object>> tracks = (List<Map<String, Object>>) timeline.getOrDefault("tracks", List.of());
        for (Map<String, Object> track : tracks) {
            String type = (String) track.getOrDefault("type", "");
            if ("SUBTITLE".equalsIgnoreCase(type) || "TEXT".equalsIgnoreCase(type)) {
                subtitleTracks.add(track);
            }
        }
        if (timeline.containsKey("subtitleTracks")) {
            subtitleTracks.addAll((List<Map<String, Object>>) timeline.get("subtitleTracks"));
        }
        return subtitleTracks;
    }

    private void renderFromAiScript(String jobId, String aiScript, RenderPreset preset, String outputPath) throws Exception {
        log.info("JavaCVRenderProvider: rendering from AI script, job={}", jobId);
        renderService.renderPlaceholder(jobId, outputPath, preset);
    }

    private Map<String, Object> parseOtiOTimeline(String aiScript) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(aiScript, Map.class);
        } catch (Exception e) {
            log.warn("JavaCVRenderProvider: failed to parse OTIO timeline JSON, using defaults");
            return Map.of("tracks", List.of());
        }
    }

    @Override
    public List<String> getSupportedProfiles() {
        return List.of(
                "default_1080p", "default_720p",
                "social_1080p", "social_720p",
                "mobile_480p", "4k_2160p",
                "preview_720p", "hq_1080p", "h265", "vp9"
        );
    }

    @Override
    public boolean supports(String capability) {
        return switch (capability) {
            case "h264", "h265", "vp9", "mp4", "watermark", "subtitle-burn", "fade", "clip", "transcode" -> true;
            case "hdr" -> false;
            default -> getSupportedProfiles().contains(capability);
        };
    }

    @Override
    public EnvironmentValidationResult validateEnvironment() {
        try {
            String version = org.bytedeco.ffmpeg.global.avcodec.class.getPackage().getImplementationVersion();
            log.info("JavaCVRenderProvider: JavaCV/FFmpeg available, version={}", version);
            return EnvironmentValidationResult.ok();
        } catch (Exception e) {
            log.warn("JavaCVRenderProvider: JavaCV not available: {}", e.getMessage());
            return EnvironmentValidationResult.failed("JavaCV/FFmpeg not available: " + e.getMessage());
        }
    }
}
