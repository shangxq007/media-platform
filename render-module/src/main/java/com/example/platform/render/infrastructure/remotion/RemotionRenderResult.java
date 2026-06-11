package com.example.platform.render.infrastructure.remotion;

import java.nio.file.Path;
import java.util.List;

public record RemotionRenderResult(
        String jobId,
        String compositionId,
        Path outputPath,
        String outputUri,
        long durationMs,
        int width,
        int height,
        int fps,
        String format,
        boolean success,
        List<String> logs,
        List<String> errors,
        int exitCode
) {
    public boolean isSuccess() {
        return success && exitCode == 0;
    }
}
