package com.example.platform.render.domain.render.local;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Policy controlling local render smoke execution boundaries.
 *
 * <p>Execution is disabled by default. Must be explicitly enabled via
 * system property {@code media.platform.localSmoke.enabled=true}.</p>
 */
public record LocalRenderSmokePolicy(
        boolean allowExecution,
        int timeoutSeconds,
        Path outputRoot,
        boolean allowOverwrite,
        Set<String> allowedBinaries,
        boolean strictMode
) {
    public static final String ENABLE_PROPERTY = "media.platform.localSmoke.enabled";
    public static final String STRICT_PROPERTY = "media.platform.localSmoke.strict";
    public static final int DEFAULT_TIMEOUT_SECONDS = 20;
    public static final String DEFAULT_OUTPUT_ROOT = "/tmp/media-platform-local-smoke";

    public LocalRenderSmokePolicy {
        Objects.requireNonNull(outputRoot, "outputRoot must not be null");
        Objects.requireNonNull(allowedBinaries, "allowedBinaries must not be null");
        allowedBinaries = Set.copyOf(allowedBinaries);
        if (timeoutSeconds <= 0) throw new IllegalArgumentException("timeoutSeconds must be positive");
    }

    /**
     * Creates the default policy with execution disabled.
     */
    public static LocalRenderSmokePolicy defaultDisabled() {
        return new LocalRenderSmokePolicy(
                false,
                DEFAULT_TIMEOUT_SECONDS,
                Path.of(DEFAULT_OUTPUT_ROOT),
                true,
                Set.of("ffmpeg", "ffprobe"),
                false
        );
    }

    /**
     * Creates the default policy with execution enabled.
     */
    public static LocalRenderSmokePolicy defaultEnabled() {
        return new LocalRenderSmokePolicy(
                true,
                DEFAULT_TIMEOUT_SECONDS,
                Path.of(DEFAULT_OUTPUT_ROOT),
                true,
                Set.of("ffmpeg", "ffprobe"),
                false
        );
    }

    /**
     * Resolves the effective policy based on system properties.
     */
    public static LocalRenderSmokePolicy resolve() {
        boolean enabled = Boolean.getBoolean(ENABLE_PROPERTY);
        boolean strict = Boolean.getBoolean(STRICT_PROPERTY);
        String outputRootProp = System.getProperty("media.platform.localSmoke.outputRoot", DEFAULT_OUTPUT_ROOT);
        return new LocalRenderSmokePolicy(
                enabled,
                DEFAULT_TIMEOUT_SECONDS,
                Path.of(outputRootProp),
                true,
                Set.of("ffmpeg", "ffprobe"),
                strict
        );
    }

    /**
     * Validates that a binary is on the allowlist.
     */
    public boolean isBinaryAllowed(String binary) {
        return allowedBinaries.contains(binary);
    }

    /**
     * Validates that an argument list does not contain shell invocation patterns.
     */
    public boolean containsShellInvocation(List<String> args) {
        if (args == null || args.size() < 2) return false;
        for (int i = 0; i < args.size() - 1; i++) {
            String current = args.get(i);
            String next = args.get(i + 1);
            if (current == null || next == null) continue;
            String lowerCurrent = current.toLowerCase();
            if ((lowerCurrent.equals("sh") || lowerCurrent.equals("bash")
                    || lowerCurrent.equals("cmd") || lowerCurrent.equals("powershell"))
                    && next.equals("-c")) {
                return true;
            }
            // Also check single-arg patterns like "sh -c ..."
            String combined = (current + " " + next).toLowerCase();
            if (combined.equals("sh -c") || combined.equals("bash -c")
                    || combined.equals("cmd /c")) {
                return true;
            }
        }
        return false;
    }
}
