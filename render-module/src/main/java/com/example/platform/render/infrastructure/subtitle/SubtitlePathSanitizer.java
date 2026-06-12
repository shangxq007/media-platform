package com.example.platform.render.infrastructure.subtitle;

import java.net.URI;
import java.nio.file.Path;

/**
 * Validates subtitle file paths to prevent path injection attacks.
 *
 * <p>Timeline JSON can contain subtitle paths in {@code metadata.subtitlePath}
 * or {@code subtitle.path}. These paths must be validated before passing to
 * FFmpeg's {@code subtitles=} filter, which interprets the path as a filesystem
 * reference.
 *
 * <h3>Threat model</h3>
 * <ul>
 *   <li>Absolute paths: {@code /etc/passwd}</li>
 *   <li>Traversal: {@code ../../../secret.srt}</li>
 *   <li>URL schemes: {@code file://}, {@code http://}, {@code ffmpeg protocol}</li>
 *   <li>Encoded traversal: {@code %2e%2e/secret.srt}</li>
 *   <li>Filter separator injection: {@code path,force_style=...}</li>
 *   <li>Null byte: {@code path\0.srt}</li>
 * </ul>
 */
public final class SubtitlePathSanitizer {

    private SubtitlePathSanitizer() {}

    /**
     * Validate and sanitize a subtitle path from timeline JSON.
     *
     * <p>Only allows paths that are:
     * <ul>
     *   <li>Relative (no leading /)</li>
     *   <li>Contain no traversal (..)</li>
     *   <li>Contain no URL scheme</li>
     *   <li>Contain no filter separator characters</li>
     *   <li>Under the expected storage root</li>
     * </ul>
     *
     * @param rawPath the raw path from timeline JSON
     * @param storageRoot the expected storage root prefix (e.g., "/data/platform")
     * @return the validated path, or null if the path is unsafe
     */
    public static String sanitize(String rawPath, String storageRoot) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }

        String path = rawPath.trim();

        // Reject URL schemes
        if (hasUrlScheme(path)) {
            return null;
        }

        // Reject null bytes
        if (path.contains("\0")) {
            return null;
        }

        // Reject filter separator characters (FFmpeg filter syntax)
        if (path.contains(",") || path.contains(";") || path.contains("[")) {
            return null;
        }

        // Reject absolute paths
        if (path.startsWith("/")) {
            // If storageRoot is specified, allow paths under it
            if (storageRoot != null && path.startsWith(storageRoot)) {
                return validateRelativePath(path, storageRoot);
            }
            return null;
        }

        // Reject Windows drive paths
        if (path.length() >= 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':') {
            return null;
        }

        // Reject backslash traversal
        if (path.contains("\\")) {
            return null;
        }

        // Validate as relative path
        return validateRelativePath(path, storageRoot);
    }

    /**
     * Validate a path for traversal after basic sanitization.
     */
    private static String validateRelativePath(String path, String storageRoot) {
        // Reject traversal
        if (path.contains("..")) {
            return null;
        }

        // Reject encoded traversal
        String lower = path.toLowerCase();
        if (lower.contains("%2e%2e") || lower.contains("%2f") || lower.contains("%5c")
                || lower.contains("%252e") || lower.contains("%252f")) {
            return null;
        }

        // If storageRoot specified, verify path is under it
        if (storageRoot != null && !storageRoot.isBlank()) {
            try {
                Path resolved = Path.of(storageRoot).resolve(path).normalize();
                Path root = Path.of(storageRoot).normalize();
                if (!resolved.startsWith(root)) {
                    return null;
                }
            } catch (Exception e) {
                return null;
            }
        }

        return path;
    }

    /**
     * Check if a path contains a URL scheme (file://, http://, etc.).
     */
    private static boolean hasUrlScheme(String path) {
        String lower = path.toLowerCase();
        // Check common schemes
        if (lower.startsWith("file://") || lower.startsWith("http://") || lower.startsWith("https://")) {
            return true;
        }
        // Check ffmpeg protocol pseudo-paths
        if (lower.startsWith("concat:") || lower.startsWith("subfile:") || lower.startsWith("data:")) {
            return true;
        }
        // Check for scheme pattern (letter(s) followed by ://)
        int colonIdx = lower.indexOf("://");
        if (colonIdx > 0 && colonIdx < 10) {
            for (int i = 0; i < colonIdx; i++) {
                char c = lower.charAt(i);
                if (!Character.isLetter(c) && c != '+' && c != '-' && c != '.') {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Create a safe temp subtitle path under the given directory.
     * The filename is platform-generated and does not come from user input.
     *
     * @param tempDir the temp directory
     * @param extension the file extension (e.g., ".srt", ".vtt")
     * @return a safe path under tempDir
     */
    public static Path createSafeTempPath(Path tempDir, String extension) {
        String safeName = "subtitle-" + java.util.UUID.randomUUID() + extension;
        return tempDir.resolve(safeName);
    }
}
