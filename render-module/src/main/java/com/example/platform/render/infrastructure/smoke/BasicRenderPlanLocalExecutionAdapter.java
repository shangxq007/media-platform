package com.example.platform.render.infrastructure.smoke;

import com.example.platform.render.domain.render.local.LocalCaptionOverlaySpec;
import com.example.platform.render.domain.render.local.LocalMediaSourceKind;
import com.example.platform.render.domain.render.local.LocalMediaSourceOrigin;
import com.example.platform.render.domain.render.local.LocalMediaSourceSpec;
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
 * <p>P2L.2 expands the supported subset to include caption overlay:</p>
 * <ul>
 *   <li>{@code DECLARE_OUTPUT_PROFILE} — extracts resolution, codec, container</li>
 *   <li>{@code ENCODE_OUTPUT} — validates encoding parameters</li>
 *   <li>{@code VERIFY_OUTPUT} — triggers ffprobe validation</li>
 *   <li>{@code APPLY_CAPTION_OVERLAY} — extracts safe caption overlay specs</li>
 * </ul>
 *
 * <p>Unsupported steps (effects, transitions, watermarks, audio,
 * clip assembly) are reported as warnings or UNSUPPORTED. The adapter uses
 * a synthetic {@code testsrc} input when real media source materialization
 * is not implemented.</p>
 *
 * <p>This adapter does NOT execute FFmpeg, generate shell commands, or
 * expose raw filtergraphs.</p>
 */
public final class BasicRenderPlanLocalExecutionAdapter {

    /** Steps supported by P2L.2 bridge. */
    private static final Set<FFmpegLibassBasicRenderStepType> SUPPORTED_STEP_TYPES = Set.of(
            FFmpegLibassBasicRenderStepType.DECLARE_OUTPUT_PROFILE,
            FFmpegLibassBasicRenderStepType.ENCODE_OUTPUT,
            FFmpegLibassBasicRenderStepType.VERIFY_OUTPUT,
            FFmpegLibassBasicRenderStepType.VALIDATE_TIMELINE,
            FFmpegLibassBasicRenderStepType.DECLARE_SAFE_METADATA,
            FFmpegLibassBasicRenderStepType.APPLY_CAPTION_OVERLAY
    );

    /** Stages supported by P2L.2 bridge. */
    private static final Set<FFmpegLibassBasicRenderStageType> SUPPORTED_STAGE_TYPES = Set.of(
            FFmpegLibassBasicRenderStageType.VALIDATE_TIMELINE,
            FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
            FFmpegLibassBasicRenderStageType.PLAN_OUTPUT_ENCODING,
            FFmpegLibassBasicRenderStageType.PLAN_OUTPUT_VERIFICATION,
            FFmpegLibassBasicRenderStageType.PLAN_CAPTION_OVERLAYS
    );

    private static final double DEFAULT_SYNTHETIC_DURATION_SEC = 2.0;
    private static final int DEFAULT_FPS = 30;
    private static final int MAX_CAPTION_OVERLAYS = 10;

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

        // Extract caption overlay specs
        List<LocalCaptionOverlaySpec> captionSpecs = extractCaptionOverlaySpecs(plan, issues);

        // Build request with synthetic testsrc input
        LocalRenderExecutionId executionId = LocalRenderExecutionId.generate();
        Path outputRoot = policy.outputRoot();
        String planId = plan.id() != null ? plan.id().value() : "unknown";

        // Adjust duration to cover all captions if needed
        double effectiveDuration = DEFAULT_SYNTHETIC_DURATION_SEC;
        for (LocalCaptionOverlaySpec spec : captionSpecs) {
            double captionEndSec = spec.endSec();
            if (captionEndSec > effectiveDuration) {
                effectiveDuration = Math.min(captionEndSec + 1.0, 30.0); // cap at 30s
            }
        }

        String outputSubdir = captionSpecs.isEmpty()
                ? "local-plan-smoke-001-basic-render-plan-testsrc-h264-mp4"
                : "local-plan-smoke-002-basic-render-plan-caption-overlay";

        LocalRenderExecutionRequest request = new LocalRenderExecutionRequest(
                executionId,
                planId,
                profile.width,
                profile.height,
                effectiveDuration,
                profile.fps > 0 ? profile.fps : DEFAULT_FPS,
                profile.videoCodec != null ? profile.videoCodec : "h264",
                profile.container != null ? profile.container : "mp4",
                outputRoot.resolve(outputSubdir),
                unsupportedSteps,
                captionSpecs,
                null, // no media source — using synthetic testsrc
                plan.safeMetadata()
        );

        issues.add(LocalRenderSmokeIssue.info(
                LocalRenderSmokeIssueCode.SYNTHETIC_INPUT_REQUIRED,
                "Using synthetic testsrc input; real media source materialization not implemented"));

