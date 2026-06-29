package com.example.platform.render.infrastructure.smoke;

import com.example.platform.render.domain.render.local.LocalRenderSmokeIssue;
import com.example.platform.render.domain.render.local.LocalRenderSmokeIssueCode;
import com.example.platform.render.domain.render.local.LocalRenderSmokeIssueSeverity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Validates render output using ffprobe.
 *
 * <p>Parses ffprobe default output format (key=value pairs) to avoid
 * adding external JSON parsing dependencies.</p>
 */
public final class LocalFfprobeValidator {

    private static final String FFPROBE_BINARY = "ffprobe";

    private LocalFfprobeValidator() {}

    /**
     * Validation result from ffprobe.
     */
    public record ValidationResult(
            boolean valid,
            int width,
            int height,
            double durationSec,
            String codec,
            String format,
            int ffprobeExitCode,
            List<LocalRenderSmokeIssue> issues
    ) {
        public ValidationResult {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }

    /**
     * Validates a media file using ffprobe.
     *
     * @param filePath       path to the media file
     * @param expectedWidth  expected width (0 to skip check)
     * @param expectedHeight expected height (0 to skip check)
     * @param minDuration    minimum expected duration in seconds
     * @param maxDuration    maximum expected duration in seconds
     * @param timeoutSeconds ffprobe timeout
     * @return validation result
     */
    public static ValidationResult validate(Path filePath, int expectedWidth, int expectedHeight,
                                             double minDuration, double maxDuration, int timeoutSeconds) {
        Objects.requireNonNull(filePath, "filePath must not be null");

        List<LocalRenderSmokeIssue> issues = new ArrayList<>();

        // Check file exists
        if (!Files.exists(filePath)) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.OUTPUT_FILE_MISSING,
                    "Output file does not exist: " + filePath));
            return new ValidationResult(false, 0, 0, 0, null, null, -1, issues);
        }

        // Build ffprobe command — controlled, no user input
        List<String> args = List.of(
                FFPROBE_BINARY,
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=codec_name,width,height,duration",
                "-show_entries", "format=format_name,duration",
                "-of", "default=noprint_wrappers=1:nokey=0",
                filePath.toString()
        );

        LocalProcessRunner.LocalProcessExecutionResult result =
                LocalProcessRunner.execute(args, timeoutSeconds);

        if (!result.success()) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.FFPROBE_EXIT_NONZERO,
                    "ffprobe exited with code " + result.exitCode() + ": " + result.stderr()));
            return new ValidationResult(false, 0, 0, 0, null, null, result.exitCode(), issues);
        }

        // Parse ffprobe output (key=value pairs)
        Map<String, String> parsed = parseFfprobeOutput(result.stdout());

        int width = parseInt(parsed.getOrDefault("width", "0"));
        int height = parseInt(parsed.getOrDefault("height", "0"));
        double duration = parseDouble(parsed.getOrDefault("duration", "0"));
        String codec = parsed.get("codec_name");
        String format = parsed.get("format_name");

        // Validate dimensions
        if (expectedWidth > 0 && width != expectedWidth) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.INVALID_OUTPUT_WIDTH,
                    "Expected width " + expectedWidth + " but got " + width));
        }
        if (expectedHeight > 0 && height != expectedHeight) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.INVALID_OUTPUT_HEIGHT,
                    "Expected height " + expectedHeight + " but got " + height));
        }

        // Validate duration
        if (duration < minDuration || duration > maxDuration) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.INVALID_OUTPUT_DURATION,
                    "Expected duration [" + minDuration + "," + maxDuration + "] but got " + duration));
        }

        // Validate codec if available
        if (codec != null && !codec.equalsIgnoreCase("h264")) {
            issues.add(LocalRenderSmokeIssue.warning(
                    LocalRenderSmokeIssueCode.INVALID_OUTPUT_CODEC,
                    "Expected codec h264 but got " + codec));
        }

        boolean valid = issues.stream().noneMatch(i ->
                i.severity() == LocalRenderSmokeIssueSeverity.ERROR
                || i.severity() == LocalRenderSmokeIssueSeverity.BLOCKING);

        return new ValidationResult(valid, width, height, duration, codec, format, result.exitCode(), issues);
    }

    /**
     * Parses ffprobe default output format (key=value pairs).
     * Multiple sections separated by blank lines; last value wins for duplicate keys.
     */
    static Map<String, String> parseFfprobeOutput(String output) {
        Map<String, String> result = new java.util.LinkedHashMap<>();
        if (output == null || output.isBlank()) return result;

        for (String line : output.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("[") || trimmed.startsWith("/")) continue;
            int eq = trimmed.indexOf('=');
            if (eq > 0) {
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                result.put(key, value);
            }
        }
        return result;
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }
}
