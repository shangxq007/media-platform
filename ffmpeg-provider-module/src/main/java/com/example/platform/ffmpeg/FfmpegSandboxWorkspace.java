package com.example.platform.ffmpeg;

import java.nio.file.Path;
import java.util.Objects;

/** Exact bounded mutable roots supplied by the platform runtime. */
public record FfmpegSandboxWorkspace(
        Path root, Path temporaryRoot, Path outputStagingRoot, Path workingDirectory) {

    public FfmpegSandboxWorkspace {
        root = normalized(root, "root");
        temporaryRoot = normalized(temporaryRoot, "temporaryRoot");
        outputStagingRoot = normalized(outputStagingRoot, "outputStagingRoot");
        workingDirectory = normalized(workingDirectory, "workingDirectory");
    }

    public static FfmpegSandboxWorkspace under(Path root) {
        Path normalized = normalized(root, "root");
        return new FfmpegSandboxWorkspace(
                normalized,
                normalized.resolve(".sandbox-tmp"),
                normalized.resolve(".sandbox-output"),
                normalized);
    }

    private static Path normalized(Path path, String label) {
        Objects.requireNonNull(path, label);
        Path normalized = path.toAbsolutePath().normalize();
        if (!path.isAbsolute() || !path.equals(normalized)) {
            throw new IllegalArgumentException(label + " must be absolute and normalized");
        }
        return normalized;
    }
}
