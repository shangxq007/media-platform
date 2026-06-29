package com.example.platform.render.infrastructure.smoke;

import com.example.platform.render.domain.render.local.LocalRenderExecutionId;
import com.example.platform.render.domain.render.local.LocalRenderExecutionRequest;
import com.example.platform.render.domain.render.local.LocalRenderSmokeIssue;
import com.example.platform.render.domain.render.local.LocalRenderSmokeIssueCode;
import com.example.platform.render.domain.render.local.LocalRenderSmokePolicy;
import com.example.platform.render.domain.timeline.render.plan.*;

import java.nio.file.Path;
import java.util.*;

/**
 * Adapter that bridges a {@link FFmpegLibassBasicRenderPlan} to a
 * {@link LocalRenderExecutionRequest}.
 *
 * <p>This is the first bridge proving BasicRenderPlan can drive controlled
 * local execution. It supports a conservative subset of plan steps:</p>
 * <ul>
 *   <li>{@code DECLARE_OUTPUT_PROFILE} — extracts resolution, codec, container</li>
 *   <li>{@code ENCODE_OUTPUT} — validates encoding parameters</li>
 *   <li>{@code VERIFY_OUTPUT} — triggers ffprobe validation</li>
 * </ul>
 *
 * <p>Unsupported steps (effects, transitions, captions, watermarks, audio,
 * clip assembly) are reported as warnings or UNSUPPORTED. The adapter uses
 * a synthetic {@code testsrc} input when real media source materialization
 * is not implemented.</p>
 *
 * <p>This adapter does NOT execute FFmpeg, generate shell commands, or
 * expose raw filtergraphs.</p>
 */
public final class BasicRenderPlanLocalExecutionAdapter {

    /** Steps supported by P2L.1 bridge. */
    private static final Set<FFmpegLibassBasicRenderStepType> SUPPORTED_STEP_TYPES = Set.of(
            FFmpegLibassBasicRenderStepType.DECLARE_OUTPUT_PROFILE,
            FFmpegLibassBasicRenderStepType.ENCODE_OUTPUT,
            FFmpegLibassBasicRenderStepType.VERIFY_OUTPUT,
            FFmpegLibassBasicRenderStepType.VALIDATE_TIMELINE,
            FFmpegLibassBasicRenderStepType.DECLARE_SAFE_METADATA
    );

    /** Stages supported by P2L.1 bridge. */
    private static final Set<FFmpegLibassBasicRenderStageType> SUPPORTED_STAGE_TYPES = Set.of(
            FFmpegLibassBasicRenderStageType.VALIDATE_TIMELINE,
            FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
            FFmpegLibassBasicRenderStageType.PLAN_OUTPUT_ENCODING,
            FFmpegLibassBasicRenderStageType.PLAN_OUTPUT_VERIFICATION
    );

    private static final double DEFAULT_SYNTHETIC_DURATION_SEC = 2.0;
    private static final int DEFAULT_FPS = 30;

    private BasicRenderPlanLocalExecutionAdapter() {}

