package com.example.platform.render.infrastructure.smoke;

import com.example.platform.render.domain.render.local.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Local render smoke harness — the main entry point for controlled local FFmpeg/ffprobe smoke execution.
 *
 * <p>This harness proves a platform-owned, controlled render smoke can:
 * <ol>
 *   <li>Produce a deterministic small test video locally.</li>
 *   <li>Validate the output with ffprobe.</li>
 *   <li>Write a deterministic local smoke report.</li>
 *   <li>Keep all execution behind a local smoke boundary.</li>
 * </ol>
 *
 * <p>This is not a public API. This is not OpenCue. This is not ProductRuntime.
 * This is not StorageRuntime. This is not RenderExecutionPlan integration.</p>
 */
public final class LocalRenderSmokeHarness {

    private LocalRenderSmokeHarness() {}

    /**
     * Executes a local render smoke scenario.
     *
     * @param request the smoke request
     * @param policy  the smoke policy
     * @return smoke result
     */
    public static LocalRenderSmokeResult execute(LocalRenderSmokeRequest request, LocalRenderSmokePolicy policy) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        List<LocalRenderSmokeIssue> issues = new ArrayList<>();
        Instant startTime = Instant.now();

        // Step 1: Check policy
        if (!policy.allowExecution()) {
            issues.add(LocalRenderSmokeIssue.info(
                    LocalRenderSmokeIssueCode.COMMAND_ALLOWLIST_VIOLATION,
                    "Local smoke execution is disabled by policy. Enable with -D" + LocalRenderSmokePolicy.ENABLE_PROPERTY + "=true"));
            return buildSkippedResult(request, issues);
        }

