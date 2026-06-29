package com.example.platform.render.infrastructure.smoke;

import com.example.platform.render.domain.render.local.LocalCaptionOverlaySpec;
import com.example.platform.render.domain.render.local.LocalRenderSmokeIssue;
import com.example.platform.render.domain.render.local.LocalRenderSmokeIssueCode;
import com.example.platform.render.domain.render.local.LocalRenderSmokePolicy;
import com.example.platform.render.domain.render.local.LocalRenderSmokeRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
     * Builds the FFmpeg command for a plan-driven local execution request.
     * Uses synthetic testsrc input with the output profile from the plan.
     *
     * @param width        output width
     * @param height       output height
     * @param durationSec  duration in seconds
     * @param fps          frame rate
     * @param videoCodec   target video codec (e.g. "h264")
     * @param container    target container (e.g. "mp4")
     * @param outputDir    output directory
     * @param policy       smoke policy
     * @return build result with args, output path, and any issues
     */
    public static BuildResult buildPlanDrivenTestsrc(int width, int height, double durationSec,
                                                      int fps, String videoCodec, String container,
                                                      java.nio.file.Path outputDir,
                                                      LocalRenderSmokePolicy policy) {
        Objects.requireNonNull(videoCodec, "videoCodec must not be null");
        Objects.requireNonNull(container, "container must not be null");
        Objects.requireNonNull(outputDir, "outputDir must not be null");
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
        Path outputPath = outputDir.resolve("output." + container);

        try {
            Files.createDirectories(outputDir);
        } catch (Exception e) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.OUTPUT_DIRECTORY_UNAVAILABLE,
                    "Cannot create output directory: " + outputDir));
            return new BuildResult(List.of(), outputPath, issues);
        }

        // Map codec name to FFmpeg encoder
        String encoder = mapCodecToEncoder(videoCodec);

        // Build args as List<String> — never as shell command
        List<String> args = List.of(
                FFMPEG_BINARY,
                "-y",
                "-f", "lavfi", "-i",
                "testsrc=duration=" + durationSec
                        + ":size=" + width + "x" + height
                        + ":rate=" + fps,
                "-pix_fmt", "yuv420p",
                "-c:v", encoder,
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
     * Builds the FFmpeg command for a plan-driven local execution with caption overlay.
     * Generates a platform-owned ASS subtitle file from safe typed caption fields
     * and burns it in using the {@code ass} video filter.
     *
     * @param width        output width
     * @param height       output height
     * @param durationSec  duration in seconds
     * @param fps          frame rate
     * @param videoCodec   target video codec
     * @param container    target container
     * @param outputDir    output directory
     * @param captionSpecs safe caption overlay specs (may be empty)
     * @param policy       smoke policy
     * @return build result with args, output path, and any issues
     */
    public static BuildResult buildPlanDrivenTestsrcWithCaptions(
            int width, int height, double durationSec, int fps,
            String videoCodec, String container, Path outputDir,
            List<LocalCaptionOverlaySpec> captionSpecs,
            LocalRenderSmokePolicy policy) {
        Objects.requireNonNull(videoCodec, "videoCodec must not be null");
        Objects.requireNonNull(container, "container must not be null");
        Objects.requireNonNull(outputDir, "outputDir must not be null");
        Objects.requireNonNull(captionSpecs, "captionSpecs must not be null");
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
        Path outputPath = outputDir.resolve("output." + container);

        try {
            Files.createDirectories(outputDir);
        } catch (Exception e) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.OUTPUT_DIRECTORY_UNAVAILABLE,
                    "Cannot create output directory: " + outputDir));
            return new BuildResult(List.of(), outputPath, issues);
        }

        // Map codec name to FFmpeg encoder
        String encoder = mapCodecToEncoder(videoCodec);

        // If no captions, fall back to simple testsrc
        if (captionSpecs.isEmpty()) {
            return buildPlanDrivenTestsrc(width, height, durationSec, fps,
                    videoCodec, container, outputDir, policy);
        }

        // Generate platform-owned ASS subtitle file from safe typed fields
        Path assPath = outputDir.resolve("caption-overlay-input.ass");
        try {
            String assContent = buildAssContent(captionSpecs, width, height);
            Files.writeString(assPath, assContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.CAPTION_OVERLAY_RENDER_FAILED,
                    "Failed to write ASS subtitle file: " + e.getMessage()));
            return new BuildResult(List.of(), outputPath, issues);
        }

        // Build args as List<String> — never as shell command
        // The ASS file path is platform-generated, not user-provided
        // The ass= filter uses a platform-owned file path, not a user-provided filtergraph
        List<String> args = List.of(
                FFMPEG_BINARY,
                "-y",
                "-f", "lavfi", "-i",
                "testsrc=duration=" + durationSec
                        + ":size=" + width + "x" + height
                        + ":rate=" + fps,
                "-pix_fmt", "yuv420p",
                "-c:v", encoder,
                "-preset", "ultrafast",
                "-vf", "ass=" + assPath.toAbsolutePath(),
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
     * Builds a minimal ASS subtitle file content from safe typed caption specs.
     *
     * <p>This is a platform-owned internal method. It generates ASS content
     * from safe typed fields only — no raw ASS style input, no external subtitle
     * paths, no font paths.</p>
     *
     * <p>Text is sanitized: braces and backslashes are removed to prevent
     * ASS override tag injection.</p>
     */
    static String buildAssContent(List<LocalCaptionOverlaySpec> captionSpecs,
                                   int playResX, int playResY) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Script Info]\n");
        sb.append("Title: P2L.2 Local Caption Overlay Smoke\n");
        sb.append("ScriptType: v4.00+\n");
        sb.append("PlayResX: ").append(playResX).append("\n");
        sb.append("PlayResY: ").append(playResY).append("\n");
        sb.append("\n[V4+ Styles]\n");
        sb.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, ");
        sb.append("OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ");
        sb.append("ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, ");
        sb.append("Alignment, MarginL, MarginR, MarginV, Encoding\n");
        // Default style: white text, semi-transparent black background, bottom-center
        sb.append("Style: Default,DejaVu Sans,24,&H00FFFFFF,&H000000FF,&H00000000,&H80000000,");
        sb.append("0,0,0,0,100,100,0,0,1,2,0,2,10,10,30,1\n");
        sb.append("\n[Events]\n");
        sb.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");

        for (LocalCaptionOverlaySpec spec : captionSpecs) {
            String start = formatAssTime(spec.startSec());
            String end = formatAssTime(spec.endSec());
            String text = sanitizeForAss(spec.text());
            sb.append("Dialogue: 0,").append(start).append(",").append(end)
                    .append(",Default,,0,0,0,,").append(text).append("\n");
        }

        return sb.toString();
    }

    /**
     * Sanitizes text for safe inclusion in ASS Dialogue lines.
     * Removes braces (prevents override tag injection) and limits length.
     */
    private static String sanitizeForAss(String text) {
        if (text == null) return "";
        String result = text;
        if (result.length() > 200) {
            result = result.substring(0, 200);
        }
        // Remove braces to prevent ASS override tag injection
        result = result.replace("{", "");
        result = result.replace("}", "");
        // Remove backslashes that could form override sequences
        result = result.replace("\\", "");
        // Convert newlines to ASS line break
        result = result.replace("\r\n", "\\N");
        result = result.replace("\r", "\\N");
        result = result.replace("\n", "\\N");
        return result;
    }

    /**
     * Formats seconds into ASS time format (H:MM:SS.cc).
     */
    private static String formatAssTime(double seconds) {
        int h = (int) (seconds / 3600);
        int m = (int) ((seconds % 3600) / 60);
        double s = seconds % 60;
        return String.format("%d:%02d:%05.2f", h, m, s);
    }

    /**
     * Maps a semantic codec name to an FFmpeg encoder name.
     */
    private static String mapCodecToEncoder(String codec) {
        if (codec == null) return "libx264";
        return switch (codec.toLowerCase()) {
            case "h264", "avc" -> "libx264";
            case "h265", "hevc" -> "libx265";
            case "vp8" -> "libvpx";
            case "vp9" -> "libvpx-vp9";
            default -> "libx264";
        };
    }

    /**
     * Extracts the binary name from a command argument list.
     */
    public static String extractBinary(List<String> args) {
        if (args == null || args.isEmpty()) return null;
        return args.get(0);
    }
}
