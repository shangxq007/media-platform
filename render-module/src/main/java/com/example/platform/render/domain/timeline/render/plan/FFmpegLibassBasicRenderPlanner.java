package com.example.platform.render.domain.timeline.render.plan;

import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.domain.timeline.editing.BasicTimelineValidator;
import com.example.platform.render.domain.timeline.render.effect.*;
import com.example.platform.render.domain.timeline.render.transition.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pure, side-effect-free FFmpeg/libass basic timeline render planner.
 * Internal domain model.
 *
 * <p>Composes BasicTimeline validation, FFmpeg baseline effect planning,
 * FFmpeg baseline transition planning, caption/watermark overlay semantics,
 * and output profile validation into a deterministic internal render plan.</p>
 *
 * <p>Does not execute FFmpeg/libass, generate shell commands, expose raw filtergraphs,
 * create RenderJob/Product, call StorageRuntime/ProductRuntime, call OpenCue,
 * or use Artifact DAG.</p>
 *
 * <p>Deterministic ordering: stage order is fixed; within stages, steps are ordered by
 * timeline order → track order → clip startMs → caption startMs → watermark startMs →
 * operation type enum order → entity id lexicographic.</p>
 */
public final class FFmpegLibassBasicRenderPlanner {

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "filter_complex", "filtergraph", "rawCommand", "shell command",
            "Runtime.getRuntime", "ProcessBuilder", "npx remotion",
            "remotion render", "npm install", "pnpm", "yarn");

    private static final Set<String> ALLOWED_CONTAINERS = Set.of("mp4", "mov", "webm");
    private static final Set<String> ALLOWED_VIDEO_CODECS = Set.of("h264", "h265", "hevc", "vp8", "vp9");
    private static final Set<String> ALLOWED_AUDIO_CODECS = Set.of("aac", "mp3", "opus", "vorbis", "flac");

    private FFmpegLibassBasicRenderPlanner() {}

    /**
     * Plan an FFmpeg/libass basic timeline render.
     */
    public static FFmpegLibassBasicRenderPlanningResult plan(FFmpegLibassBasicRenderPlanningRequest request) {
        if (request == null) {
            return FFmpegLibassBasicRenderPlanningResult.failed(List.of(
                    FFmpegLibassBasicRenderPlanIssue.error(
                            FFmpegLibassBasicRenderPlanIssueCode.INVALID_REQUEST,
                            "Request must not be null")));
        }

        FFmpegLibassBasicRenderPolicy policy = request.policy() != null
                ? request.policy() : FFmpegLibassBasicRenderPolicy.conservative();

        TimelineSpec timeline = request.timeline();
        if (timeline == null) {
            return FFmpegLibassBasicRenderPlanningResult.validationFailed(null, List.of(
                    FFmpegLibassBasicRenderPlanIssue.error(
                            FFmpegLibassBasicRenderPlanIssueCode.INVALID_TIMELINE,
                            "Timeline must not be null")));
        }

        List<FFmpegLibassBasicRenderStage> stages = new ArrayList<>();
        List<FFmpegLibassBasicRenderPlanIssue> issues = new ArrayList<>();
        AtomicInteger stageSeq = new AtomicInteger(0);
        AtomicInteger stepSeq = new AtomicInteger(0);

        // Stage 1: VALIDATE_TIMELINE
        stages.add(buildValidationStage(timeline, policy, issues, stageSeq, stepSeq));

        // Stage 2: PREPARE_INPUTS
        stages.add(buildPrepareInputsStage(timeline, stageSeq, stepSeq));

        // Stage 3: PLAN_CLIP_SEQUENCE
        stages.add(buildClipSequenceStage(timeline, stageSeq, stepSeq));

        // Stage 4: PLAN_EFFECTS
        FFmpegBaselineEffectPlan effectPlan = buildEffectPlan(timeline, policy, issues);
        stages.add(buildEffectStage(effectPlan, issues, stageSeq, stepSeq));

        // Stage 5: PLAN_TRANSITIONS
        FFmpegBaselineTransitionPlan transitionPlan = buildTransitionPlan(timeline, policy, issues);
        stages.add(buildTransitionStage(transitionPlan, issues, stageSeq, stepSeq));

        // Stage 6: PLAN_CAPTION_OVERLAYS
        stages.add(buildCaptionOverlayStage(timeline, policy, issues, stageSeq, stepSeq));

        // Stage 7: PLAN_WATERMARK_OVERLAYS
        stages.add(buildWatermarkOverlayStage(timeline, policy, issues, stageSeq, stepSeq));

        // Stage 8: PLAN_FINAL_ASSEMBLY
        stages.add(buildFinalAssemblyStage(timeline, stageSeq, stepSeq));

        // Stage 9: PLAN_OUTPUT_ENCODING
        stages.add(buildOutputEncodingStage(timeline, issues, stageSeq, stepSeq));

        // Stage 10: PLAN_OUTPUT_VERIFICATION
        stages.add(buildVerificationStage(timeline, stageSeq, stepSeq));

        // Build summary
        int totalSteps = stages.stream().mapToInt(s -> s.steps().size()).sum();
        FFmpegLibassBasicRenderPlanSummary summary = new FFmpegLibassBasicRenderPlanSummary(
                stages.size(), totalSteps,
                countStepsByType(stages, FFmpegLibassBasicRenderStepType.VALIDATE_TIMELINE),
                countStepsByType(stages, FFmpegLibassBasicRenderStepType.DECLARE_INPUT_CLIP),
                countStepsByType(stages, FFmpegLibassBasicRenderStepType.APPLY_EFFECT_OPERATION),
                countStepsByType(stages, FFmpegLibassBasicRenderStepType.APPLY_TRANSITION_OPERATION),
                countStepsByType(stages, FFmpegLibassBasicRenderStepType.APPLY_CAPTION_OVERLAY),
                countStepsByType(stages, FFmpegLibassBasicRenderStepType.APPLY_WATERMARK_OVERLAY),
                countStepsByType(stages, FFmpegLibassBasicRenderStepType.ASSEMBLE_CLIP_SEQUENCE),
                countStepsByType(stages, FFmpegLibassBasicRenderStepType.ENCODE_OUTPUT),
                countStepsByType(stages, FFmpegLibassBasicRenderStepType.VERIFY_OUTPUT),
                Map.of());

        // Determine plan status
        FFmpegLibassBasicRenderPlanStatus planStatus = determinePlanStatus(issues, policy);

        FFmpegLibassBasicRenderPlanId planId = new FFmpegLibassBasicRenderPlanId(
                "plan-" + request.id().value());
        FFmpegLibassBasicRenderPlan plan = new FFmpegLibassBasicRenderPlan(
                planId, planStatus, stages, summary, issues, Map.of());

        if (planStatus == FFmpegLibassBasicRenderPlanStatus.BLOCKED
                || planStatus == FFmpegLibassBasicRenderPlanStatus.FAILED) {
            return FFmpegLibassBasicRenderPlanningResult.blocked(issues);
        }
        if (planStatus == FFmpegLibassBasicRenderPlanStatus.UNSUPPORTED) {
            return FFmpegLibassBasicRenderPlanningResult.unsupported(issues);
        }
        if (planStatus == FFmpegLibassBasicRenderPlanStatus.INVALID) {
            return FFmpegLibassBasicRenderPlanningResult.validationFailed(plan, issues);
        }

        return FFmpegLibassBasicRenderPlanningResult.planned(plan);
    }

    // --- Stage builders ---

    private static FFmpegLibassBasicRenderStage buildValidationStage(
            TimelineSpec timeline, FFmpegLibassBasicRenderPolicy policy,
            List<FFmpegLibassBasicRenderPlanIssue> issues,
            AtomicInteger stageSeq, AtomicInteger stepSeq) {

        List<FFmpegLibassBasicRenderStep> steps = new ArrayList<>();

        // Validate timeline
        var validationIssues = BasicTimelineValidator.validate(timeline);
        boolean hasBlocking = validationIssues.stream().anyMatch(i ->
                i.severity() == com.example.platform.render.domain.timeline.editing.TimelineValidationIssueSeverity.BLOCKING
                        || i.severity() == com.example.platform.render.domain.timeline.editing.TimelineValidationIssueSeverity.ERROR);
        boolean hasWarning = validationIssues.stream().anyMatch(i ->
                i.severity() == com.example.platform.render.domain.timeline.editing.TimelineValidationIssueSeverity.WARNING);

        if (hasBlocking) {
            issues.add(FFmpegLibassBasicRenderPlanIssue.blocking(
                    FFmpegLibassBasicRenderPlanIssueCode.TIMELINE_VALIDATION_FAILED,
                    "Timeline validation failed with blocking issues"));
        } else if (hasWarning && policy.failOnTimelineWarnings()) {
            issues.add(FFmpegLibassBasicRenderPlanIssue.error(
                    FFmpegLibassBasicRenderPlanIssueCode.TIMELINE_VALIDATION_FAILED,
                    "Timeline validation has warnings"));
        } else if (hasWarning) {
            issues.add(FFmpegLibassBasicRenderPlanIssue.warning(
                    FFmpegLibassBasicRenderPlanIssueCode.TIMELINE_VALIDATION_FAILED,
                    "Timeline validation has warnings (non-blocking)"));
        }

        steps.add(new FFmpegLibassBasicRenderStep(
                new FFmpegLibassBasicRenderStepId("step-" + stepSeq.incrementAndGet()),
                FFmpegLibassBasicRenderStepType.VALIDATE_TIMELINE,
                new FFmpegLibassBasicRenderStepTarget(
                        FFmpegLibassBasicRenderStepTargetType.TIMELINE, timeline.id(), Map.of()),
                List.of(),
                FFmpegLibassBasicRenderStepSource.TIMELINE_VALIDATION,
                Map.of()));

        return new FFmpegLibassBasicRenderStage(
                new FFmpegLibassBasicRenderStageId("stage-" + stageSeq.incrementAndGet()),
                FFmpegLibassBasicRenderStageType.VALIDATE_TIMELINE,
                hasBlocking ? FFmpegLibassBasicRenderStageStatus.INVALID : FFmpegLibassBasicRenderStageStatus.VALID,
                steps, Map.of());
    }

    private static FFmpegLibassBasicRenderStage buildPrepareInputsStage(
            TimelineSpec timeline, AtomicInteger stageSeq, AtomicInteger stepSeq) {

        List<FFmpegLibassBasicRenderStep> steps = new ArrayList<>();

        // Declare output profile
        if (timeline.outputSpec() != null) {
            List<FFmpegLibassBasicRenderStepParameter> params = new ArrayList<>();
            params.add(new FFmpegLibassBasicRenderStepParameter(
                    "width", FFmpegLibassBasicRenderStepParameterType.INTEGER,
                    timeline.outputSpec().width(), Map.of()));
            params.add(new FFmpegLibassBasicRenderStepParameter(
                    "height", FFmpegLibassBasicRenderStepParameterType.INTEGER,
                    timeline.outputSpec().height(), Map.of()));
            params.add(new FFmpegLibassBasicRenderStepParameter(
                    "fps", FFmpegLibassBasicRenderStepParameterType.DECIMAL,
                    timeline.outputSpec().frameRate(), Map.of()));
            params.add(new FFmpegLibassBasicRenderStepParameter(
                    "container", FFmpegLibassBasicRenderStepParameterType.STRING,
                    timeline.outputSpec().format(), Map.of()));
            params.add(new FFmpegLibassBasicRenderStepParameter(
                    "videoCodec", FFmpegLibassBasicRenderStepParameterType.STRING,
                    timeline.outputSpec().videoCodec(), Map.of()));
            if (timeline.outputSpec().audioSpec() != null) {
                params.add(new FFmpegLibassBasicRenderStepParameter(
                        "audioCodec", FFmpegLibassBasicRenderStepParameterType.STRING,
                        timeline.outputSpec().audioSpec().codec(), Map.of()));
            }

            steps.add(new FFmpegLibassBasicRenderStep(
                    new FFmpegLibassBasicRenderStepId("step-" + stepSeq.incrementAndGet()),
                    FFmpegLibassBasicRenderStepType.DECLARE_OUTPUT_PROFILE,
                    new FFmpegLibassBasicRenderStepTarget(
                            FFmpegLibassBasicRenderStepTargetType.OUTPUT_PROFILE,
                            timeline.id() + "-output", Map.of()),
                    params,
                    FFmpegLibassBasicRenderStepSource.OUTPUT_PROFILE,
                    Map.of()));
        }

        return new FFmpegLibassBasicRenderStage(
                new FFmpegLibassBasicRenderStageId("stage-" + stageSeq.incrementAndGet()),
                FFmpegLibassBasicRenderStageType.PREPARE_INPUTS,
                FFmpegLibassBasicRenderStageStatus.VALID,
                steps, Map.of());
    }

    private static FFmpegLibassBasicRenderStage buildClipSequenceStage(
            TimelineSpec timeline, AtomicInteger stageSeq, AtomicInteger stepSeq) {

        List<FFmpegLibassBasicRenderStep> steps = new ArrayList<>();

        if (timeline.tracks() != null) {
            for (TimelineTrack track : timeline.tracks()) {
                if (track.clips() == null) continue;
                List<TimelineClip> sortedClips = new ArrayList<>(track.clips());
                sortedClips.sort(Comparator.comparingDouble(TimelineClip::timelineStart));
                for (TimelineClip clip : sortedClips) {
                    List<FFmpegLibassBasicRenderStepParameter> params = new ArrayList<>();
                    params.add(new FFmpegLibassBasicRenderStepParameter(
                            "trackId", FFmpegLibassBasicRenderStepParameterType.STRING,
                            track.id(), Map.of()));
                    params.add(new FFmpegLibassBasicRenderStepParameter(
                            "timelineStart", FFmpegLibassBasicRenderStepParameterType.DECIMAL,
                            clip.timelineStart(), Map.of()));
                    params.add(new FFmpegLibassBasicRenderStepParameter(
                            "clipDuration", FFmpegLibassBasicRenderStepParameterType.DECIMAL,
                            clip.clipDuration(), Map.of()));

                    steps.add(new FFmpegLibassBasicRenderStep(
                            new FFmpegLibassBasicRenderStepId("step-" + stepSeq.incrementAndGet()),
                            FFmpegLibassBasicRenderStepType.DECLARE_INPUT_CLIP,
                            new FFmpegLibassBasicRenderStepTarget(
                                    FFmpegLibassBasicRenderStepTargetType.CLIP, clip.id(), Map.of()),
                            params,
                            FFmpegLibassBasicRenderStepSource.CLIP_SEQUENCE,
                            Map.of()));
                }
            }
        }

        return new FFmpegLibassBasicRenderStage(
                new FFmpegLibassBasicRenderStageId("stage-" + stageSeq.incrementAndGet()),
                FFmpegLibassBasicRenderStageType.PLAN_CLIP_SEQUENCE,
                FFmpegLibassBasicRenderStageStatus.VALID,
                steps, Map.of());
    }

    private static FFmpegBaselineEffectPlan buildEffectPlan(
            TimelineSpec timeline, FFmpegLibassBasicRenderPolicy policy,
            List<FFmpegLibassBasicRenderPlanIssue> issues) {

        FFmpegBaselineEffectPolicy effectPolicy = new FFmpegBaselineEffectPolicy(
                policy.allowPocEffects(), false, policy.allowWarnings(),
                true, true);
        var effectRequest = new FFmpegBaselineEffectPlanningRequest(
                new FFmpegBaselineEffectPlanningRequestId("effect-" + timeline.id()),
                timeline, effectPolicy, Map.of());
        var effectResult = FFmpegBaselineEffectPlanner.plan(effectRequest);

        if (effectResult.status() == FFmpegBaselineEffectPlanningResultStatus.BLOCKED) {
            issues.add(FFmpegLibassBasicRenderPlanIssue.blocking(
                    FFmpegLibassBasicRenderPlanIssueCode.EFFECT_PLAN_BLOCKED,
                    "Effect plan blocked"));
        } else if (effectResult.status() == FFmpegBaselineEffectPlanningResultStatus.UNSUPPORTED) {
            issues.add(FFmpegLibassBasicRenderPlanIssue.warning(
                    FFmpegLibassBasicRenderPlanIssueCode.EFFECT_PLAN_UNSUPPORTED,
                    "Effect plan has unsupported effects"));
        } else if (effectResult.status() == FFmpegBaselineEffectPlanningResultStatus.FAILED
                || effectResult.status() == FFmpegBaselineEffectPlanningResultStatus.VALIDATION_FAILED) {
            if (policy.failOnEffectWarnings()) {
                issues.add(FFmpegLibassBasicRenderPlanIssue.error(
                        FFmpegLibassBasicRenderPlanIssueCode.EFFECT_PLAN_FAILED,
                        "Effect plan validation failed"));
            } else {
                issues.add(FFmpegLibassBasicRenderPlanIssue.warning(
                        FFmpegLibassBasicRenderPlanIssueCode.EFFECT_PLAN_FAILED,
                        "Effect plan validation failed (non-blocking)"));
            }
        }

        return effectResult.plan();
    }

    private static FFmpegLibassBasicRenderStage buildEffectStage(
            FFmpegBaselineEffectPlan effectPlan,
            List<FFmpegLibassBasicRenderPlanIssue> issues,
            AtomicInteger stageSeq, AtomicInteger stepSeq) {

        List<FFmpegLibassBasicRenderStep> steps = new ArrayList<>();

        if (effectPlan != null && effectPlan.operations() != null) {
            for (var op : effectPlan.operations()) {
                List<FFmpegLibassBasicRenderStepParameter> params = new ArrayList<>();
                params.add(new FFmpegLibassBasicRenderStepParameter(
                        "effectOperationId", FFmpegLibassBasicRenderStepParameterType.STRING,
                        op.id().value(), Map.of()));
                params.add(new FFmpegLibassBasicRenderStepParameter(
                        "operationType", FFmpegLibassBasicRenderStepParameterType.STRING,
                        op.type().name(), Map.of()));
                if (op.target() != null) {
                    params.add(new FFmpegLibassBasicRenderStepParameter(
                            "targetId", FFmpegLibassBasicRenderStepParameterType.STRING,
                            op.target().targetId(), Map.of()));
                }

                steps.add(new FFmpegLibassBasicRenderStep(
                        new FFmpegLibassBasicRenderStepId("step-" + stepSeq.incrementAndGet()),
                        FFmpegLibassBasicRenderStepType.APPLY_EFFECT_OPERATION,
                        op.target() != null ? new FFmpegLibassBasicRenderStepTarget(
                                FFmpegLibassBasicRenderStepTargetType.EFFECT_OPERATION,
                                op.id().value(), Map.of()) : null,
                        params,
                        FFmpegLibassBasicRenderStepSource.EFFECT_PLAN,
                        Map.of()));
            }
        }

        return new FFmpegLibassBasicRenderStage(
                new FFmpegLibassBasicRenderStageId("stage-" + stageSeq.incrementAndGet()),
                FFmpegLibassBasicRenderStageType.PLAN_EFFECTS,
                FFmpegLibassBasicRenderStageStatus.VALID,
                steps, Map.of());
    }

    private static FFmpegBaselineTransitionPlan buildTransitionPlan(
            TimelineSpec timeline, FFmpegLibassBasicRenderPolicy policy,
            List<FFmpegLibassBasicRenderPlanIssue> issues) {

        FFmpegBaselineTransitionPolicy transitionPolicy = new FFmpegBaselineTransitionPolicy(
                policy.allowPocTransitions(), false, policy.allowWarnings(),
                true, true, true, true);
        var transitionRequest = new FFmpegBaselineTransitionPlanningRequest(
                new FFmpegBaselineTransitionPlanningRequestId("transition-" + timeline.id()),
                timeline, transitionPolicy, Map.of());
        var transitionResult = FFmpegBaselineTransitionPlanner.plan(transitionRequest);

        if (transitionResult.status() == FFmpegBaselineTransitionPlanningResultStatus.BLOCKED) {
            issues.add(FFmpegLibassBasicRenderPlanIssue.blocking(
                    FFmpegLibassBasicRenderPlanIssueCode.TRANSITION_PLAN_BLOCKED,
                    "Transition plan blocked"));
        } else if (transitionResult.status() == FFmpegBaselineTransitionPlanningResultStatus.UNSUPPORTED) {
            issues.add(FFmpegLibassBasicRenderPlanIssue.warning(
                    FFmpegLibassBasicRenderPlanIssueCode.TRANSITION_PLAN_UNSUPPORTED,
                    "Transition plan has unsupported transitions"));
        } else if (transitionResult.status() == FFmpegBaselineTransitionPlanningResultStatus.FAILED
                || transitionResult.status() == FFmpegBaselineTransitionPlanningResultStatus.VALIDATION_FAILED) {
            if (policy.failOnTransitionWarnings()) {
                issues.add(FFmpegLibassBasicRenderPlanIssue.error(
                        FFmpegLibassBasicRenderPlanIssueCode.TRANSITION_PLAN_FAILED,
                        "Transition plan validation failed"));
            } else {
                issues.add(FFmpegLibassBasicRenderPlanIssue.warning(
                        FFmpegLibassBasicRenderPlanIssueCode.TRANSITION_PLAN_FAILED,
                        "Transition plan validation failed (non-blocking)"));
            }
        }

        return transitionResult.plan();
    }

    private static FFmpegLibassBasicRenderStage buildTransitionStage(
            FFmpegBaselineTransitionPlan transitionPlan,
            List<FFmpegLibassBasicRenderPlanIssue> issues,
            AtomicInteger stageSeq, AtomicInteger stepSeq) {

        List<FFmpegLibassBasicRenderStep> steps = new ArrayList<>();

        if (transitionPlan != null && transitionPlan.operations() != null) {
            for (var op : transitionPlan.operations()) {
                List<FFmpegLibassBasicRenderStepParameter> params = new ArrayList<>();
                params.add(new FFmpegLibassBasicRenderStepParameter(
                        "transitionOperationId", FFmpegLibassBasicRenderStepParameterType.STRING,
                        op.id().value(), Map.of()));
                params.add(new FFmpegLibassBasicRenderStepParameter(
                        "operationType", FFmpegLibassBasicRenderStepParameterType.STRING,
                        op.type().name(), Map.of()));
                if (op.target() != null) {
                    params.add(new FFmpegLibassBasicRenderStepParameter(
                            "fromClipId", FFmpegLibassBasicRenderStepParameterType.STRING,
                            op.target().fromClipId(), Map.of()));
                    params.add(new FFmpegLibassBasicRenderStepParameter(
                            "toClipId", FFmpegLibassBasicRenderStepParameterType.STRING,
                            op.target().toClipId(), Map.of()));
                }

                steps.add(new FFmpegLibassBasicRenderStep(
                        new FFmpegLibassBasicRenderStepId("step-" + stepSeq.incrementAndGet()),
                        FFmpegLibassBasicRenderStepType.APPLY_TRANSITION_OPERATION,
                        op.target() != null ? new FFmpegLibassBasicRenderStepTarget(
                                FFmpegLibassBasicRenderStepTargetType.TRANSITION_OPERATION,
                                op.id().value(), Map.of()) : null,
                        params,
                        FFmpegLibassBasicRenderStepSource.TRANSITION_PLAN,
                        Map.of()));
            }
        }

        return new FFmpegLibassBasicRenderStage(
                new FFmpegLibassBasicRenderStageId("stage-" + stageSeq.incrementAndGet()),
                FFmpegLibassBasicRenderStageType.PLAN_TRANSITIONS,
                FFmpegLibassBasicRenderStageStatus.VALID,
                steps, Map.of());
    }

    private static FFmpegLibassBasicRenderStage buildCaptionOverlayStage(
            TimelineSpec timeline, FFmpegLibassBasicRenderPolicy policy,
            List<FFmpegLibassBasicRenderPlanIssue> issues,
            AtomicInteger stageSeq, AtomicInteger stepSeq) {

        List<FFmpegLibassBasicRenderStep> steps = new ArrayList<>();

        if (timeline.textOverlays() != null) {
            for (TimelineTextOverlay overlay : timeline.textOverlays()) {
                if (policy.requireCaptionOverlayValidation()) {
                    if (overlay.text() == null || overlay.text().isBlank()) {
                        issues.add(FFmpegLibassBasicRenderPlanIssue.error(
                                FFmpegLibassBasicRenderPlanIssueCode.CAPTION_OVERLAY_INVALID,
                                "Caption overlay text is blank: " + overlay.id()));
                    }
                    if (overlay.startTime() < 0) {
                        issues.add(FFmpegLibassBasicRenderPlanIssue.error(
                                FFmpegLibassBasicRenderPlanIssueCode.CAPTION_OVERLAY_INVALID,
                                "Caption overlay start time is negative: " + overlay.id()));
                    }
                    if (overlay.duration() <= 0) {
                        issues.add(FFmpegLibassBasicRenderPlanIssue.error(
                                FFmpegLibassBasicRenderPlanIssueCode.CAPTION_OVERLAY_INVALID,
                                "Caption overlay duration must be positive: " + overlay.id()));
                    }
                }

                List<FFmpegLibassBasicRenderStepParameter> params = new ArrayList<>();
                params.add(new FFmpegLibassBasicRenderStepParameter(
                        "captionId", FFmpegLibassBasicRenderStepParameterType.STRING,
                        overlay.id(), Map.of()));
                params.add(new FFmpegLibassBasicRenderStepParameter(
                        "startMs", FFmpegLibassBasicRenderStepParameterType.DECIMAL,
                        overlay.startTime() * 1000, Map.of()));
                params.add(new FFmpegLibassBasicRenderStepParameter(
                        "endMs", FFmpegLibassBasicRenderStepParameterType.DECIMAL,
                        (overlay.startTime() + overlay.duration()) * 1000, Map.of()));
                params.add(new FFmpegLibassBasicRenderStepParameter(
                        "textRef", FFmpegLibassBasicRenderStepParameterType.STRING,
                        overlay.text(), Map.of()));

                steps.add(new FFmpegLibassBasicRenderStep(
                        new FFmpegLibassBasicRenderStepId("step-" + stepSeq.incrementAndGet()),
                        FFmpegLibassBasicRenderStepType.APPLY_CAPTION_OVERLAY,
                        new FFmpegLibassBasicRenderStepTarget(
                                FFmpegLibassBasicRenderStepTargetType.CAPTION,
                                overlay.id(), Map.of()),
                        params,
                        FFmpegLibassBasicRenderStepSource.CAPTION_OVERLAY,
                        Map.of()));
            }
        }

        return new FFmpegLibassBasicRenderStage(
                new FFmpegLibassBasicRenderStageId("stage-" + stageSeq.incrementAndGet()),
                FFmpegLibassBasicRenderStageType.PLAN_CAPTION_OVERLAYS,
                FFmpegLibassBasicRenderStageStatus.VALID,
                steps, Map.of());
    }

    private static FFmpegLibassBasicRenderStage buildWatermarkOverlayStage(
            TimelineSpec timeline, FFmpegLibassBasicRenderPolicy policy,
            List<FFmpegLibassBasicRenderPlanIssue> issues,
            AtomicInteger stageSeq, AtomicInteger stepSeq) {

        List<FFmpegLibassBasicRenderStep> steps = new ArrayList<>();

        // Watermarks are stored in metadata in the current model
        if (timeline.metadata() != null) {
            Map<String, String> meta = timeline.metadata();
            if (meta.containsKey("watermark.placement") || meta.containsKey("watermark.opacity")) {
                if (policy.requireWatermarkOverlayValidation()) {
                    String opacityStr = meta.get("watermark.opacity");
                    if (opacityStr != null) {
                        try {
                            double opacity = Double.parseDouble(opacityStr);
                            if (opacity < 0 || opacity > 1) {
                                issues.add(FFmpegLibassBasicRenderPlanIssue.error(
                                        FFmpegLibassBasicRenderPlanIssueCode.WATERMARK_OVERLAY_INVALID,
                                        "Watermark opacity must be 0..1: " + opacity));
                            }
                        } catch (NumberFormatException e) {
                            issues.add(FFmpegLibassBasicRenderPlanIssue.error(
                                    FFmpegLibassBasicRenderPlanIssueCode.WATERMARK_OVERLAY_INVALID,
                                    "Watermark opacity is not a valid number"));
                        }
                    }
                }

                List<FFmpegLibassBasicRenderStepParameter> params = new ArrayList<>();
                params.add(new FFmpegLibassBasicRenderStepParameter(
                        "watermarkId", FFmpegLibassBasicRenderStepParameterType.STRING,
                        "watermark-default", Map.of()));
                if (meta.containsKey("watermark.placement")) {
                    params.add(new FFmpegLibassBasicRenderStepParameter(
                            "placement", FFmpegLibassBasicRenderStepParameterType.STRING,
                            meta.get("watermark.placement"), Map.of()));
                }
                if (meta.containsKey("watermark.opacity")) {
                    params.add(new FFmpegLibassBasicRenderStepParameter(
                            "opacity", FFmpegLibassBasicRenderStepParameterType.PERCENT,
                            meta.get("watermark.opacity"), Map.of()));
                }

                steps.add(new FFmpegLibassBasicRenderStep(
                        new FFmpegLibassBasicRenderStepId("step-" + stepSeq.incrementAndGet()),
                        FFmpegLibassBasicRenderStepType.APPLY_WATERMARK_OVERLAY,
                        new FFmpegLibassBasicRenderStepTarget(
                                FFmpegLibassBasicRenderStepTargetType.WATERMARK,
                                "watermark-default", Map.of()),
                        params,
                        FFmpegLibassBasicRenderStepSource.CAPTION_OVERLAY,
                        Map.of()));
            }
        }

        return new FFmpegLibassBasicRenderStage(
                new FFmpegLibassBasicRenderStageId("stage-" + stageSeq.incrementAndGet()),
                FFmpegLibassBasicRenderStageType.PLAN_WATERMARK_OVERLAYS,
                FFmpegLibassBasicRenderStageStatus.VALID,
                steps, Map.of());
    }

    private static FFmpegLibassBasicRenderStage buildFinalAssemblyStage(
            TimelineSpec timeline, AtomicInteger stageSeq, AtomicInteger stepSeq) {

        List<FFmpegLibassBasicRenderStep> steps = new ArrayList<>();

        if (timeline.tracks() != null) {
            for (TimelineTrack track : timeline.tracks()) {
                List<FFmpegLibassBasicRenderStepParameter> params = new ArrayList<>();
                params.add(new FFmpegLibassBasicRenderStepParameter(
                        "trackId", FFmpegLibassBasicRenderStepParameterType.STRING,
                        track.id(), Map.of()));
                if (track.clips() != null) {
                    List<String> clipIds = track.clips().stream()
                            .sorted(Comparator.comparingDouble(TimelineClip::timelineStart))
                            .map(TimelineClip::id)
                            .toList();
                    params.add(new FFmpegLibassBasicRenderStepParameter(
                            "clipIds", FFmpegLibassBasicRenderStepParameterType.STRING,
                            String.join(",", clipIds), Map.of()));
                }

                steps.add(new FFmpegLibassBasicRenderStep(
                        new FFmpegLibassBasicRenderStepId("step-" + stepSeq.incrementAndGet()),
                        FFmpegLibassBasicRenderStepType.ASSEMBLE_CLIP_SEQUENCE,
                        new FFmpegLibassBasicRenderStepTarget(
                                FFmpegLibassBasicRenderStepTargetType.TRACK,
                                track.id(), Map.of()),
                        params,
                        FFmpegLibassBasicRenderStepSource.FINAL_ASSEMBLY,
                        Map.of()));
            }
        }

        return new FFmpegLibassBasicRenderStage(
                new FFmpegLibassBasicRenderStageId("stage-" + stageSeq.incrementAndGet()),
                FFmpegLibassBasicRenderStageType.PLAN_FINAL_ASSEMBLY,
                FFmpegLibassBasicRenderStageStatus.VALID,
                steps, Map.of());
    }

    private static FFmpegLibassBasicRenderStage buildOutputEncodingStage(
            TimelineSpec timeline, List<FFmpegLibassBasicRenderPlanIssue> issues,
            AtomicInteger stageSeq, AtomicInteger stepSeq) {

        List<FFmpegLibassBasicRenderStep> steps = new ArrayList<>();

        if (timeline.outputSpec() != null) {
            String container = timeline.outputSpec().format();
            String videoCodec = timeline.outputSpec().videoCodec();
            String audioCodec = timeline.outputSpec().audioSpec() != null
                    ? timeline.outputSpec().audioSpec().codec() : null;

            if (container != null && !ALLOWED_CONTAINERS.contains(container.toLowerCase())) {
                issues.add(FFmpegLibassBasicRenderPlanIssue.blocking(
                        FFmpegLibassBasicRenderPlanIssueCode.UNSUPPORTED_OUTPUT_CONTAINER,
                        "Unsupported container: " + container));
            }
            if (videoCodec != null && !ALLOWED_VIDEO_CODECS.contains(videoCodec.toLowerCase())) {
                issues.add(FFmpegLibassBasicRenderPlanIssue.blocking(
                        FFmpegLibassBasicRenderPlanIssueCode.UNSUPPORTED_VIDEO_CODEC,
                        "Unsupported video codec: " + videoCodec));
            }
            if (audioCodec != null && !ALLOWED_AUDIO_CODECS.contains(audioCodec.toLowerCase())) {
                issues.add(FFmpegLibassBasicRenderPlanIssue.warning(
                        FFmpegLibassBasicRenderPlanIssueCode.UNSUPPORTED_AUDIO_CODEC,
                        "Unsupported audio codec: " + audioCodec));
            }

            List<FFmpegLibassBasicRenderStepParameter> params = new ArrayList<>();
            params.add(new FFmpegLibassBasicRenderStepParameter(
                    "container", FFmpegLibassBasicRenderStepParameterType.STRING,
                    container, Map.of()));
            params.add(new FFmpegLibassBasicRenderStepParameter(
                    "videoCodec", FFmpegLibassBasicRenderStepParameterType.STRING,
                    videoCodec, Map.of()));
            if (audioCodec != null) {
                params.add(new FFmpegLibassBasicRenderStepParameter(
                        "audioCodec", FFmpegLibassBasicRenderStepParameterType.STRING,
                        audioCodec, Map.of()));
            }

            steps.add(new FFmpegLibassBasicRenderStep(
                    new FFmpegLibassBasicRenderStepId("step-" + stepSeq.incrementAndGet()),
                    FFmpegLibassBasicRenderStepType.ENCODE_OUTPUT,
                    new FFmpegLibassBasicRenderStepTarget(
                            FFmpegLibassBasicRenderStepTargetType.FINAL_OUTPUT,
                            timeline.id() + "-output", Map.of()),
                    params,
                    FFmpegLibassBasicRenderStepSource.OUTPUT_PROFILE,
                    Map.of()));
        }

        return new FFmpegLibassBasicRenderStage(
                new FFmpegLibassBasicRenderStageId("stage-" + stageSeq.incrementAndGet()),
                FFmpegLibassBasicRenderStageType.PLAN_OUTPUT_ENCODING,
                FFmpegLibassBasicRenderStageStatus.VALID,
                steps, Map.of());
    }

    private static FFmpegLibassBasicRenderStage buildVerificationStage(
            TimelineSpec timeline, AtomicInteger stageSeq, AtomicInteger stepSeq) {

        List<FFmpegLibassBasicRenderStep> steps = new ArrayList<>();

        List<FFmpegLibassBasicRenderStepParameter> params = new ArrayList<>();
        if (timeline.outputSpec() != null) {
            params.add(new FFmpegLibassBasicRenderStepParameter(
                    "expectedContainer", FFmpegLibassBasicRenderStepParameterType.STRING,
                    timeline.outputSpec().format(), Map.of()));
            params.add(new FFmpegLibassBasicRenderStepParameter(
                    "expectedVideoCodec", FFmpegLibassBasicRenderStepParameterType.STRING,
                    timeline.outputSpec().videoCodec(), Map.of()));
        }

        steps.add(new FFmpegLibassBasicRenderStep(
                new FFmpegLibassBasicRenderStepId("step-" + stepSeq.incrementAndGet()),
                FFmpegLibassBasicRenderStepType.VERIFY_OUTPUT,
                new FFmpegLibassBasicRenderStepTarget(
                        FFmpegLibassBasicRenderStepTargetType.FINAL_OUTPUT,
                        timeline.id() + "-output", Map.of()),
                params,
                FFmpegLibassBasicRenderStepSource.VERIFICATION,
                Map.of()));

        return new FFmpegLibassBasicRenderStage(
                new FFmpegLibassBasicRenderStageId("stage-" + stageSeq.incrementAndGet()),
                FFmpegLibassBasicRenderStageType.PLAN_OUTPUT_VERIFICATION,
                FFmpegLibassBasicRenderStageStatus.VALID,
                steps, Map.of());
    }

    // --- Helpers ---

    private static int countStepsByType(List<FFmpegLibassBasicRenderStage> stages,
                                         FFmpegLibassBasicRenderStepType type) {
        return stages.stream()
                .flatMap(s -> s.steps().stream())
                .filter(s -> s.type() == type)
                .mapToInt(s -> 1)
                .sum();
    }

    private static FFmpegLibassBasicRenderPlanStatus determinePlanStatus(
            List<FFmpegLibassBasicRenderPlanIssue> issues, FFmpegLibassBasicRenderPolicy policy) {
        boolean hasBlocking = issues.stream().anyMatch(i ->
                i.severity() == FFmpegLibassBasicRenderPlanIssueSeverity.BLOCKING);
        boolean hasError = issues.stream().anyMatch(i ->
                i.severity() == FFmpegLibassBasicRenderPlanIssueSeverity.ERROR);
        boolean hasWarning = issues.stream().anyMatch(i ->
                i.severity() == FFmpegLibassBasicRenderPlanIssueSeverity.WARNING);

        if (hasBlocking) return FFmpegLibassBasicRenderPlanStatus.BLOCKED;
        if (hasError) {
            return policy.failOnUnsupportedOutputProfile()
                    ? FFmpegLibassBasicRenderPlanStatus.INVALID
                    : FFmpegLibassBasicRenderPlanStatus.VALID_WITH_WARNINGS;
        }
        if (hasWarning && !policy.allowWarnings()) {
            return FFmpegLibassBasicRenderPlanStatus.INVALID;
        }
        if (hasWarning) return FFmpegLibassBasicRenderPlanStatus.VALID_WITH_WARNINGS;
        return FFmpegLibassBasicRenderPlanStatus.READY;
    }
}
