package com.example.platform.render.infrastructure;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class JavaCVMediaProbeAdapter implements MediaProbeAdapter {
    private static final Logger log = LoggerFactory.getLogger(JavaCVMediaProbeAdapter.class);

    @Override
    public MediaProbeResult probe(String jobId, String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return MediaProbeResult.failed(jobId, "File not found: " + filePath);
        }

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(file)) {
            grabber.start();

            List<String> warnings = new ArrayList<>();
            int width = grabber.getImageWidth();
            int height = grabber.getImageHeight();
            double durationMs = grabber.getLengthInTime() / 1000.0;

            if (width == 0 || height == 0) warnings.add("No video stream detected");
            if (durationMs <= 0) warnings.add("Duration is zero or unknown");

            Map<String, String> streamMeta = collectStreamMetadata(grabber);
            String pixelFormat = resolvePixelFormatName(grabber);
            ColorProbeMetadata color = ColorProbeMetadataExtractor.fromStreamMetadata(streamMeta, pixelFormat);
            if (color.hdr()) {
                warnings.add("HDR color transfer detected");
            }

            MediaProbeResult result = new MediaProbeResult(
                    jobId,
                    true,
                    filePath,
                    file.length(),
                    durationMs,
                    width,
                    height,
                    grabber.getVideoCodecName(),
                    grabber.getAudioCodecName(),
                    grabber.getFrameRate(),
                    grabber.getVideoBitrate(),
                    grabber.getAudioChannels(),
                    grabber.getSampleRate(),
                    warnings,
                    "",
                    color
            );

            grabber.stop();
            log.info("JavaCVMediaProbeAdapter: probed {} — {}x{} {}fps codec={}",
                    file.getName(), result.width(), result.height(),
                    String.format("%.2f", result.frameRate()), result.videoCodec());

            return result;
        } catch (Exception e) {
            log.error("JavaCVMediaProbeAdapter: failed to probe {}", filePath, e);
            return MediaProbeResult.failed(jobId, "Probe failed: " + e.getMessage());
        }
    }

    private static Map<String, String> collectStreamMetadata(FFmpegFrameGrabber grabber) {
        Map<String, String> meta = new LinkedHashMap<>();
        for (String key : List.of(
                "color_space", "color_primaries", "color_transfer", "color_range",
                "pix_fmt", "pixel_format", "chroma_location")) {
            String value = grabber.getMetadata(key);
            if (value != null && !value.isBlank()) {
                meta.put(key, value.trim());
            }
        }
        return meta;
    }

    private static String resolvePixelFormatName(FFmpegFrameGrabber grabber) {
        String fromMeta = grabber.getMetadata("pix_fmt");
        if (fromMeta != null && !fromMeta.isBlank()) {
            return fromMeta.trim();
        }
        int pixFmt = grabber.getPixelFormat();
        if (pixFmt >= 0) {
            try {
                return avutil.av_get_pix_fmt_name(pixFmt).getString();
            } catch (Exception ignored) {
                return String.valueOf(pixFmt);
            }
        }
        return "";
    }

    @Override
    public boolean isAvailable() {
        try {
            Class.forName("org.bytedeco.javacv.FFmpegFrameGrabber");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
