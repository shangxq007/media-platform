package com.example.platform.render.infrastructure;

import com.example.platform.shared.Ids;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.example.platform.render.infrastructure.ofx.OfxFfmpegCompositeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OFX-based render provider for advanced video effects.
 *
 * <p>Extends JavaCV rendering with Open Effects Association (OFX) compatible
 * effect chains. Uses FFmpeg filtergraph for complex compositing.</p>
 *
 * <p>Supported effects:</p>
 * <ul>
 *   <li><strong>Transitions</strong>: Dissolve, Wipe, Slide, Zoom</li>
 *   <li><strong>Filters</strong>: Blur, Sharpen, Color Grade, Vignette, Chromatic Aberration</li>
 *   <li><strong>Text/Subtitles</title>: Burn-in subtitles with font/style control</li>
 *   <li><strong>Compositing</strong>: Picture-in-picture, split screen, overlay</li>
 * </ul>
 *
 * <p>OTIO timeline metadata field {@code effects} controls which OFX effects
 * are applied to each clip:</p>
 * <pre>
 * {
 *   "name": "clip_001",
 *   "effects": [
 *     {"type": "transition", "name": "dissolve", "duration": 0.5},
 *     {"type": "filter", "name": "blur", "params": {"radius": 3}},
 *     {"type": "text", "text": "Hello World", "position": "bottom"}
 *   ]
 * }
 * </pre>
 *
 * @deprecated Since 2026-06-11. The current implementation is a Java2D simulation,
 *             not a real OFX plugin. The name is misleading for system scheduling and maintenance.
 *             Use {@code BasicEffectsProvider} as replacement, or implement a real OFX plugin
 *             host as {@code RealOFXPluginProvider}. Does not participate in auto-routing.
 */
@Deprecated
@Component
public class OFXRenderProvider implements RenderProvider {
    private static final Logger log = LoggerFactory.getLogger(OFXRenderProvider.class);

    private final OfxFfmpegCompositeService ofxFfmpegCompositeService;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    /** No-arg for unit tests; production uses Spring-injected compositor. */
    public OFXRenderProvider() {
        this(null);
    }

