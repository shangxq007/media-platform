package com.example.platform.render.infrastructure.gpac;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.shared.Ids;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.render.domain.timeline.TimelineClip;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.render.infrastructure.RenderPreset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(prefix = "render.providers.gpac", name = "enabled", havingValue = "true")
public class GPACRenderProvider implements RenderProvider {

    private static final Logger log = LoggerFactory.getLogger(GPACRenderProvider.class);

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    private final ProcessToolRunner processToolRunner;
    private final Mp4BoxCommandFactory commandFactory;
    private final TimelineScriptParser timelineScriptParser;

    public GPACRenderProvider(ProcessToolRunner processToolRunner,
                               Mp4BoxCommandFactory commandFactory,
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
        log.info("GPACRenderProvider: rendering job={}, profile={}", jobId, profile);

        try {
            Path outputDir = Path.of(storageRoot, "artifacts", jobId);
            outputDir.toFile().mkdirs();
            String outputPath = outputDir.resolve("output.mp4").toString();

            RenderPreset preset = RenderPreset.fromProfile(profile);

            String artifactId = Ids.newId("art");

            String format = resolveOutputFormat(aiScript, profile);
            String mezzanine = resolveMezzaninePath(jobId, aiScript);

            if ("dash".equalsIgnoreCase(format) || "hls".equalsIgnoreCase(format) || "cmaf".equalsIgnoreCase(format)) {
                return packageStreaming(jobId, outputDir, preset, format, artifactId, mezzanine);
            }

            return packageMp4(jobId, outputPath, preset, artifactId, mezzanine);

        } catch (PlatformException e) {
            throw e;
        } catch (Exception e) {
            log.error("GPACRenderProvider: render failed for job={}", jobId, e);
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-500-001", 500101,
                            Map.of("en", "GPAC render failed", "zh", "GPAC渲染失败"),
                            "render", 500),
                    e.getMessage(),
                    Map.of("jobId", jobId, "provider", "gpac", "error", e.getClass().getSimpleName()),
                    "en"
            );
        }
    }

    private RenderResult packageStreaming(String jobId, Path outputDir, RenderPreset preset,
                                           String format, String artifactId, String mezzanine) throws Exception {
        log.info("GPACRenderProvider: packaging streaming format={}, job={}", format, jobId);

        String baseMp4 = outputDir.resolve("base.mp4").toString();
        prepareMezzanine(baseMp4, preset, mezzanine);

        List<String> args;
        String manifestPath;
        switch (format.toLowerCase()) {
            case "dash":
                manifestPath = outputDir.resolve("manifest.mpd").toString();
                args = commandFactory.buildDashCommand(baseMp4, manifestPath, 4000);
                break;
            case "hls":
                manifestPath = outputDir.resolve("master.m3u8").toString();
                args = commandFactory.buildHlsCommand(baseMp4, manifestPath, 6000);
                break;
            case "cmaf":
                manifestPath = outputDir.resolve("manifest.mpd").toString();
                args = commandFactory.buildCmafCommand(baseMp4, outputDir.toString(), 4000);
                break;
            default:
                throw new PlatformException(
                        new ConfigurableErrorCode("RENDER-400-002", 500402,
                                Map.of("en", "Unsupported streaming format", "zh", "不支持的流媒体格式"),
                                "render", 400),
                        "Unsupported format: " + format,
                        Map.of("jobId", jobId, "format", format),
                        "en"
                );
        }

        ToolExecutionRequest request = ToolExecutionRequest.withTimeout("MP4Box", args, 300_000);
        ToolExecutionResult result = processToolRunner.execute(request);

        if (result.isSuccess()) {
            String resolution = preset.width() + "x" + preset.height();
            return new RenderResult(
                    artifactId,
                    "localFsStorageProvider://artifacts/" + jobId + "/" + Path.of(manifestPath).getFileName(),
                    30L,
                    format,
                    resolution
            );
        } else {
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-500-003", 500503,
                            Map.of("en", "MP4Box packaging failed", "zh", "MP4Box打包失败"),
                            "render", 500),
                    "MP4Box failed: " + result.stderr(),
                    Map.of("jobId", jobId, "format", format),
                    "en"
            );
        }
    }

    private RenderResult packageMp4(String jobId, String outputPath, RenderPreset preset,
                                      String artifactId, String mezzanine) throws Exception {
        log.info("GPACRenderProvider: packaging MP4 job={}", jobId);

        prepareMezzanine(outputPath, preset, mezzanine);

        String faststartOutput = outputPath + ".faststart.mp4";
        List<String> args = commandFactory.buildFaststartCommand(outputPath, faststartOutput);

        ToolExecutionRequest request = ToolExecutionRequest.withTimeout("MP4Box", args, 120_000);
        ToolExecutionResult result = processToolRunner.execute(request);

        if (result.isSuccess()) {
            new File(faststartOutput).renameTo(new File(outputPath));
        }

        String resolution = preset.width() + "x" + preset.height();
        return new RenderResult(
                artifactId,
                "localFsStorageProvider://artifacts/" + jobId + "/output.mp4",
                30L,
                "mp4",
                resolution
        );
    }

    private void prepareMezzanine(String outputPath, RenderPreset preset, String mezzanine) throws Exception {
        if (mezzanine != null && Files.isRegularFile(Path.of(mezzanine))) {
            Files.copy(Path.of(mezzanine), Path.of(outputPath),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        createBaseMp4(outputPath, preset);
    }

    private String resolveMezzaninePath(String jobId, String aiScript) {
        Path jobOutput = Path.of(storageRoot, "artifacts", jobId, "output.mp4");
        if (Files.isRegularFile(jobOutput)) {
            return jobOutput.toString();
        }
        Path transcodeOutput = Path.of(storageRoot, "artifacts", jobId, "transcode-output.mp4");
        if (Files.isRegularFile(transcodeOutput)) {
            return transcodeOutput.toString();
        }
        Optional<TimelineSpec> timeline = timelineScriptParser.parse(aiScript);
        if (timeline.isPresent()) {
            Optional<TimelineClip> clip = timelineScriptParser.firstVideoClip(timeline.get());
            if (clip.isPresent() && clip.get().assetRef() != null) {
                String uri = clip.get().assetRef().storageUri();
                if (timelineScriptParser.mediaFileExists(uri, storageRoot)) {
                    return timelineScriptParser.resolveLocalPath(uri, storageRoot);
                }
            }
        }
        return null;
    }

    private String resolveOutputFormat(String aiScript, String profile) {
        if (profile != null) {
            if (profile.contains("dash")) {
                return "dash";
            }
            if (profile.contains("hls")) {
                return "hls";
            }
            if (profile.contains("cmaf")) {
                return "cmaf";
            }
        }
        if (aiScript != null && aiScript.contains("{")) {
            try {
                Map<String, Object> timeline = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(aiScript, Map.class);
                return (String) timeline.getOrDefault("format", "mp4");
            } catch (Exception ignored) {
                return "mp4";
            }
        }
        return "mp4";
    }

    private void createBaseMp4(String outputPath, RenderPreset preset) throws Exception {
        try (org.bytedeco.javacv.FFmpegFrameRecorder recorder =
                     new org.bytedeco.javacv.FFmpegFrameRecorder(outputPath, preset.width(), preset.height())) {
            recorder.setFormat("mp4");
            recorder.setVideoCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264);
            recorder.setFrameRate(preset.frameRate());
            recorder.setVideoBitrate(preset.videoBitrateKbps() * 1000);
            recorder.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P);
            recorder.setAudioChannels(preset.audioChannels());
            recorder.setSampleRate(preset.sampleRate());
            recorder.setAudioCodec(org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AAC);
            recorder.start();

            org.bytedeco.javacv.Java2DFrameConverter converter = new org.bytedeco.javacv.Java2DFrameConverter();
            int totalFrames = 5 * preset.frameRate();

            for (int i = 0; i < totalFrames; i++) {
                java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                        preset.width(), preset.height(), java.awt.image.BufferedImage.TYPE_3BYTE_BGR);
                java.awt.Graphics2D g = image.createGraphics();
                float progress = (float) i / totalFrames;
                g.setColor(new java.awt.Color((int) (progress * 60), (int) ((1 - progress) * 40), 80));
                g.fillRect(0, 0, preset.width(), preset.height());
                g.setColor(java.awt.Color.WHITE);
                g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 24));
                String text = String.format("GPAC Frame %d / %d", i, totalFrames);
                java.awt.FontMetrics fm = g.getFontMetrics();
                g.drawString(text, (preset.width() - fm.stringWidth(text)) / 2, preset.height() / 2);
                g.dispose();
                recorder.record(converter.convert(image));
            }

            recorder.stop();
            recorder.release();
        }
    }

    @Override
    public List<String> getSupportedProfiles() {
        return List.of("default_1080p", "default_720p", "social_1080p", "social_720p",
                "gpac_dash", "gpac_hls", "gpac_cmaf");
    }

    @Override
    public boolean supports(String capability) {
        return switch (capability) {
            case "mp4", "dash", "hls", "cmaf", "faststart", "multi-track", "subtitle-track" -> true;
            default -> getSupportedProfiles().contains(capability);
        };
    }

    @Override
    public EnvironmentValidationResult validateEnvironment() {
        try {
            ToolExecutionRequest request = ToolExecutionRequest.withTimeout(
                    "MP4Box", List.of("-version"), 10_000);
            ToolExecutionResult result = processToolRunner.execute(request);
            if (result.isSuccess()) {
                log.info("GPACRenderProvider: MP4Box available");
                return EnvironmentValidationResult.ok();
            }
            return EnvironmentValidationResult.failed("MP4Box returned non-zero exit code");
        } catch (Exception e) {
            log.warn("GPACRenderProvider: MP4Box not available: {}", e.getMessage());
            return EnvironmentValidationResult.failed("MP4Box not available: " + e.getMessage());
        }
    }
}
