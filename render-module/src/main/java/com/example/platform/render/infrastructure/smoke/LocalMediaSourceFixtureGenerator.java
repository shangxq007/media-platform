package com.example.platform.render.infrastructure.smoke;

import com.example.platform.render.domain.render.local.LocalMediaSourceKind;
import com.example.platform.render.domain.render.local.LocalMediaSourceOrigin;
import com.example.platform.render.domain.render.local.LocalMediaSourceSpec;
import com.example.platform.render.domain.render.local.LocalRenderSmokeIssue;
import com.example.platform.render.domain.render.local.LocalRenderSmokeIssueCode;
import com.example.platform.render.domain.render.local.LocalRenderSmokePolicy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generates deterministic local media fixture files for the local runner.
 *
 * <p>Uses controlled FFmpeg commands to generate short testsrc/colorsource
 * MP4 files under the configured output root. No binary fixtures committed.
 * No remote downloads. No user media ingestion. No StorageRuntime.</p>
 *
 * <p>Safety model: all commands are platform-owned, built as {@code List<String>},
 * validated against binary allowlist, no shell invocation.</p>
 */
public final class LocalMediaSourceFixtureGenerator {

    private LocalMediaSourceFixtureGenerator() {}

    /**
     * Result of generating a media fixture.
     *
     * @param spec      media source spec for the generated fixture
     * @param issues    issues encountered during generation
     * @param blocked   true if generation was blocked by safety violations
     */
    public record FixtureGenerationResult(
            LocalMediaSourceSpec spec,
            List<LocalRenderSmokeIssue> issues,
            boolean blocked
    ) {}

    /**
     * Configuration for fixture generation.
     *
     * @param outputRoot  root directory for fixture output
     * @param width       video width
     * @param height      video height
     * @param durationSec duration in seconds
     * @param fps         frame rate
     * @param codec       video codec (e.g., "h264")
     * @param container   container format (e.g., "mp4")
     */
    public record FixtureConfig(
            Path outputRoot,
            int width,
            int height,
            double durationSec,
            int fps,
            String codec,
            String container
    ) {
        public FixtureConfig {
            Objects.requireNonNull(outputRoot, "outputRoot must not be null");
            Objects.requireNonNull(codec, "codec must not be null");
            Objects.requireNonNull(container, "container must not be null");
            if (width <= 0) throw new IllegalArgumentException("width must be positive");
            if (height <= 0) throw new IllegalArgumentException("height must be positive");
            if (durationSec <= 0) throw new IllegalArgumentException("durationSec must be positive");
            if (fps <= 0) throw new IllegalArgumentException("fps must be positive");
        }

        /**
         * Creates a default fixture config: 320x180, 3s, 30fps, h264, mp4.
         */
        public static FixtureConfig defaults(Path outputRoot) {
            return new FixtureConfig(outputRoot, 320, 180, 3.0, 30, "h264", "mp4");
        }
    }

    /**
     * Generates a deterministic input fixture MP4 under the controlled output root.
     *
     * <p>Uses FFmpeg testsrc or colorsource to create a short video.
     * The fixture path is always under the controlled output root.</p>
     *
     * @param config fixture generation config
     * @param policy local smoke policy (for binary allowlist and timeout)
     * @return fixture generation result
     */
    public static FixtureGenerationResult generate(FixtureConfig config, LocalRenderSmokePolicy policy) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        List<LocalRenderSmokeIssue> issues = new ArrayList<>();

        // Validate binary allowlist
        if (!policy.isBinaryAllowed("ffmpeg")) {
            issues.add(LocalRenderSmokeIssue.blocking(
                    LocalRenderSmokeIssueCode.COMMAND_ALLOWLIST_VIOLATION,
                    "Binary 'ffmpeg' is not on the allowlist"));
            return new FixtureGenerationResult(null, issues, true);
        }

        // Create output directory under controlled root
        Path fixtureDir = config.outputRoot().resolve("local-plan-smoke-003-real-media-source-caption-overlay");
        try {
            Files.createDirectories(fixtureDir);
        } catch (IOException e) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.INPUT_OUTPUT_DIRECTORY_UNAVAILABLE,
                    "Cannot create fixture directory: " + fixtureDir));
            return new FixtureGenerationResult(null, issues, true);
        }

        Path fixturePath = fixtureDir.resolve("input-fixture." + config.container());

        // Build FFmpeg command as List<String> — never as shell string
        List<String> args = List.of(
                "ffmpeg", "-y",
                "-f", "lavfi", "-i",
                "testsrc=duration=" + config.durationSec()
                        + ":size=" + config.width() + "x" + config.height()
                        + ":rate=" + config.fps(),
                "-c:v", mapCodec(config.codec()),
                "-pix_fmt", "yuv420p",
                "-movflags", "+faststart",
                fixturePath.toString()
        );

        // Validate no shell invocation
        if (policy.containsShellInvocation(args)) {
            issues.add(LocalRenderSmokeIssue.blocking(
                    LocalRenderSmokeIssueCode.SHELL_INVOCATION_FORBIDDEN,
                    "Shell invocation detected in fixture generation command"));
            return new FixtureGenerationResult(null, issues, true);
        }

        // Execute FFmpeg to generate fixture
        LocalProcessRunner.LocalProcessExecutionResult result =
                LocalProcessRunner.execute(args, policy.timeoutSeconds());

        if (!result.success()) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.INPUT_FFMPEG_EXIT_NONZERO,
                    "Fixture generation FFmpeg exited with code " + result.exitCode()
                            + ": " + result.stderr()));
            return new FixtureGenerationResult(null, issues, false);
        }

        // Verify fixture file exists
        if (!Files.exists(fixturePath)) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.MEDIA_SOURCE_FILE_MISSING,
                    "Fixture file was not created: " + fixturePath));
            return new FixtureGenerationResult(null, issues, false);
        }

        // Build media source spec
        LocalMediaSourceSpec spec = new LocalMediaSourceSpec(
                LocalMediaSourceKind.CONTROLLED_LOCAL_FIXTURE,
                LocalMediaSourceOrigin.PLATFORM_GENERATED,
                fixturePath,
                config.container(),
                config.codec()
        );

        issues.add(LocalRenderSmokeIssue.info(
                LocalRenderSmokeIssueCode.SYNTHETIC_INPUT_REQUIRED,
                "Generated deterministic input fixture: " + fixturePath.getFileName()));

        return new FixtureGenerationResult(spec, issues, false);
    }

    private static String mapCodec(String codec) {
        if (codec == null) return "libx264";
        return switch (codec.toLowerCase()) {
            case "h264", "avc" -> "libx264";
            case "h265", "hevc" -> "libx265";
            case "vp8" -> "libvpx";
            case "vp9" -> "libvpx-vp9";
            default -> "libx264";
        };
    }
}
