package com.example.platform.render.infrastructure.smoke;

import com.example.platform.render.domain.render.local.*;
import com.example.platform.render.domain.timeline.render.plan.FFmpegLibassBasicRenderPlan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Local runner that bridges a {@link FFmpegLibassBasicRenderPlan} to controlled
 * local FFmpeg/ffprobe execution.
 *
 * <p>Flow:</p>
 * <ol>
 *   <li>Accept BasicRenderPlan</li>
 *   <li>Use {@link BasicRenderPlanLocalExecutionAdapter} to create local request</li>
 *   <li>Build controlled FFmpeg command via {@link LocalFfmpegSmokeCommandBuilder}</li>
 *   <li>Execute via {@link LocalProcessRunner}</li>
 *   <li>Validate output with {@link LocalFfprobeValidator}</li>
 *   <li>Return {@link LocalRenderExecutionResult}</li>
 * </ol>
 *
 * <p>This is not a full timeline renderer. This is the first bridge proving
 * BasicRenderPlan can drive controlled local execution.</p>
 *
 * <p>Execution must remain disabled by default. The runner reuses
 * {@link LocalRenderSmokePolicy} for safety enforcement.</p>
 */
public final class BasicRenderPlanLocalRunner {

    private BasicRenderPlanLocalRunner() {}

