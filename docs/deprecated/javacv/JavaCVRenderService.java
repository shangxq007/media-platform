package com.example.platform.render.infrastructure;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameFilter;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
public class JavaCVRenderService {

    private static final Logger log = LoggerFactory.getLogger(JavaCVRenderService.class);

    private final MediaProbeService mediaProbeService;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public JavaCVRenderService(MediaProbeService mediaProbeService) {
        this.mediaProbeService = mediaProbeService;
    }

    public MediaProbeService getMediaProbeService() {
        return mediaProbeService;
    }

    /**
     * Transcode a video file with the given preset.
     */
    public void transcode(String jobId, String inputPath, String outputPath, RenderPreset preset) throws Exception {
        log.info("JavaCVRenderService: transcoding job={} input={} preset={}", jobId, inputPath, preset.key());

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath)) {
            grabber.start();

            int width = preset.width();
            int height = preset.height();
            int frameRate = preset.frameRate();

            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, width, height)) {
                configureRecorder(recorder, preset, grabber);
                recorder.start();

                Frame frame;
                while ((frame = grabber.grabImage()) != null) {
                    recorder.record(frame);
                }

                recorder.stop();
            }
            grabber.stop();
        }

        log.info("JavaCVRenderService: transcode complete job={} output={}", jobId, outputPath);
    }

    /**
     * Transcode with time clipping (start/duration in seconds).
     */
    public void transcodeWithClipping(String jobId, String inputPath, String outputPath,
                                       RenderPreset preset, double startTime, double duration) throws Exception {
        log.info("JavaCVRenderService: transcoding with clipping job={} start={}s duration={}s",
                jobId, startTime, duration);

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath)) {
            grabber.start();

            int width = preset.width();
            int height = preset.height();
            int frameRate = preset.frameRate();

            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, width, height)) {
                configureRecorder(recorder, preset, grabber);
                recorder.start();

                long startFrame = (long) (startTime * grabber.getFrameRate());
                long durationFrames = (long) (duration * grabber.getFrameRate());

                grabber.setVideoFrameNumber((int) startFrame);

                Frame frame;
                long frameCount = 0;
                while ((frame = grabber.grabImage()) != null && frameCount < durationFrames) {
                    recorder.record(frame);
                    frameCount++;
                }

                recorder.stop();
            }
            grabber.stop();
        }

        log.info("JavaCVRenderService: clipping transcode complete job={}", jobId);
    }

    /**
     * Render with subtitle burn-in using FFmpeg filtergraph.
     */
    public void renderWithSubtitleBurnIn(String jobId, String inputPath, String outputPath,
                                          RenderPreset preset, double startTime, double duration,
                                          List<Map<String, Object>> subtitleTracks) throws Exception {
        log.info("JavaCVRenderService: rendering with subtitle burn-in job={} tracks={}",
                jobId, subtitleTracks.size());

        // Build filter complex for subtitle burn-in
        StringBuilder filterComplex = buildSubtitleFilterComplex(subtitleTracks, preset);

        if (filterComplex.length() == 0) {
            // No valid subtitle filters, fall back to plain transcode
            transcodeWithClipping(jobId, inputPath, outputPath, preset, startTime, duration);
            return;
        }

        // Use FFmpegFrameFilter for subtitle burn-in
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath)) {
            grabber.start();

            int width = preset.width();
            int height = preset.height();
            int frameRate = preset.frameRate();

            try (FFmpegFrameFilter filter = new FFmpegFrameFilter(filterComplex.toString(), width, height)) {
                filter.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
                filter.start();

                try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, width, height)) {
                    configureRecorder(recorder, preset, grabber);
                    recorder.start();

                    long startFrame = (long) (startTime * grabber.getFrameRate());
                    long durationFrames = (long) (duration * grabber.getFrameRate());

                    grabber.setVideoFrameNumber((int) startFrame);

                    Frame frame;
                    long frameCount = 0;
                    while ((frame = grabber.grabImage()) != null && frameCount < durationFrames) {
                        filter.push(frame);
                        Frame filteredFrame = filter.pull();
                        if (filteredFrame != null) {
                            recorder.record(filteredFrame);
                        }
                        frameCount++;
                    }

                    recorder.stop();
                }
                filter.stop();
            }
            grabber.stop();
        }

        log.info("JavaCVRenderService: subtitle burn-in render complete job={}", jobId);
    }

    /**
     * Generate a placeholder video with the given preset.
     */
    public void renderPlaceholder(String jobId, String outputPath, RenderPreset preset) throws Exception {
        log.info("JavaCVRenderService: rendering placeholder job={} preset={}", jobId, preset.key());

        int width = preset.width();
        int height = preset.height();
        int frameRate = preset.frameRate();
        int durationSeconds = 5;

        try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, width, height)) {
            recorder.setFormat("mp4");
            recorder.setVideoCodec(mapCodecId(preset.videoCodec()));
            recorder.setFrameRate(frameRate);
            recorder.setVideoBitrate(preset.videoBitrateKbps() * 1000);
            recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
            recorder.setAudioChannels(preset.audioChannels());
            recorder.setSampleRate(preset.sampleRate());
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
            recorder.start();

            Java2DFrameConverter converter = new Java2DFrameConverter();
            int totalFrames = durationSeconds * frameRate;

            for (int i = 0; i < totalFrames; i++) {
                Frame frame = converter.convert(createPlaceholderImage(width, height, i, totalFrames));
                recorder.record(frame);
            }

            recorder.stop();
            recorder.release();
        }

        log.info("JavaCVRenderService: placeholder complete job={} output={}", jobId, outputPath);
    }

    // --- Private helpers ---

    private void configureRecorder(FFmpegFrameRecorder recorder, RenderPreset preset,
                                    FFmpegFrameGrabber grabber) {
        recorder.setFormat("mp4");
        recorder.setVideoCodec(mapCodecId(preset.videoCodec()));
        recorder.setFrameRate(preset.frameRate());
        recorder.setVideoBitrate(preset.videoBitrateKbps() * 1000);
        recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
        recorder.setAudioChannels(grabber.getAudioChannels() > 0 ? grabber.getAudioChannels() : preset.audioChannels());
        recorder.setSampleRate(grabber.getSampleRate() > 0 ? grabber.getSampleRate() : preset.sampleRate());
        recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);

        // GPU-specific encoder options
        if (preset.requiresGpu()) {
            configureGpuEncoder(recorder, preset);
        }
    }

    private void configureGpuEncoder(FFmpegFrameRecorder recorder, RenderPreset preset) {
        String codec = preset.videoCodec();
        switch (codec) {
            case "h264_nvenc":
                // NVIDIA NVENC H.264 encoder options
                recorder.setVideoOption("preset", "p4");        // balanced preset
                recorder.setVideoOption("tune", "hq");          // high quality
                recorder.setVideoOption("rc", "vbr");           // variable bitrate
                recorder.setVideoOption("cq", "23");            // quality level
                log.info("JavaCVRenderService: configured NVENC H.264 GPU encoder");
                break;
            case "hevc_nvenc":
                // NVIDIA NVENC H.265 encoder options
                recorder.setVideoOption("preset", "p4");
                recorder.setVideoOption("tune", "hq");
                recorder.setVideoOption("rc", "vbr");
                recorder.setVideoOption("cq", "28");
                log.info("JavaCVRenderService: configured NVENC H.265 GPU encoder");
                break;
            case "vp9_vaapi":
                // Intel/AMD VAAPI VP9 encoder options
                recorder.setVideoOption("quality", "balanced");
                log.info("JavaCVRenderService: configured VAAPI VP9 GPU encoder");
                break;
            default:
                log.warn("JavaCVRenderService: unknown GPU codec '{}', falling back to default", codec);
                break;
        }
    }

    private int mapCodecId(String codec) {
        return switch (codec) {
            case "libx264" -> avcodec.AV_CODEC_ID_H264;
            case "libx265" -> avcodec.AV_CODEC_ID_HEVC;
            case "libvpx-vp9" -> avcodec.AV_CODEC_ID_VP9;
            // GPU-accelerated codecs
            case "h264_nvenc" -> avcodec.AV_CODEC_ID_H264;  // NVENC wraps H.264
            case "hevc_nvenc" -> avcodec.AV_CODEC_ID_HEVC;  // NVENC wraps HEVC
            case "vp9_vaapi" -> avcodec.AV_CODEC_ID_VP9;     // VAAPI wraps VP9
            default -> avcodec.AV_CODEC_ID_H264;
        };
    }

    private StringBuilder buildSubtitleFilterComplex(List<Map<String, Object>> subtitleTracks, RenderPreset preset) {
        StringBuilder filter = new StringBuilder();
        int cueIndex = 0;

        for (Map<String, Object> subTrack : subtitleTracks) {
            if (!Boolean.TRUE.equals(subTrack.get("burnIn"))) continue;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cues = (List<Map<String, Object>>) subTrack.getOrDefault("cues", List.of());
            if (cues.isEmpty()) continue;

            String fontFile = resolveFontFile(subTrack);
            String fontParam = (fontFile != null && !fontFile.isEmpty()) ? ":fontfile=" + fontFile : "";

            for (Map<String, Object> cue : cues) {
                String text = (String) cue.getOrDefault("text", "");
                if (text == null || text.isEmpty()) continue;

                double cueStart = ((Number) cue.getOrDefault("startTime", 0.0)).doubleValue();
                double cueEnd = ((Number) cue.getOrDefault("endTime", 0.0)).doubleValue();

                // Escape text for FFmpeg drawtext
                text = text.replace("'", "'\\\\''")
                        .replace(":", "\\:")
                        .replace(",", "\\,")
                        .replace("%", "%%");

                if (cueIndex > 0) filter.append(",");

                filter.append(String.format(
                        "drawtext=text='%s':fontsize=24:fontcolor=white:box=1:boxcolor=black@0.5" +
                        ":x=(w-text_w)/2:y=h-text_h-20:enable='between(t,%.1f,%.1f)'%s",
                        text, cueStart, cueEnd, fontParam
                ));
                cueIndex++;
            }
        }

        return filter;
    }

    private String resolveFontFile(Map<String, Object> subTrack) {
        String fontId = (String) subTrack.get("fontId");
        if (fontId == null) return null;

        @SuppressWarnings("unchecked")
        List<String> fallbackIds = (List<String>) subTrack.getOrDefault("fallbackFontIds", List.of());

        String fontPath = Path.of(System.getProperty("java.io.tmpdir"), "fonts", fontId + ".ttf").toString();
        if (java.nio.file.Files.exists(Path.of(fontPath))) {
            return fontPath;
        }

        for (String fallbackId : fallbackIds) {
            String fallbackPath = Path.of(System.getProperty("java.io.tmpdir"), "fonts", fallbackId + ".ttf").toString();
            if (java.nio.file.Files.exists(Path.of(fallbackPath))) {
                return fallbackPath;
            }
        }

        return null;
    }

    private BufferedImage createPlaceholderImage(int width, int height, int frame, int totalFrames) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = image.createGraphics();

        float progress = (float) frame / totalFrames;
        int r = (int) (progress * 60);
        int gr = (int) ((1 - progress) * 40);
        g.setColor(new Color(r + 20, gr + 30, 80));
        g.fillRect(0, 0, width, height);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 24));
        String text = String.format("Frame %d / %d", frame, totalFrames);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (width - fm.stringWidth(text)) / 2, height / 2);
        g.dispose();

        return image;
    }
}
