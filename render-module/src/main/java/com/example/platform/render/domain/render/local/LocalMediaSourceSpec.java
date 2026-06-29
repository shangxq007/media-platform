package com.example.platform.render.domain.render.local;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Safe, typed specification for a local media source.
 *
 * <p>Only controlled local fixture sources are accepted.
 * User-provided paths, remote URLs, and storage references are rejected.</p>
 *
 * @param kind     source kind (must be CONTROLLED_LOCAL_FIXTURE)
 * @param origin   source origin (must be PLATFORM_GENERATED or CONTROLLED_TEST_FIXTURE)
 * @param path     local file path under controlled root
 * @param format   container format (e.g., "mp4")
 * @param codec    video codec (e.g., "h264")
 */
public record LocalMediaSourceSpec(
        LocalMediaSourceKind kind,
        LocalMediaSourceOrigin origin,
        Path path,
        String format,
        String codec
) {
    public LocalMediaSourceSpec {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(origin, "origin must not be null");
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(codec, "codec must not be null");
    }

    /**
     * Validates that the path is under the given controlled root.
     *
     * @param controlledRoot the controlled output root directory
     * @return true if path is under controlled root
     */
    public boolean isUnderControlledRoot(Path controlledRoot) {
        if (controlledRoot == null || path == null) return false;
        return path.toAbsolutePath().startsWith(controlledRoot.toAbsolutePath());
    }

    /**
     * Validates that the path does not contain traversal sequences.
     *
     * @return true if path is safe (no traversal)
     */
    public boolean isPathSafe() {
        if (path == null) return false;
        String pathStr = path.toString();
        // Reject path traversal attempts
        if (pathStr.contains("..")) return false;
        // Reject remote URLs (handle both raw and Path-normalized forms)
        String lower = pathStr.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("http:/") || lower.startsWith("https:/")
                || lower.startsWith("ftp://") || lower.startsWith("ftp:/")
                || lower.startsWith("rtmp://") || lower.startsWith("rtmp:/")) return false;
        // Reject storage internals
        if (pathStr.contains("bucket") || pathStr.contains("objectKey")
                || pathStr.contains("signedUrl") || pathStr.contains("materializedPath")) return false;
        return true;
    }
}