        // Step 2: Check ffmpeg availability
        if (!isBinaryAvailable("ffmpeg")) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.FFMPEG_NOT_AVAILABLE,
                    "ffmpeg binary not found on PATH"));
            return buildNotAvailableResult(request, issues);
        }

        // Step 3: Check ffprobe availability
        if (!isBinaryAvailable("ffprobe")) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.FFPROBE_NOT_AVAILABLE,
                    "ffprobe binary not found on PATH"));
            return buildNotAvailableResult(request, issues);
        }

        // Step 4: Build controlled FFmpeg command
        LocalFfmpegSmokeCommandBuilder.BuildResult buildResult =
                LocalFfmpegSmokeCommandBuilder.buildTestsrcH264Mp4(request, policy);

        if (!buildResult.issues().isEmpty()) {
            issues.addAll(buildResult.issues());
            boolean hasBlocking = buildResult.issues().stream()
                    .anyMatch(i -> i.severity() == LocalRenderSmokeIssueSeverity.BLOCKING);
            if (hasBlocking) {
                return buildBlockedResult(request, issues);
            }
        }

        if (buildResult.args().isEmpty()) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.COMMAND_ALLOWLIST_VIOLATION,
                    "Failed to build FFmpeg command"));
            return buildFailResult(request, null, -1, Duration.ZERO, issues);
        }

        // Step 5: Execute FFmpeg with timeout
        LocalProcessRunner.LocalProcessExecutionResult ffmpegResult =
                LocalProcessRunner.execute(buildResult.args(), policy.timeoutSeconds());

        if (!ffmpegResult.success()) {
            if (ffmpegResult.stderr() != null && ffmpegResult.stderr().contains("PROCESS_TIMEOUT")) {
                issues.add(LocalRenderSmokeIssue.error(
                        LocalRenderSmokeIssueCode.PROCESS_TIMEOUT,
                        "FFmpeg timed out after " + policy.timeoutSeconds() + "s"));
            } else {
                issues.add(LocalRenderSmokeIssue.error(
                        LocalRenderSmokeIssueCode.FFMPEG_EXIT_NONZERO,
                        "FFmpeg exited with code " + ffmpegResult.exitCode()));
            }
            return buildFailResult(request, buildResult.outputPath(),
                    ffmpegResult.exitCode(), ffmpegResult.duration(), issues);
        }

        // Step 6: Validate output with ffprobe
        LocalFfprobeValidator.ValidationResult validation = LocalFfprobeValidator.validate(
                buildResult.outputPath(),
                request.width(), request.height(),
                request.durationSec() * 0.5, request.durationSec() * 2.0,
                policy.timeoutSeconds());

        if (!validation.issues().isEmpty()) {
            issues.addAll(validation.issues());
        }

        // Step 7: Check output file
        long outputBytes = 0;
        try {
            if (Files.exists(buildResult.outputPath())) {
                outputBytes = Files.size(buildResult.outputPath());
            }
        } catch (IOException ignored) {}

        // Step 8: Determine final status
        boolean hasErrors = issues.stream().anyMatch(i ->
                i.severity() == LocalRenderSmokeIssueSeverity.ERROR
                || i.severity() == LocalRenderSmokeIssueSeverity.BLOCKING);
        boolean hasWarnings = issues.stream().anyMatch(i ->
                i.severity() == LocalRenderSmokeIssueSeverity.WARNING);

        LocalRenderSmokeStatus status;
        if (hasErrors) {
            status = LocalRenderSmokeStatus.FAIL;
        } else if (hasWarnings) {
            status = LocalRenderSmokeStatus.PASS_WITH_WARNINGS;
        } else {
            status = LocalRenderSmokeStatus.PASS;
        }

        return new LocalRenderSmokeResult(
                request.smokeId(),
                request.smokeName(),
                status,
                buildResult.outputPath(),
                outputBytes,
                ffmpegResult.exitCode(),
                ffmpegResult.duration(),
                validation.ffprobeExitCode(),
                validation.width(),
                validation.height(),
                validation.durationSec(),
                validation.codec(),
                validation.format(),
                issues,
                Map.of(
                        "binary", "ffmpeg",
                        "argumentCount", String.valueOf(buildResult.args().size()),
                        "executionTimeMs", String.valueOf(ffmpegResult.duration().toMillis())
                )
        );
    }

    /**
     * Executes the default testsrc-h264-mp4 smoke and writes a report.
     */
    public static LocalRenderSmokeReport executeDefaultSmoke(LocalRenderSmokePolicy policy) {
        Objects.requireNonNull(policy, "policy must not be null");

        Path smokeRoot = policy.outputRoot();
        LocalRenderSmokeRequest request = LocalRenderSmokeRequest.testsrcH264Mp4(smokeRoot);
        LocalRenderSmokeResult result = execute(request, policy);

        LocalRenderSmokeReport report = new LocalRenderSmokeReport(
                "smoke-report-" + request.smokeId().value(),
                Instant.now(),
                result.status(),
                List.of(result),
                result.status() == LocalRenderSmokeStatus.PASS
                        || result.status() == LocalRenderSmokeStatus.PASS_WITH_WARNINGS ? 1 : 0,
                result.status() == LocalRenderSmokeStatus.FAIL ? 1 : 0,
                result.status() == LocalRenderSmokeStatus.SKIPPED ? 1 : 0,
                result.status() == LocalRenderSmokeStatus.NOT_AVAILABLE ? 1 : 0,
                Map.of()
        );

        // Write report to /tmp if enabled
        writeReport(report, smokeRoot);

        return report;
    }

    private static void writeReport(LocalRenderSmokeReport report, Path outputRoot) {
        try {
            Path reportPath = outputRoot.resolve("smoke-report.txt");
            Files.createDirectories(outputRoot);
            StringBuilder sb = new StringBuilder();
            sb.append("Local Render Smoke Report\n");
            sb.append("========================\n");
            sb.append("Report ID: ").append(report.reportId()).append("\n");
            sb.append("Executed At: ").append(report.executedAt()).append("\n");
            sb.append("Overall Status: ").append(report.overallStatus()).append("\n");
            sb.append("Pass: ").append(report.passCount()).append("\n");
            sb.append("Fail: ").append(report.failCount()).append("\n");
            sb.append("Skipped: ").append(report.skippedCount()).append("\n");
            sb.append("Not Available: ").append(report.notAvailableCount()).append("\n");
            sb.append("\nResults:\n");
            for (LocalRenderSmokeResult r : report.results()) {
                sb.append("  ").append(r.smokeName().value()).append(": ").append(r.status()).append("\n");
                sb.append("    Output: ").append(r.outputPath()).append("\n");
                sb.append("    Size: ").append(r.outputFileBytes()).append(" bytes\n");
                sb.append("    FFmpeg exit: ").append(r.ffmpegExitCode()).append("\n");
                sb.append("    Duration: ").append(r.actualDurationSec()).append("s\n");
                sb.append("    Resolution: ").append(r.actualWidth()).append("x").append(r.actualHeight()).append("\n");
                sb.append("    Codec: ").append(r.actualCodec()).append("\n");
                if (!r.issues().isEmpty()) {
                    sb.append("    Issues:\n");
                    for (LocalRenderSmokeIssue issue : r.issues()) {
                        sb.append("      [").append(issue.severity()).append("] ").append(issue.code())
                                .append(": ").append(issue.message()).append("\n");
                    }
                }
            }
            Files.writeString(reportPath, sb.toString());
        } catch (Exception e) {
            // Best-effort report writing; don't fail the smoke for this
        }
    }

    private static boolean isBinaryAvailable(String binary) {
        try {
            ProcessBuilder pb = new ProcessBuilder(binary, "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean done = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            return done && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static LocalRenderSmokeResult buildSkippedResult(LocalRenderSmokeRequest request,
                                                              List<LocalRenderSmokeIssue> issues) {
        return new LocalRenderSmokeResult(
                request.smokeId(), request.smokeName(), LocalRenderSmokeStatus.SKIPPED,
                null, 0, -1, Duration.ZERO, -1, 0, 0, 0, null, null,
                issues, Map.of("reason", "policy-disabled"));
    }

    private static LocalRenderSmokeResult buildNotAvailableResult(LocalRenderSmokeRequest request,
                                                                   List<LocalRenderSmokeIssue> issues) {
        return new LocalRenderSmokeResult(
                request.smokeId(), request.smokeName(), LocalRenderSmokeStatus.NOT_AVAILABLE,
                null, 0, -1, Duration.ZERO, -1, 0, 0, 0, null, null,
                issues, Map.of("reason", "binary-not-available"));
    }

    private static LocalRenderSmokeResult buildBlockedResult(LocalRenderSmokeRequest request,
                                                              List<LocalRenderSmokeIssue> issues) {
        return new LocalRenderSmokeResult(
                request.smokeId(), request.smokeName(), LocalRenderSmokeStatus.BLOCKED,
                null, 0, -1, Duration.ZERO, -1, 0, 0, 0, null, null,
                issues, Map.of("reason", "safety-violation"));
    }

    private static LocalRenderSmokeResult buildFailResult(LocalRenderSmokeRequest request,
                                                           Path outputPath, int exitCode,
                                                           Duration duration,
                                                           List<LocalRenderSmokeIssue> issues) {
        return new LocalRenderSmokeResult(
                request.smokeId(), request.smokeName(), LocalRenderSmokeStatus.FAIL,
                outputPath, 0, exitCode, duration, -1, 0, 0, 0, null, null,
                issues, Map.of());
    }
}
