package com.example.platform.render.infrastructure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FfmpegEnvironmentCheck implements RenderEnvironmentChecker {

    @Override
    public RenderEnvironmentCheckResult check(ExecutionMode mode, Path workingDir, Path outputDir) {
        if (mode == ExecutionMode.MOCK) {
            return RenderEnvironmentCheckResult.ok(
                    List.of(new RenderEnvironmentCheckResult.EnvironmentCheckEntry(
                            "ffmpeg", true, "MOCK mode: skipped")));
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int exit = p.waitFor();
            if (exit == 0) {
                return RenderEnvironmentCheckResult.ok(
                        List.of(new RenderEnvironmentCheckResult.EnvironmentCheckEntry(
                                "ffmpeg", true, "ffmpeg found")));
            }
        } catch (Exception e) {
            // fall through
        }
        return RenderEnvironmentCheckResult.failed(
                List.of(new RenderEnvironmentCheckResult.EnvironmentCheckEntry(
                        "ffmpeg", false, "ffmpeg not found or not executable")));
    }

    @Override
    public List<String> requiredBinaries(ExecutionMode mode) {
        if (mode == ExecutionMode.MOCK) return List.of();
        return List.of("ffmpeg");
    }
}