        return new AdaptResult(request, issues, false);
    }

    /**
     * Adapts a BasicRenderPlan to a local execution request with a controlled media source.
     *
     * @param plan          the render plan to adapt
     * @param policy        the local smoke policy
     * @param mediaSource   controlled media source spec (must be validated)
     * @return adaptation result with request, issues, and blocked flag
     */
    public static AdaptResult adapt(FFmpegLibassBasicRenderPlan plan, LocalRenderSmokePolicy policy,
                                     LocalMediaSourceSpec mediaSource) {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(mediaSource, "mediaSource must not be null");

        List<LocalRenderSmokeIssue> issues = new ArrayList<>();
        List<String> unsupportedSteps = new ArrayList<>();

        // Validate plan status (same as base adapt)
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

        // Validate media source
        if (mediaSource.kind() != LocalMediaSourceKind.CONTROLLED_LOCAL_FIXTURE) {
            issues.add(LocalRenderSmokeIssue.blocking(
                    LocalRenderSmokeIssueCode.MEDIA_SOURCE_KIND_UNSUPPORTED,
                    "Unsupported media source kind: " + mediaSource.kind()));
            return new AdaptResult(null, issues, true);
        }

        if (mediaSource.origin() != LocalMediaSourceOrigin.PLATFORM_GENERATED
                && mediaSource.origin() != LocalMediaSourceOrigin.CONTROLLED_TEST_FIXTURE) {
            issues.add(LocalRenderSmokeIssue.blocking(
                    LocalRenderSmokeIssueCode.MEDIA_SOURCE_ORIGIN_UNSUPPORTED,
                    "Unsupported media source origin: " + mediaSource.origin()));
            return new AdaptResult(null, issues, true);
        }

        // Validate path safety
        if (!mediaSource.isPathSafe()) {
            issues.add(LocalRenderSmokeIssue.blocking(
                    LocalRenderSmokeIssueCode.MEDIA_SOURCE_PATH_FORBIDDEN,
                    "Media source path is not safe: " + mediaSource.path()));
            return new AdaptResult(null, issues, true);
        }

        // Validate path is under controlled root
        if (!mediaSource.isUnderControlledRoot(policy.outputRoot())) {
            issues.add(LocalRenderSmokeIssue.blocking(
                    LocalRenderSmokeIssueCode.MEDIA_SOURCE_PATH_FORBIDDEN,
                    "Media source path is outside controlled root: " + mediaSource.path()));
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

        // Extract caption overlay specs
        List<LocalCaptionOverlaySpec> captionSpecs = extractCaptionOverlaySpecs(plan, issues);

        // Build request with controlled media source
        LocalRenderExecutionId executionId = LocalRenderExecutionId.generate();
        Path outputRoot = policy.outputRoot();
        String planId = plan.id() != null ? plan.id().value() : "unknown";

        // Duration from input media (use profile fps as default)
        double effectiveDuration = 3.0; // will be overridden by ffprobe validation

        String outputSubdir = "local-plan-smoke-003-real-media-source-caption-overlay";

        LocalRenderExecutionRequest request = new LocalRenderExecutionRequest(
                executionId,
                planId,
                profile.width,
                profile.height,
                effectiveDuration,
                profile.fps > 0 ? profile.fps : DEFAULT_FPS,
                profile.videoCodec != null ? profile.videoCodec : "h264",
                profile.container != null ? profile.container : "mp4",
                outputRoot.resolve(outputSubdir),
                unsupportedSteps,
                captionSpecs,
                mediaSource,
                plan.safeMetadata()
        );

        issues.add(LocalRenderSmokeIssue.info(
                LocalRenderSmokeIssueCode.SYNTHETIC_INPUT_REQUIRED,
                "Using controlled local media fixture: " + mediaSource.path().getFileName()));

        return new AdaptResult(request, issues, false);
    }

    /**
     * Extracts caption overlay specs from the plan's APPLY_CAPTION_OVERLAY steps.
     */
    private static List<LocalCaptionOverlaySpec> extractCaptionOverlaySpecs(
            FFmpegLibassBasicRenderPlan plan,
            List<LocalRenderSmokeIssue> issues) {

        List<LocalCaptionOverlaySpec> specs = new ArrayList<>();
        int count = 0;

        for (FFmpegLibassBasicRenderStage stage : plan.stages()) {
            for (FFmpegLibassBasicRenderStep step : stage.steps()) {
                if (step.type() != FFmpegLibassBasicRenderStepType.APPLY_CAPTION_OVERLAY) {
                    continue;
                }

                count++;

                if (count > MAX_CAPTION_OVERLAYS) {
                    issues.add(LocalRenderSmokeIssue.warning(
                            LocalRenderSmokeIssueCode.CAPTION_OVERLAY_UNSUPPORTED,
                            "Caption overlay count exceeds max (" + MAX_CAPTION_OVERLAYS + "); skipping remaining"));
                    return specs;
                }

                // Extract parameters
                String captionId = null;
                double startMs = -1;
                double endMs = -1;
                String text = null;
                boolean hasRawFiltergraph = false;
                boolean hasRawAssStyle = false;
                boolean hasExternalSubtitle = false;
                boolean hasFontPath = false;

                if (step.parameters() != null) {
                    for (FFmpegLibassBasicRenderStepParameter param : step.parameters()) {
                        switch (param.name()) {
                            case "captionId" -> captionId = toString(param.value());
                            case "startMs" -> startMs = toDouble(param.value());
                            case "endMs" -> endMs = toDouble(param.value());
                            case "textRef" -> text = toString(param.value());
                            case "rawFiltergraph" -> hasRawFiltergraph = true;
                            case "rawAssStyle" -> hasRawAssStyle = true;
                            case "externalSubtitlePath" -> hasExternalSubtitle = true;
                            case "fontPath" -> hasFontPath = true;
                        }
                    }
                }

                // Reject forbidden fields
                if (hasRawFiltergraph) {
                    issues.add(LocalRenderSmokeIssue.blocking(
                            LocalRenderSmokeIssueCode.CAPTION_RAW_FILTERGRAPH_FORBIDDEN,
                            "Caption contains raw filtergraph: " + captionId));
                    continue;
                }
                if (hasRawAssStyle) {
                    issues.add(LocalRenderSmokeIssue.blocking(
                            LocalRenderSmokeIssueCode.CAPTION_RAW_ASS_STYLE_FORBIDDEN,
                            "Caption contains raw ASS style: " + captionId));
                    continue;
                }
                if (hasExternalSubtitle) {
                    issues.add(LocalRenderSmokeIssue.blocking(
                            LocalRenderSmokeIssueCode.CAPTION_EXTERNAL_SUBTITLE_FORBIDDEN,
                            "Caption references external subtitle: " + captionId));
                    continue;
                }
                if (hasFontPath) {
                    issues.add(LocalRenderSmokeIssue.blocking(
                            LocalRenderSmokeIssueCode.CAPTION_FONT_PATH_FORBIDDEN,
                            "Caption references font path: " + captionId));
                    continue;
                }

                // Validate text
                if (text == null || text.isBlank()) {
                    issues.add(LocalRenderSmokeIssue.error(
                            LocalRenderSmokeIssueCode.CAPTION_TEXT_MISSING,
                            "Caption text is missing: " + captionId));
                    continue;
                }
                if (text.length() > LocalCaptionOverlaySpec.MAX_TEXT_LENGTH) {
                    issues.add(LocalRenderSmokeIssue.warning(
                            LocalRenderSmokeIssueCode.CAPTION_TEXT_TOO_LONG,
                            "Caption text too long (" + text.length() + " chars), will be truncated: " + captionId));
                    text = text.substring(0, LocalCaptionOverlaySpec.MAX_TEXT_LENGTH);
                }

                // Validate time range
                if (startMs < 0) {
                    issues.add(LocalRenderSmokeIssue.error(
                            LocalRenderSmokeIssueCode.CAPTION_TIME_RANGE_INVALID,
                            "Caption startMs < 0: " + captionId));
                    continue;
                }
                if (endMs <= startMs) {
                    issues.add(LocalRenderSmokeIssue.error(
                            LocalRenderSmokeIssueCode.CAPTION_TIME_RANGE_INVALID,
                            "Caption endMs <= startMs: " + captionId));
                    continue;
                }

                // Use step target id as captionId if not provided
                if (captionId == null && step.target() != null) {
                    captionId = step.target().targetId();
                }
                if (captionId == null) {
                    captionId = "caption-" + count;
                }

                try {
                    specs.add(new LocalCaptionOverlaySpec(captionId, text, startMs, endMs));
                } catch (IllegalArgumentException e) {
                    issues.add(LocalRenderSmokeIssue.error(
                            LocalRenderSmokeIssueCode.CAPTION_OVERLAY_UNSUPPORTED,
                            "Invalid caption spec: " + e.getMessage()));
                }
            }
        }

        if (count == 0) {
            issues.add(LocalRenderSmokeIssue.info(
                    LocalRenderSmokeIssueCode.CAPTION_OVERLAY_MISSING,
                    "No APPLY_CAPTION_OVERLAY steps found in plan"));
        }

        return specs;
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

    private static double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); } catch (Exception e) { return 0; }
    }

    private static String toString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private record OutputProfile(int width, int height, int fps, String videoCodec, String container) {}
}
