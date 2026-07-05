package com.example.platform.render.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * FFprobe-based media probe adapter.
 * Replaces deprecated JavaCVMediaProbeAdapter.
 */
@Component
public class FFprobeMediaProbeAdapter implements MediaProbeAdapter {

    private static final Logger log = LoggerFactory.getLogger(FFprobeMediaProbeAdapter.class);

    @Override
    public MediaProbeResult probe(String jobId, String filePath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "ffprobe", "-v", "quiet", "-print_format", "json",
                "-show_format", "-show_streams", filePath
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return MediaProbeResult.failed(jobId, "ffprobe timeout");
            }

            if (process.exitValue() != 0) {
                return MediaProbeResult.failed(jobId, "ffprobe failed: " + process.exitValue());
            }

            String json = output.toString();
            String duration = extractJsonValue(json, "duration");
            String width = extractJsonValue(json, "width");
            String height = extractJsonValue(json, "height");
            String codecName = extractJsonValue(json, "codec_name");

            return new MediaProbeResult(
                jobId, true, filePath, 0,
                duration != null ? Double.parseDouble(duration) * 1000 : 0,
                width != null ? Integer.parseInt(width) : 0,
                height != null ? Integer.parseInt(height) : 0,
                codecName, "", 0, 0, 0, 0,
                List.of(), null, ColorProbeMetadata.empty()
            );
        } catch (Exception e) {
            log.error("FFprobe failed for {}: {}", filePath, e.getMessage());
            return MediaProbeResult.failed(jobId, "ffprobe error: " + e.getMessage());
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            Process p = new ProcessBuilder("ffprobe", "-version").start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(":", idx);
        if (colonIdx < 0) return null;
        int start = colonIdx + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;
        char quote = json.charAt(start);
        if (quote == '"') {
            int end = json.indexOf("\"", start + 1);
            return end > start ? json.substring(start + 1, end) : null;
        } else {
            int end = start;
            while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.')) end++;
            return end > start ? json.substring(start, end) : null;
        }
    }
}
