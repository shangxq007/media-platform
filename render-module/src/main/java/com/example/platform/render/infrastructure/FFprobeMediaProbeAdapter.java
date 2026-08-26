package com.example.platform.render.infrastructure;

import com.example.platform.sandbox.LocalSandboxProcess;
import com.example.platform.sandbox.SandboxCancellation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
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
            List<String> command = List.of(
                "ffprobe", "-v", "quiet", "-print_format", "json",
                "-show_format", "-show_streams", filePath
            );
            Path input = Path.of(filePath).toAbsolutePath().normalize();
            Path workspace = input.getParent();
            var process = LocalSandboxProcess.execute(command, workspace, workspace, java.util.Set.of(input),
                    java.util.Map.of("PATH", "/usr/bin:/bin", "LANG", "C"),
                    java.time.Duration.ofSeconds(30), 1L << 20, SandboxCancellation.never());
            if (process.failure().isPresent()) return MediaProbeResult.failed(jobId,
                    process.failure().orElseThrow().code() + ": " + process.failure().orElseThrow().message());
            String json = process.stdout().utf8();
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
