package com.example.platform.render.infrastructure;

import com.example.platform.render.domain.timeline.TimelineClip;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineTrack;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final TimelineScriptParser timelineScriptParser;

    public JavaCVRenderProvider(JavaCVRenderService renderService,
            JavaCVTranscodeService transcodeService,
            TimelineScriptParser timelineScriptParser) {
        this.renderService = renderService;
        this.transcodeService = transcodeService;
        this.timelineScriptParser = timelineScriptParser;
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
        log.info("JavaCVRenderProvider: rendering from timeline, job={}, preset={}", jobId, preset.key());

        Optional<TimelineSpec> parsed = timelineScriptParser.parse(aiScript);
        if (parsed.isEmpty()) {
            renderService.renderPlaceholder(jobId, outputPath, preset);
            return;
        }

        TimelineSpec timeline = parsed.get();
        Optional<TimelineClip> firstClip = timelineScriptParser.firstVideoClip(timeline);
        if (firstClip.isEmpty() || firstClip.get().assetRef() == null) {
            renderService.renderPlaceholder(jobId, outputPath, preset);
            return;
        }

        String sourceUrl = firstClip.get().assetRef().storageUri();
        if (!timelineScriptParser.mediaFileExists(sourceUrl, storageRoot)) {
            log.warn("JavaCVRenderProvider: media not found {}, using placeholder", sourceUrl);
            renderService.renderPlaceholder(jobId, outputPath, preset);
            return;
        }

        double startTime = firstClip.get().assetInPoint();
        double duration = firstClip.get().clipDuration();
        String sourcePath = timelineScriptParser.resolveLocalPath(sourceUrl, storageRoot);

        boolean hasBurnInSubtitles = timeline.tracks() != null && timeline.tracks().stream()
                .anyMatch(t -> t.type() == TimelineTrack.TrackType.SUBTITLE);

        if (hasBurnInSubtitles) {
            List<Map<String, Object>> subtitleTracks = timeline.tracks().stream()
                    .filter(t -> t.type() == TimelineTrack.TrackType.SUBTITLE)
                    .map(t -> Map.<String, Object>of("type", "SUBTITLE", "burnIn", true))
                    .toList();
            renderService.renderWithSubtitleBurnIn(jobId, sourcePath, outputPath, preset,
                    startTime, duration, subtitleTracks);
        } else {
            renderService.transcodeWithClipping(jobId, sourcePath, outputPath, preset, startTime, duration);
        }
    }

    private void renderFromAiScript(String jobId, String aiScript, RenderPreset preset, String outputPath) throws Exception {
        log.info("JavaCVRenderProvider: rendering from AI script, job={}", jobId);
        renderService.renderPlaceholder(jobId, outputPath, preset);
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
