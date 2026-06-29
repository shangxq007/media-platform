package com.example.platform.render.infrastructure.smoke;

import com.example.platform.render.domain.render.local.LocalRenderSmokeIssue;
import com.example.platform.render.domain.render.local.LocalRenderSmokeIssueCode;
import com.example.platform.render.domain.render.local.LocalRenderSmokePolicy;
import com.example.platform.render.domain.render.local.LocalRenderSmokeRequest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds controlled FFmpeg argument lists for local smoke execution.
 *
 * <p>All arguments are built as {@link List<String>} — never as shell strings.
 * The command is entirely platform-owned; no user-provided filtergraph or command
 * input is accepted.</p>
 *
 * <p>The {@code testsrc} lavfi input is a platform-owned internal smoke expression,
 * not a user-provided raw filtergraph.</p>
 */
public final class LocalFfmpegSmokeCommandBuilder {

    private static final String FFMPEG_BINARY = "ffmpeg";

    private LocalFfmpegSmokeCommandBuilder() {}

    /**
     * Result of building a smoke command.
     */
    public record BuildResult(
            List<String> args,
            Path outputPath,
            List<LocalRenderSmokeIssue> issues
    ) {
        public BuildResult {
            args = args == null ? List.of() : List.copyOf(args);
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }

    /**
     * Builds the FFmpeg command for the testsrc-h264-mp4 smoke scenario.
     *
     * @param request the smoke request
     * @param policy  the smoke policy
     * @return build result with args, output path, and any issues
     */
    public static BuildResult buildTestsrcH264Mp4(LocalRenderSmokeRequest request, LocalRenderSmokePolicy policy) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        List<LocalRenderSmokeIssue> issues = new ArrayList<>();

        // Validate binary allowlist
        if (!policy.isBinaryAllowed(FFMPEG_BINARY)) {
            issues.add(LocalRenderSmokeIssue.blocking(
                    LocalRenderSmokeIssueCode.COMMAND_ALLOWLIST_VIOLATION,
                    "Binary '" + FFMPEG_BINARY + "' is not on the allowlist"));
            return new BuildResult(List.of(), null, issues);
        }

        // Prepare output directory
        Path smokeDir = request.outputRoot().resolve(request.smokeId().value());
        Path outputPath = smokeDir.resolve("output.mp4");

        try {
            Files.createDirectories(smokeDir);
        } catch (Exception e) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.OUTPUT_DIRECTORY_UNAVAILABLE,
                    "Cannot create output directory: " + smokeDir));
            return new BuildResult(List.of(), outputPath, issues);
        }

        // Build args as List<String> — never as shell command
        // testsrc is a platform-owned internal lavfi smoke input, not user-provided filtergraph
        List<String> args = List.of(
                FFMPEG_BINARY,
                "-y",
                "-f", "lavfi", "-i",
                "testsrc=duration=" + request.durationSec()
                        + ":size=" + request.width() + "x" + request.height()
                        + ":rate=" + request.fps(),
                "-pix_fmt", "yuv420p",
                "-c:v", "libx264",
                "-preset", "ultrafast",
                outputPath.toString()
        );

        // Validate no shell invocation patterns
        if (policy.containsShellInvocation(args)) {
            issues.add(LocalRenderSmokeIssue.blocking(
                    LocalRenderSmokeIssueCode.SHELL_INVOCATION_FORBIDDEN,
                    "Shell invocation detected in built arguments"));
            return new BuildResult(List.of(), outputPath, issues);
        }

        return new BuildResult(args, outputPath, issues);
    }

    /**
     * Extracts the binary name from a command argument list.
     */
    public static String extractBinary(List<String> args) {
        if (args == null || args.isEmpty()) return null;
        return args.get(0);
    }
}