    /**
     * Executes a BasicRenderPlan through the local runner bridge.
     *
     * @param plan   the render plan to execute
     * @param policy the local smoke policy
     * @return execution result
     */
    public static LocalRenderExecutionResult execute(FFmpegLibassBasicRenderPlan plan,
                                                      LocalRenderSmokePolicy policy) {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        List<LocalRenderSmokeIssue> issues = new ArrayList<>();

        // Step 1: Check policy
        if (!policy.allowExecution()) {
            issues.add(LocalRenderSmokeIssue.info(
                    LocalRenderSmokeIssueCode.COMMAND_ALLOWLIST_VIOLATION,
                    "Local execution is disabled by policy. Enable with -D"
                            + LocalRenderSmokePolicy.ENABLE_PROPERTY + "=true"));
            return buildSkippedResult(plan, issues);
        }

        // Step 2: Adapt plan to local request
        BasicRenderPlanLocalExecutionAdapter.AdaptResult adaptResult =
                BasicRenderPlanLocalExecutionAdapter.adapt(plan, policy);

        if (adaptResult.blocked() || adaptResult.request() == null) {
            issues.addAll(adaptResult.issues());
            LocalRenderExecutionStatus status = adaptResult.issues().stream()
                    .anyMatch(i -> i.severity() == LocalRenderSmokeIssueSeverity.BLOCKING)
                    ? LocalRenderExecutionStatus.BLOCKED
                    : LocalRenderExecutionStatus.UNSUPPORTED;
            return buildRejectedResult(plan, status, issues, adaptResult.request());
        }

        LocalRenderExecutionRequest request = adaptResult.request();
        issues.addAll(adaptResult.issues());

        // Step 3: Check ffmpeg availability
        if (!isBinaryAvailable("ffmpeg")) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.FFMPEG_NOT_AVAILABLE,
                    "ffmpeg binary not found on PATH"));
            return buildNotAvailableResult(plan, request, issues);
        }

        // Step 4: Check ffprobe availability
        if (!isBinaryAvailable("ffprobe")) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.FFPROBE_NOT_AVAILABLE,
                    "ffprobe binary not found on PATH"));
            return buildNotAvailableResult(plan, request, issues);
        }

        // Step 5: Build controlled FFmpeg command
        LocalFfmpegSmokeCommandBuilder.BuildResult buildResult =
                LocalFfmpegSmokeCommandBuilder.buildPlanDrivenTestsrc(
                        request.width(), request.height(), request.durationSec(),
                        request.fps(), request.videoCodec(), request.container(),
                        request.outputRoot(), policy);

        if (!buildResult.issues().isEmpty()) {
            issues.addAll(buildResult.issues());
            boolean hasBlocking = buildResult.issues().stream()
                    .anyMatch(i -> i.severity() == LocalRenderSmokeIssueSeverity.BLOCKING);
            if (hasBlocking) {
                return buildBlockedResult(plan, request, issues);
            }
        }

        if (buildResult.args().isEmpty()) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.COMMAND_ALLOWLIST_VIOLATION,
                    "Failed to build FFmpeg command"));
            return buildFailResult(plan, request, null, -1, Duration.ZERO, issues);
        }

        // Step 6: Execute FFmpeg with timeout
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
            return buildFailResult(plan, request, buildResult.outputPath(),
                    ffmpegResult.exitCode(), ffmpegResult.duration(), issues);
        }

        // Step 7: Validate output with ffprobe
        LocalFfprobeValidator.ValidationResult validation = LocalFfprobeValidator.validate(
                buildResult.outputPath(),
                request.width(), request.height(),
                request.durationSec() * 0.5, request.durationSec() * 2.0,
                policy.timeoutSeconds());

        if (!validation.issues().isEmpty()) {
            issues.addAll(validation.issues());
        }

        // Step 8: Check output file
        long outputBytes = 0;
        try {
            if (Files.exists(buildResult.outputPath())) {
                outputBytes = Files.size(buildResult.outputPath());
            }
        } catch (IOException ignored) {}

        // Step 9: Determine final status
        boolean hasErrors = issues.stream().anyMatch(i ->
                i.severity() == LocalRenderSmokeIssueSeverity.ERROR
                || i.severity() == LocalRenderSmokeIssueSeverity.BLOCKING);
        boolean hasWarnings = issues.stream().anyMatch(i ->
                i.severity() == LocalRenderSmokeIssueSeverity.WARNING);

        LocalRenderExecutionStatus status;
        if (hasErrors) {
            status = LocalRenderExecutionStatus.FAIL;
        } else if (hasWarnings) {
            status = LocalRenderExecutionStatus.PASS_WITH_WARNINGS;
        } else {
            status = LocalRenderExecutionStatus.PASS;
        }

        return new LocalRenderExecutionResult(
                request.executionId(),
                request.planId(),
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
                request.unsupportedSteps(),
                issues,
                Map.of(
                        "binary", "ffmpeg",
                        "argumentCount", String.valueOf(buildResult.args().size()),
                        "executionTimeMs", String.valueOf(ffmpegResult.duration().toMillis()),
                        "syntheticInput", "testsrc",
                        "planId", request.planId()
                )
        );
    }

    /**
     * Executes a BasicRenderPlan and writes a report under the configured output root.
     *
     * @param plan   the render plan to execute
     * @param policy the local smoke policy
     * @return execution report
     */
    public static LocalRenderExecutionReport executeAndReport(FFmpegLibassBasicRenderPlan plan,
                                                               LocalRenderSmokePolicy policy) {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        LocalRenderExecutionResult result = execute(plan, policy);

        LocalRenderExecutionReport report = new LocalRenderExecutionReport(
                "exec-report-" + result.executionId().value(),
                Instant.now(),
                result.status(),
                List.of(result),
                result.status() == LocalRenderExecutionStatus.PASS
                        || result.status() == LocalRenderExecutionStatus.PASS_WITH_WARNINGS ? 1 : 0,
                result.status() == LocalRenderExecutionStatus.FAIL ? 1 : 0,
                result.status() == LocalRenderExecutionStatus.SKIPPED ? 1 : 0,
                result.status() == LocalRenderExecutionStatus.NOT_AVAILABLE ? 1 : 0,
                result.status() == LocalRenderExecutionStatus.UNSUPPORTED ? 1 : 0,
                Map.of()
        );

        // Write report
        writeReport(report, result);

        return report;
    }

    private static void writeReport(LocalRenderExecutionReport report,
                                     LocalRenderExecutionResult result) {
        try {
            Path outputDir = result.outputPath() != null
                    ? result.outputPath().getParent()
                    : null;
            if (outputDir == null) return;

            Files.createDirectories(outputDir);
            Path reportPath = outputDir.resolve("local-render-execution-report.txt");

            StringBuilder sb = new StringBuilder();
            sb.append("Local Render Execution Report (BasicRenderPlan Bridge)\n");
            sb.append("=====================================================\n");
            sb.append("Report ID: ").append(report.reportId()).append("\n");
            sb.append("Executed At: ").append(report.executedAt()).append("\n");
            sb.append("Overall Status: ").append(report.overallStatus()).append("\n");
            sb.append("Pass: ").append(report.passCount()).append("\n");
            sb.append("Fail: ").append(report.failCount()).append("\n");
            sb.append("Skipped: ").append(report.skippedCount()).append("\n");
            sb.append("Not Available: ").append(report.notAvailableCount()).append("\n");
            sb.append("Unsupported: ").append(report.unsupportedCount()).append("\n");
            sb.append("\nResults:\n");
            for (LocalRenderExecutionResult r : report.results()) {
                sb.append("  Execution ID: ").append(r.executionId().value()).append("\n");
                sb.append("  BasicRenderPlan ID: ").append(r.planId()).append("\n");
                sb.append("  Status: ").append(r.status()).append("\n");
                sb.append("  Output: ").append(r.outputPath()).append("\n");
                sb.append("  Size: ").append(r.outputFileBytes()).append(" bytes\n");
                sb.append("  FFmpeg exit: ").append(r.ffmpegExitCode()).append("\n");
                sb.append("  Duration: ").append(r.actualDurationSec()).append("s\n");
                sb.append("  Resolution: ").append(r.actualWidth()).append("x").append(r.actualHeight()).append("\n");
                sb.append("  Codec: ").append(r.actualCodec()).append("\n");
                sb.append("  Format: ").append(r.actualFormat()).append("\n");
                if (!r.unsupportedSteps().isEmpty()) {
                    sb.append("  Unsupported Steps: ").append(String.join(", ", r.unsupportedSteps())).append("\n");
                }
                if (!r.issues().isEmpty()) {
                    sb.append("  Issues:\n");
                    for (LocalRenderSmokeIssue issue : r.issues()) {
                        sb.append("    [").append(issue.severity()).append("] ").append(issue.code())
                                .append(": ").append(issue.message()).append("\n");
                    }
                }
            }
            Files.writeString(reportPath, sb.toString());
        } catch (Exception e) {
            // Best-effort report writing; don't fail the execution for this
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

    private static LocalRenderExecutionResult buildSkippedResult(
            FFmpegLibassBasicRenderPlan plan, List<LocalRenderSmokeIssue> issues) {
        String planId = plan.id() != null ? plan.id().value() : "unknown";
        return new LocalRenderExecutionResult(
                LocalRenderExecutionId.generate(), planId,
                LocalRenderExecutionStatus.SKIPPED,
                null, 0, -1, Duration.ZERO, -1, 0, 0, 0, null, null,
                List.of(), issues, Map.of("reason", "policy-disabled"));
    }

    private static LocalRenderExecutionResult buildRejectedResult(
            FFmpegLibassBasicRenderPlan plan, LocalRenderExecutionStatus status,
            List<LocalRenderSmokeIssue> issues, LocalRenderExecutionRequest request) {
        String planId = plan.id() != null ? plan.id().value() : "unknown";
        return new LocalRenderExecutionResult(
                LocalRenderExecutionId.generate(), planId,
                status,
                null, 0, -1, Duration.ZERO, -1, 0, 0, 0, null, null,
                request != null ? request.unsupportedSteps() : List.of(),
                issues, Map.of("reason", "plan-rejected"));
    }

    private static LocalRenderExecutionResult buildNotAvailableResult(
            FFmpegLibassBasicRenderPlan plan, LocalRenderExecutionRequest request,
            List<LocalRenderSmokeIssue> issues) {
        return new LocalRenderExecutionResult(
                request.executionId(), request.planId(),
                LocalRenderExecutionStatus.NOT_AVAILABLE,
                null, 0, -1, Duration.ZERO, -1, 0, 0, 0, null, null,
                request.unsupportedSteps(), issues, Map.of("reason", "binary-not-available"));
    }

    private static LocalRenderExecutionResult buildBlockedResult(
            FFmpegLibassBasicRenderPlan plan, LocalRenderExecutionRequest request,
            List<LocalRenderSmokeIssue> issues) {
        return new LocalRenderExecutionResult(
                request.executionId(), request.planId(),
                LocalRenderExecutionStatus.BLOCKED,
                null, 0, -1, Duration.ZERO, -1, 0, 0, 0, null, null,
                request.unsupportedSteps(), issues, Map.of("reason", "safety-violation"));
    }

    private static LocalRenderExecutionResult buildFailResult(
            FFmpegLibassBasicRenderPlan plan, LocalRenderExecutionRequest request,
            Path outputPath, int exitCode, Duration duration,
            List<LocalRenderSmokeIssue> issues) {
        return new LocalRenderExecutionResult(
                request.executionId(), request.planId(),
                LocalRenderExecutionStatus.FAIL,
                outputPath, 0, exitCode, duration, -1, 0, 0, 0, null, null,
                request.unsupportedSteps(), issues, Map.of());
    }
}