    /**
     * Result of adapting a BasicRenderPlan to a local execution request.
     */
    public record AdaptResult(
            LocalRenderExecutionRequest request,
            List<LocalRenderSmokeIssue> issues,
            boolean blocked
    ) {
        public AdaptResult {
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }

    /**
     * Adapts a BasicRenderPlan to a local execution request.
     *
     * @param plan   the render plan to adapt
     * @param policy the local smoke policy
     * @return adaptation result with request, issues, and blocked flag
     */
    public static AdaptResult adapt(FFmpegLibassBasicRenderPlan plan, LocalRenderSmokePolicy policy) {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(policy, "policy must not be null");

        List<LocalRenderSmokeIssue> issues = new ArrayList<>();
        List<String> unsupportedSteps = new ArrayList<>();

        // Validate plan status
        if (plan.status() == FFmpegLibassBasicRenderPlanStatus.BLOCKED
                || plan.status() == FFmpegLibassBasicRenderPlanStatus.FAILED) {
            issues.add(LocalRenderSmokeIssue.blocking(
                    LocalRenderSmokeIssueCode.BASIC_RENDER_PLAN_BLOCKED,
                    "BasicRenderPlan is " + plan.status() + "; cannot execute locally"));
            return new AdaptResult(null, issues, true);
        }

        if (plan.status() == FFmpegLibassBasicRenderPlanStatus.UNSUPPORTED) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.BASIC_RENDER_PLAN_UNSUPPORTED,
                    "BasicRenderPlan is UNSUPPORTED; cannot execute locally"));
            return new AdaptResult(null, issues, true);
        }

        if (plan.status() == FFmpegLibassBasicRenderPlanStatus.INVALID) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.BASIC_RENDER_PLAN_INVALID,
                    "BasicRenderPlan is INVALID; cannot execute locally"));
            return new AdaptResult(null, issues, true);
        }

        // Scan stages/steps for unsupported content
        for (FFmpegLibassBasicRenderStage stage : plan.stages()) {
            if (!SUPPORTED_STAGE_TYPES.contains(stage.type())) {
                unsupportedSteps.add("stage:" + stage.type());
                issues.add(LocalRenderSmokeIssue.warning(
                        LocalRenderSmokeIssueCode.UNSUPPORTED_RENDER_STAGE,
                        "Unsupported stage: " + stage.type()));
            }
            for (FFmpegLibassBasicRenderStep step : stage.steps()) {
                if (!SUPPORTED_STEP_TYPES.contains(step.type())) {
                    unsupportedSteps.add("step:" + step.type());
                    issues.add(LocalRenderSmokeIssue.warning(
                            LocalRenderSmokeIssueCode.UNSUPPORTED_RENDER_STEP,
                            "Unsupported step: " + step.type()));
                }
            }
        }

        // Extract output profile from plan
        OutputProfile profile = extractOutputProfile(plan);
        if (profile == null) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.OUTPUT_PROFILE_MISSING,
                    "No DECLARE_OUTPUT_PROFILE or ENCODE_OUTPUT step found in plan"));
            return new AdaptResult(null, issues, true);
        }

        // Validate output profile
        if (profile.width <= 0 || profile.height <= 0) {
            issues.add(LocalRenderSmokeIssue.error(
                    LocalRenderSmokeIssueCode.OUTPUT_PROFILE_UNSUPPORTED,
                    "Invalid output dimensions: " + profile.width + "x" + profile.height));
            return new AdaptResult(null, issues, true);
        }

        // Build request with synthetic testsrc input
        LocalRenderExecutionId executionId = LocalRenderExecutionId.generate();
        Path outputRoot = policy.outputRoot();
        String planId = plan.id() != null ? plan.id().value() : "unknown";

        LocalRenderExecutionRequest request = new LocalRenderExecutionRequest(
                executionId,
                planId,
                profile.width,
                profile.height,
                DEFAULT_SYNTHETIC_DURATION_SEC,
                profile.fps > 0 ? profile.fps : DEFAULT_FPS,
                profile.videoCodec != null ? profile.videoCodec : "h264",
                profile.container != null ? profile.container : "mp4",
                outputRoot.resolve("local-plan-smoke-001-basic-render-plan-testsrc-h264-mp4"),
                unsupportedSteps,
                plan.safeMetadata()
        );

        issues.add(LocalRenderSmokeIssue.info(
                LocalRenderSmokeIssueCode.SYNTHETIC_INPUT_REQUIRED,
                "Using synthetic testsrc input; real media source materialization not implemented"));

        return new AdaptResult(request, issues, false);
    }

    /**
     * Extracts output profile from the plan by inspecting DECLARE_OUTPUT_PROFILE
     * and ENCODE_OUTPUT steps.
     */
    private static OutputProfile extractOutputProfile(FFmpegLibassBasicRenderPlan plan) {
        int width = 0, height = 0, fps = 0;
        String videoCodec = null, container = null;

        for (FFmpegLibassBasicRenderStage stage : plan.stages()) {
            for (FFmpegLibassBasicRenderStep step : stage.steps()) {
                if (step.type() == FFmpegLibassBasicRenderStepType.DECLARE_OUTPUT_PROFILE
                        || step.type() == FFmpegLibassBasicRenderStepType.ENCODE_OUTPUT) {
                    for (FFmpegLibassBasicRenderStepParameter param : step.parameters()) {
                        switch (param.name()) {
                            case "width" -> width = toInt(param.value());
                            case "height" -> height = toInt(param.value());
                            case "fps" -> fps = toInt(param.value());
                            case "videoCodec" -> videoCodec = toString(param.value());
                            case "container" -> container = toString(param.value());
                        }
                    }
                }
            }
        }

        if (width <= 0 && height <= 0) return null;
        return new OutputProfile(width, height, fps, videoCodec, container);
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(value)); } catch (Exception e) { return 0; }
    }

    private static String toString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private record OutputProfile(int width, int height, int fps, String videoCodec, String container) {}
}