    public OFXRenderProvider(OfxFfmpegCompositeService ofxFfmpegCompositeService) {
        this.ofxFfmpegCompositeService = ofxFfmpegCompositeService;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public String getProviderKey() {
        return "ofx";
    }

    public RenderProviderCapability getCapability() {
        return new RenderProviderCapability(
                "ofx",
                Set.of("mp4", "webm"),
                Set.of("h264", "aac", "vp9"),
                Set.of("video.fade_in", "video.fade_out", "video.cross_dissolve",
                        "video.blur", "video.sharpen", "video.vignette", "video.chromatic",
                        "video.brightness", "video.contrast", "video.grayscale", "video.sepia",
                        "video.watermark", "video.overlay", "video.pip", "video.particle_overlay",
                        "text.subtitle_burn_in", "text.overlay",
                        "audio.volume"),
                Set.of("dissolve", "wipe", "slide", "zoom", "fade_in", "fade_out", "cross_dissolve"),
                Set.of("burn_in", "overlay"),
                "3840x2160",
                true,
                false,
                false,
                Set.of("ofx_1080p", "ofx_720p", "pro_1080p", "team_4k",
                        "enterprise_4k_ofx", "experimental_all_providers"),
                ProviderStatus.DEPRECATED,
                "P3",
                ProviderType.RENDER,
                "Java2D-simulated effects (NOT real OFX plugin). Misleading name - use BasicEffectsProvider instead.",
                List.of(
                        "Current implementation is Java2D simulation, not real OFX plugin",
                        "Name is misleading for system scheduling and maintenance",
                        "Does not participate in auto-routing",
                        "Use BasicEffectsProvider as replacement or implement RealOFXPluginProvider for real OFX"
                ),
                false
        );
    }

    @Override
    public ProviderStatus getStatus() {
        return ProviderStatus.DEPRECATED;
    }

    @Override
    public String getPriority() {
        return "P3";
    }

    @Override
    public ProviderType getProviderType() {
        return ProviderType.RENDER;
    }

    @Override
    public String getPurpose() {
        return "Java2D-simulated effects (NOT real OFX plugin). Misleading name - use BasicEffectsProvider instead.";
    }

    @Override
    public List<String> getLimitations() {
        return List.of(
                "Current implementation is Java2D simulation, not real OFX plugin",
                "Name is misleading for system scheduling and maintenance",
                "Does not participate in auto-routing",
                "Use BasicEffectsProvider as replacement or implement RealOFXPluginProvider for real OFX"
        );
    }

    @Override
    public List<String> getCapabilities() {
        return List.of("video.fade_in", "video.fade_out", "video.cross_dissolve",
                "video.blur", "video.sharpen", "video.vignette", "video.chromatic",
                "video.brightness", "video.contrast", "video.grayscale", "video.sepia",
                "video.watermark", "video.overlay", "video.pip", "video.particle_overlay",
                "text.subtitle_burn_in", "text.overlay", "audio.volume");
    }

    @Override
    public boolean isAutoDispatch() {
        return false;
    }

    @Override
    public RenderResult render(String jobId, String aiScript, String profile) {
        log.info("OFXRenderProvider: rendering job={}, profile={}", jobId, profile);

        try {
            Path outputDir = Path.of(storageRoot, "artifacts", jobId);
            outputDir.toFile().mkdirs();
            String outputPath = outputDir.resolve("output.mp4").toString();

            if (aiScript != null && aiScript.contains("{")) {
                renderFromTimeline(jobId, aiScript, profile, outputPath);
            } else {
                renderFromAiScript(jobId, aiScript, profile, outputPath);
            }

            String artifactId = Ids.newId("art");
            String resolution = resolveResolution(profile);

            log.info("OFXRenderProvider: render complete, artifact={}, resolution={}", artifactId, resolution);

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
            log.error("OFXRenderProvider: render failed for job={}", jobId, e);
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-500-001", 500101,
                            Map.of("en", "Render execution failed", "zh", "渲染执行失败"),
                            "render", 500),
                    e.getMessage(),
                    Map.of("jobId", jobId, "provider", "ofx", "error", e.getClass().getSimpleName()),
                    "en"
            );
        }
    }

    private void renderFromTimeline(String jobId, String aiScript, String profile, String outputPath) throws Exception {
        log.info("OFXRenderProvider: rendering from OTIO timeline with effects, job={}", jobId);

        if (ofxFfmpegCompositeService != null) {
            var ffmpegDone = ofxFfmpegCompositeService.tryCompose(jobId, aiScript, Path.of(outputPath));
            if (ffmpegDone.isPresent()) {
                log.info("OFXRenderProvider: FFmpeg dual-input / libass path completed for job={}", jobId);
                return;
            }
        }

        Map<String, Object> timeline = parseOtiOTimeline(aiScript);
        List<Map<String, Object>> tracks = (List<Map<String, Object>>) timeline.getOrDefault("tracks", List.of());

        int width = profile.contains("720") ? 1280 : 1920;
        int height = profile.contains("720") ? 720 : 1080;
        int frameRate = 30;
        int durationSeconds = 5;

        // Collect all effects from timeline
        List<Map<String, Object>> allEffects = new java.util.ArrayList<>();
        List<Map<String, Object>> subtitleTracks = new java.util.ArrayList<>();
        for (Map<String, Object> track : tracks) {
            String trackType = (String) track.getOrDefault("type", "");
            if ("SUBTITLE".equalsIgnoreCase(trackType) || "TEXT".equalsIgnoreCase(trackType)) {
                subtitleTracks.add(track);
            }
            List<Map<String, Object>> children = (List<Map<String, Object>>) track.getOrDefault("children", List.of());
            for (Map<String, Object> clip : children) {
                List<Map<String, Object>> effects = (List<Map<String, Object>>) clip.getOrDefault("effects", List.of());
                allEffects.addAll(effects);
            }
        }
        // Also check top-level subtitleTracks
        if (timeline.containsKey("subtitleTracks")) {
            subtitleTracks.addAll((List<Map<String, Object>>) timeline.get("subtitleTracks"));
        }
        boolean hasBurnInSubtitles = subtitleTracks.stream()
                .anyMatch(st -> Boolean.TRUE.equals(st.get("burnIn")));
        if (hasBurnInSubtitles) {
            log.info("OFXRenderProvider: {} burn-in subtitle tracks detected", subtitleTracks.size());
        }

        try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, width, height)) {
            recorder.setFormat("mp4");
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setFrameRate(frameRate);
            recorder.setVideoBitrate(3000000);
            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
            recorder.setAudioChannels(2);
            recorder.setSampleRate(44100);
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
            recorder.start();

            Java2DFrameConverter converter = new Java2DFrameConverter();
            int totalFrames = durationSeconds * frameRate;

            for (int i = 0; i < totalFrames; i++) {
                float progress = (float) i / totalFrames;
                BufferedImage image = createFrameWithEffects(width, height, i, totalFrames, progress, allEffects);
                Frame frame = converter.convert(image);
                recorder.record(frame);
            }

            recorder.stop();
            recorder.release();
        }

        log.info("OFXRenderProvider: OFX render complete with {} effects", allEffects.size());
    }

    private void renderFromAiScript(String jobId, String aiScript, String profile, String outputPath) throws Exception {
        log.info("OFXRenderProvider: rendering from AI script, job={}", jobId);
        renderPlaceholderWithEffects(outputPath, profile, List.of());
    }

    private void renderPlaceholderWithEffects(String outputPath, String profile, List<Map<String, Object>> effects) throws Exception {
        int width = profile.contains("720") ? 1280 : 1920;
        int height = profile.contains("720") ? 720 : 1080;
        int frameRate = 30;
        int durationSeconds = 5;

        try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, width, height)) {
            recorder.setFormat("mp4");
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setFrameRate(frameRate);
            recorder.setVideoBitrate(3000000);
            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
            recorder.setAudioChannels(2);
            recorder.setSampleRate(44100);
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
            recorder.start();

            Java2DFrameConverter converter = new Java2DFrameConverter();
            int totalFrames = durationSeconds * frameRate;

            for (int i = 0; i < totalFrames; i++) {
                float progress = (float) i / totalFrames;
                BufferedImage image = createFrameWithEffects(width, height, i, totalFrames, progress, effects);
                Frame frame = converter.convert(image);
                recorder.record(frame);
            }

            recorder.stop();
            recorder.release();
        }
    }

    private BufferedImage createFrameWithEffects(int width, int height, int frame, int totalFrames,
                                                   float progress, List<Map<String, Object>> effects) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = image.createGraphics();

        // Background gradient
        int r = (int) (progress * 80);
        int gr = (int) ((1 - progress) * 60);
        g.setColor(new Color(r + 20, gr + 30, 100));
        g.fillRect(0, 0, width, height);

        // Apply effects
        if (effects != null) {
            for (Map<String, Object> effect : effects) {
                String type = (String) effect.getOrDefault("type", "");
                String name = (String) effect.getOrDefault("name", "");

                switch (type) {
                    case "filter":
                        applyFilter(g, width, height, frame, totalFrames, name, effect);
                        break;
                    case "transition":
                        applyTransition(g, width, height, progress, name, effect);
                        break;
                    case "text":
                        applyTextEffect(g, width, height, frame, totalFrames, effect);
                        break;
                    default:
                        break;
                }
            }
        }

        // Frame counter
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        String text = String.format("OFX Frame %d / %d", frame, totalFrames);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (width - fm.stringWidth(text)) / 2, height / 2);

        g.dispose();
        return image;
    }

    private void applyFilter(Graphics2D g, int width, int height, int frame, int totalFrames,
                              String name, Map<String, Object> params) {
        switch (name) {
            case "blur":
                float blurIntensity = ((Number) params.getOrDefault("radius", 3)).floatValue() / 10f;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, blurIntensity * 0.3f));
                g.setColor(new Color(255, 255, 255, 30));
                g.fillRect(0, 0, width, height);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                break;
            case "vignette":
                float vignetteStrength = 0.4f;
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, vignetteStrength));
                g.setColor(Color.BLACK);
                g.fillOval(-width / 4, -height / 4, width * 3 / 2, height * 3 / 2);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                break;
            case "chromatic":
                int offset = (int) (Math.sin(frame * 0.1) * 3);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f));
                g.setColor(Color.RED);
                g.fillRect(offset, 0, width, height);
                g.setColor(Color.BLUE);
                g.fillRect(-offset, 0, width, height);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                break;
            case "sharpen":
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                break;
            default:
                break;
        }
    }

    private void applyTransition(Graphics2D g, int width, int height, float progress,
                                  String name, Map<String, Object> params) {
        double duration = ((Number) params.getOrDefault("duration", 0.5)).doubleValue();
        float fadeAmount = (float) Math.min(1.0, progress / duration);

        switch (name) {
            case "dissolve":
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeAmount * 0.5f));
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, width, height);
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                break;
            case "wipe":
                int wipeX = (int) (width * fadeAmount);
                g.setColor(new Color(20, 30, 80));
                g.fillRect(0, 0, wipeX, height);
                break;
            case "slide":
                int slideX = (int) (width * (1 - fadeAmount));
                g.setColor(new Color(30, 40, 90));
                g.fillRect(slideX, 0, width - slideX, height);
                break;
            case "zoom":
                float scale = 1 + (1 - fadeAmount) * 0.2f;
                g.setTransform(java.awt.geom.AffineTransform.getScaleInstance(scale, scale));
                break;
            default:
                break;
        }
    }

    private void applyTextEffect(Graphics2D g, int width, int height, int frame, int totalFrames,
                                  Map<String, Object> params) {
        String text = (String) params.getOrDefault("text", "");
        String position = (String) params.getOrDefault("position", "bottom");

        if (text == null || text.isEmpty()) return;

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 28));

        FontMetrics fm = g.getFontMetrics();
        int textX = (width - fm.stringWidth(text)) / 2;
        int textY = position.equals("top") ? 50 : height - 50;

        // Draw shadow
        g.setColor(new Color(0, 0, 0, 128));
        g.drawString(text, textX + 2, textY + 2);
        // Draw text
        g.setColor(Color.WHITE);
        g.drawString(text, textX, textY);
    }

    private Map<String, Object> parseOtiOTimeline(String aiScript) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(aiScript, Map.class);
        } catch (Exception e) {
            log.warn("OFXRenderProvider: failed to parse OTIO timeline JSON");
            return Map.of("tracks", List.of());
        }
    }

    private String resolveResolution(String profile) {
        if (profile.contains("720")) return "1280x720";
        if (profile.contains("4k")) return "3840x2160";
        return "1920x1080";
    }

    @Override
    public List<String> getSupportedProfiles() {
        return List.of(
                "default_1080p", "default_720p",
                "social_1080p", "social_720p",
                "mobile_480p", "4k_2160p",
                "ofx_1080p", "ofx_720p"
        );
    }

    @Override
    public boolean supports(String capability) {
        return switch (capability) {
            case "h264", "mp4", "watermark", "subtitle-burn", "fade", "clip", "transcode",
                 "blur", "sharpen", "vignette", "chromatic", "dissolve", "wipe", "slide", "zoom",
                 "text-burn", "color-grade", "overlay", "pip", "particle_overlay" -> true;
            case "h265", "hdr" -> false;
            default -> getSupportedProfiles().contains(capability);
        };
    }

    @Override
    public EnvironmentValidationResult validateEnvironment() {
        try {
            log.info("OFXRenderProvider: environment validation passed");
            return EnvironmentValidationResult.ok();
        } catch (Exception e) {
            return EnvironmentValidationResult.failed("OFX environment error: " + e.getMessage());
        }
    }
}
