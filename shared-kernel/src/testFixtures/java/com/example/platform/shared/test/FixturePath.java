package com.example.platform.shared.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Utility for locating test fixture directories.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>{@code GOLDEN_PROJECT_DIR} env var → assets subdirectory</li>
 *   <li>Walk up from {@code user.dir} to find {@code platform/test-assets/golden-render-project-v1}</li>
 *   <li>Walk up from {@code user.dir} to find {@code platform/docs}</li>
 * </ol>
 *
 * <p>This class is test-only and should never be used in production code.
 */
public final class FixturePath {

    private static final int MAX_DEPTH = 6;

    private FixturePath() {}

    /**
     * Locate the Golden Project assets directory.
     *
     * @return absolute path to {@code test-assets/golden-render-project-v1/assets}
     * @throws IllegalStateException if not found
     */
    public static Path goldenProjectAssets() {
        String env = System.getenv("GOLDEN_PROJECT_DIR");
        if (env != null && !env.isBlank()) {
            Path p = Path.of(env).resolve("assets");
            if (Files.isDirectory(p)) return p.toAbsolutePath().normalize();
        }
        Path found = findUpward("test-assets/golden-render-project-v1/assets");
        if (found != null) return found;
        throw new IllegalStateException(
                "Cannot locate Golden Project assets. " +
                "Set GOLDEN_PROJECT_DIR or run: bash test-assets/golden-render-project-v1/scripts/generate-assets.sh");
    }

    /**
     * Locate the Golden Project root (manifests, scripts, etc).
     *
     * @return absolute path to {@code test-assets/golden-render-project-v1/}
     */
    public static Path goldenProjectRoot() {
        String env = System.getenv("GOLDEN_PROJECT_DIR");
        if (env != null && !env.isBlank()) {
            Path p = Path.of(env);
            if (Files.isDirectory(p)) return p.toAbsolutePath().normalize();
        }
        Path found = findUpward("test-assets/golden-render-project-v1");
        if (found != null) return found;
        throw new IllegalStateException(
                "Cannot locate Golden Project root. " +
                "Set GOLDEN_PROJECT_DIR or run: bash test-assets/golden-render-project-v1/scripts/generate-assets.sh");
    }

    /**
     * Locate the platform docs directory.
     *
     * @return absolute path to {@code docs/} (which contains media-rendering/)
     */
    public static Path docs() {
        Path found = findUpward("docs/media-rendering");
        if (found != null) return found.getParent();
        throw new IllegalStateException(
                "Cannot locate platform docs directory. Expected: platform/docs/media-rendering/");
    }

    /**
     * Locate a specific docs fixture file.
     *
     * @param relativePath relative to {@code docs/}, e.g. "media-rendering/examples/timeline-v1-full-sample.json"
     * @return absolute path to the fixture
     */
    public static Path docsFixture(String relativePath) {
        return docs().resolve(relativePath);
    }

    /**
     * Locate the platform root directory.
     *
     * @return absolute path to {@code platform/}
     */
    public static Path platformRoot() {
        Path found = findUpward("settings.gradle.kts");
        if (found != null) return found.getParent();
        throw new IllegalStateException("Cannot locate platform root directory");
    }

    /**
     * Walk up from {@code user.dir} to find a relative path.
     *
     * @param relativePath path segment to look for (e.g. "test-assets/golden-render-project-v1/assets")
     * @return absolute path if found, null otherwise
     */
    private static Path findUpward(String relativePath) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int i = 0; i < MAX_DEPTH; i++) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) return candidate.toAbsolutePath().normalize();
            Path parent = current.getParent();
            if (parent == null || parent.equals(current)) break;
            current = parent;
        }
        return null;
    }
}
