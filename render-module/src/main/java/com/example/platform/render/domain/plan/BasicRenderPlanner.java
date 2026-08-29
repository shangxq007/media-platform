package com.example.platform.render.domain.plan;


import com.example.platform.render.domain.effect.*;
import com.example.platform.render.domain.transition.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.domain.interchange.TimelineTextOverlay;
import com.example.platform.render.domain.legacy.TimelineClip;
import com.example.platform.render.domain.legacy.TimelineTrack;

/**
 * Pure, side-effect-free basic timeline render planner.
 * Internal domain model.
 *
 * <p>Composes BasicTimeline validation, baseline effect planning,
 * baseline transition planning, caption/watermark overlay semantics,
 * and output profile validation into a deterministic internal render plan.</p>
 *
 * <p>Does not invoke provider runtimes, generate shell commands, expose raw provider expressions,
 * create RenderJob/Product, call StorageRuntime/ProductRuntime, call OpenCue,
 * or use Artifact DAG.</p>
 *
 * <p>Deterministic ordering: stage order is fixed; within stages, steps are ordered by
 * timeline order → track order → clip startMs → caption startMs → watermark startMs →
 * operation type enum order → entity id lexicographic.</p>
 */
public final class BasicRenderPlanner {

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "provider_expression", "provider expression", "rawCommand", "shell command",
            "Runtime.getRuntime", "ProcessBuilder", "npx remotion",
            "remotion render", "npm install", "pnpm", "yarn");

    private static final Set<String> ALLOWED_CONTAINERS = Set.of("mp4", "mov", "webm");
    private static final Set<String> ALLOWED_VIDEO_CODECS = Set.of("h264", "h265", "hevc", "vp8", "vp9");
    private static final Set<String> ALLOWED_AUDIO_CODECS = Set.of("aac", "mp3", "opus", "vorbis", "flac");

    private BasicRenderPlanner() {}

    /**
     * Plan a basic timeline render.
     */
    public static BasicRenderPlanningResult plan(BasicRenderPlanningRequest request) {
        if (request == null) {
            return BasicRenderPlanningResult.failed(List.of(
                    BasicRenderPlanIssue.error(
                            BasicRenderPlanIssueCode.INVALID_REQUEST,
                            "Request must not be null")));
        }

        BasicRenderPolicy policy = request.policy() != null
                ? request.policy() : BasicRenderPolicy.conservative();

        TimelineSpec timeline = request.timeline();
        if (timeline == null) {
            return BasicRenderPlanningResult.validationFailed(null, List.of(
                    BasicRenderPlanIssue.error(
                            BasicRenderPlanIssueCode.INVALID_TIMELINE,
                            "Timeline must not be null")));
        }

        List<BasicRenderStage> stages = new ArrayList<>();
        List<BasicRenderPlanIssue> issues = new ArrayList<>();
        AtomicInteger stageSeq = new AtomicInteger(0);
        AtomicInteger stepSeq = new AtomicInteger(0);

        // Stage 1: VALIDATE_TIMELINE
        stages.add(buildValidationStage(timeline, policy, issues, stageSeq, stepSeq));

        // Stage 2: PREPARE_INPUTS
        stages.add(buildPrepareInputsStage(timeline, stageSeq, stepSeq));

        // Stage 3: PLAN_CLIP_SEQUENCE
        stages.add(buildClipSequenceStage(timeline, stageSeq, stepSeq));

        // Stage 4: PLAN_EFFECTS
        BaselineEffectPlan effectPlan = buildEffectPlan(timeline, policy, issues);
        stages.add(buildEffectStage(effectPlan, issues, stageSeq, stepSeq));

        // Stage 5: PLAN_TRANSITIONS
        BaselineTransitionPlan transitionPlan = buildTransitionPlan(timeline, policy, issues);
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
        BasicRenderPlanSummary summary = new BasicRenderPlanSummary(
                stages.size(), totalSteps,
                countStepsByType(stages, BasicRenderStepType.VALIDATE_TIMELINE),
                countStepsByType(stages, BasicRenderStepType.DECLARE_INPUT_CLIP),
                countStepsByType(stages, BasicRenderStepType.APPLY_EFFECT_OPERATION),
                countStepsByType(stages, BasicRenderStepType.APPLY_TRANSITION_OPERATION),
                countStepsByType(stages, BasicRenderStepType.APPLY_CAPTION_OVERLAY),
                countStepsByType(stages, BasicRenderStepType.APPLY_WATERMARK_OVERLAY),
                countStepsByType(stages, BasicRenderStepType.ASSEMBLE_CLIP_SEQUENCE),
                countStepsByType(stages, BasicRenderStepType.ENCODE_OUTPUT),
                countStepsByType(stages, BasicRenderStepType.VERIFY_OUTPUT),
                Map.of());

        // Determine plan status
        BasicRenderPlanStatus planStatus = determinePlanStatus(issues, policy);

        BasicRenderPlanId planId = new BasicRenderPlanId(
                "plan-" + request.id().value());
        BasicRenderPlan plan = new BasicRenderPlan(
                planId, planStatus, stages, summary, issues, Map.of());

        if (planStatus == BasicRenderPlanStatus.BLOCKED
                || planStatus == BasicRenderPlanStatus.FAILED) {
            return BasicRenderPlanningResult.blocked(issues);
        }
        if (planStatus == BasicRenderPlanStatus.UNSUPPORTED) {
            return BasicRenderPlanningResult.unsupported(issues);
        }
        if (planStatus == BasicRenderPlanStatus.INVALID) {
            return BasicRenderPlanningResult.validationFailed(plan, issues);
        }

        return BasicRenderPlanningResult.planned(plan);
    }

    // --- Stage builders ---

    private static BasicRenderStage buildValidationStage(
            TimelineSpec timeline, BasicRenderPolicy policy,
            List<BasicRenderPlanIssue> issues,
            AtomicInteger stageSeq, AtomicInteger stepSeq) {

        List<BasicRenderStep> steps = new ArrayList<>();

        // ROADMAP_19 FINAL AUTHORITY: TimelineSpec is a non-authoritative
        // execution projection; its compact constructor enforces structural
        // invariants. The BasicTimelineValidator parallel model is DELETED.


        steps.add(new BasicRenderStep(
                new BasicRenderStepId("step-" + stepSeq.incrementAndGet()),
                BasicRenderStepType.VALIDATE_TIMELINE,
                new BasicRenderStepTarget(
                        BasicRenderStepTargetType.TIMELINE, timeline.id(), Map.of()),
                List.of(),
                BasicRenderStepSource.TIMELINE_VALIDATION,
                Map.of()));

        return new BasicRenderStage(
                new BasicRenderStageId("stage-" + stageSeq.incrementAndGet()),
                BasicRenderStageType.VALIDATE_TIMELINE,
                BasicRenderStageStatus.VALID,
                steps, Map.of());
    }

    private static BasicRenderStage buildPrepareInputsStage(
            TimelineSpec timeline, AtomicInteger stageSeq, AtomicInteger stepSeq) {

        List<BasicRenderStep> steps = new ArrayList<>();

        // Declare output profile
        if (timeline.outputSpec() != null) {
            List<BasicRenderStepParameter> params = new ArrayList<>();
            params.add(new BasicRenderStepParameter(
                    "width", BasicRenderStepParameterType.INTEGER,
                    timeline.outputSpec().width(), Map.of()));
            params.add(new BasicRenderStepParameter(
                    "height", BasicRenderStepParameterType.INTEGER,
                    timeline.outputSpec().height(), Map.of()));
            params.add(new BasicRenderStepParameter(
                    "fps", BasicRenderStepParameterType.DECIMAL,
                    timeline.outputSpec().frameRate(), Map.of()));
            params.add(new BasicRenderStepParameter(
                    "container", BasicRenderStepParameterType.STRING,
                    timeline.outputSpec().format(), Map.of()));
            params.add(new BasicRenderStepParameter(
                    "videoCodec", BasicRenderStepParameterType.STRING,
                    timeline.outputSpec().videoCodec(), Map.of()));
            if (timeline.outputSpec().audioSpec() != null) {
                params.add(new BasicRenderStepParameter(
                        "audioCodec", BasicRenderStepParameterType.STRING,
                        timeline.outputSpec().audioSpec().codec(), Map.of()));
            }

            steps.add(new BasicRenderStep(
                    new BasicRenderStepId("step-" + stepSeq.incrementAndGet()),
                    BasicRenderStepType.DECLARE_OUTPUT_PROFILE,
                    new BasicRenderStepTarget(
                            BasicRenderStepTargetType.OUTPUT_PROFILE,
                            timeline.id() + "-output", Map.of()),
                    params,
                    BasicRenderStepSource.OUTPUT_PROFILE,
                    Map.of()));
        }

        return new BasicRenderStage(
                new BasicRenderStageId("stage-" + stageSeq.incrementAndGet()),
                BasicRenderStageType.PREPARE_INPUTS,
                BasicRenderStageStatus.VALID,
                steps, Map.of());
    }

    private static BasicRenderStage buildClipSequenceStage(
            TimelineSpec timeline, AtomicInteger stageSeq, AtomicInteger stepSeq) {

        List<BasicRenderStep> steps = new ArrayList<>();

        if (timeline.tracks() != null) {
            for (TimelineTrack track : timeline.tracks()) {
                if (track.clips() == null) continue;
                List<TimelineClip> sortedClips = new ArrayList<>(track.clips());
                sortedClips.sort(Comparator.comparingDouble(TimelineClip::timelineStart));
                for (TimelineClip clip : sortedClips) {
                    List<BasicRenderStepParameter> params = new ArrayList<>();
                    params.add(new BasicRenderStepParameter(
                            "trackId", BasicRenderStepParameterType.STRING,
                            track.id(), Map.of()));
                    params.add(new BasicRenderStepParameter(
                            "timelineStart", BasicRenderStepParameterType.DECIMAL,
                            clip.timelineStart(), Map.of()));
                    params.add(new BasicRenderStepParameter(
                            "clipDuration", BasicRenderStepParameterType.DECIMAL,
                            clip.clipDuration(), Map.of()));

                    steps.add(new BasicRenderStep(
                            new BasicRenderStepId("step-" + stepSeq.incrementAndGet()),
                            BasicRenderStepType.DECLARE_INPUT_CLIP,
                            new BasicRenderStepTarget(
                                    BasicRenderStepTargetType.CLIP, clip.id(), Map.of()),
                            params,
                            BasicRenderStepSource.CLIP_SEQUENCE,
                            Map.of()));
                }
            }
        }

        return new BasicRenderStage(
                new BasicRenderStageId("stage-" + stageSeq.incrementAndGet()),
                BasicRenderStageType.PLAN_CLIP_SEQUENCE,
                BasicRenderStageStatus.VALID,
                steps, Map.of());
    }

    private static BaselineEffectPlan buildEffectPlan(
            TimelineSpec timeline, BasicRenderPolicy policy,
            List<BasicRenderPlanIssue> issues) {

        BaselineEffectPolicy effectPolicy = new BaselineEffectPolicy(
                policy.allowPocEffects(), false, policy.allowWarnings(),
                true, true);
        var effectRequest = new BaselineEffectPlanningRequest(
                new BaselineEffectPlanningRequestId("effect-" + timeline.id()),
                timeline, effectPolicy, Map.of());
        var effectResult = BaselineEffectPlanner.plan(effectRequest);

        if (effectResult.status() == BaselineEffectPlanningResultStatus.BLOCKED) {
            issues.add(BasicRenderPlanIssue.blocking(
                    BasicRenderPlanIssueCode.EFFECT_PLAN_BLOCKED,
                    "Effect plan blocked"));
        } else if (effectResult.status() == BaselineEffectPlanningResultStatus.UNSUPPORTED) {
            issues.add(BasicRenderPlanIssue.warning(
                    BasicRenderPlanIssueCode.EFFECT_PLAN_UNSUPPORTED,
                    "Effect plan has unsupported effects"));
        } else if (effectResult.status() == BaselineEffectPlanningResultStatus.FAILED
                || effectResult.status() == BaselineEffectPlanningResultStatus.VALIDATION_FAILED) {
            if (policy.failOnEffectWarnings()) {
                issues.add(BasicRenderPlanIssue.error(
                        BasicRenderPlanIssueCode.EFFECT_PLAN_FAILED,
                        "Effect plan validation failed"));
            } else {
                issues.add(BasicRenderPlanIssue.warning(
                        BasicRenderPlanIssueCode.EFFECT_PLAN_FAILED,
                        "Effect plan validation failed (non-blocking)"));
            }
        }

        return effectResult.plan();
    }

    private static BasicRenderStage buildEffectStage(
            BaselineEffectPlan effectPlan,
            List<BasicRenderPlanIssue> issues,
            AtomicInteger stageSeq, AtomicInteger stepSeq) {

        List<BasicRenderStep> steps = new ArrayList<>();

        if (effectPlan != null && effectPlan.operations() != null) {
            for (var op : effectPlan.operations()) {
                List<BasicRenderStepParameter> params = new ArrayList<>();
                params.add(new BasicRenderStepParameter(
                        "effectOperationId", BasicRenderStepParameterType.STRING,
                        op.id().value(), Map.of()));
                params.add(new BasicRenderStepParameter(
                        "operationType", BasicRenderStepParameterType.STRING,
                        op.type().name(), Map.of()));
                if (op.target() != null) {
                    params.add(new BasicRenderStepParameter(
                            "targetId", BasicRenderStepParameterType.STRING,
                            op.target().targetId(), Map.of()));
                }

                steps.add(new BasicRenderStep(
                        new BasicRenderStepId("step-" + stepSeq.incrementAndGet()),
                        BasicRenderStepType.APPLY_EFFECT_OPERATION,
                        op.target() != null ? new BasicRenderStepTarget(
                                BasicRenderStepTargetType.EFFECT_OPERATION,
                                op.id().value(), Map.of()) : null,
                        params,
                        BasicRenderStepSource.EFFECT_PLAN,
                        Map.of()));
            }
        }

        return new BasicRenderStage(
                new BasicRenderStageId("stage-" + stageSeq.incrementAndGet()),
                BasicRenderStageType.PLAN_EFFECTS,
                BasicRenderStageStatus.VALID,
                steps, Map.of());
    }

    private static BaselineTransitionPlan buildTransitionPlan(
            TimelineSpec timeline, BasicRenderPolicy policy,
            List<BasicRenderPlanIssue> issues) {

        BaselineTransitionPolicy transitionPolicy = new BaselineTransitionPolicy(
                policy.allowPocTransitions(), false, policy.allowWarnings(),
                true, true, true, true);
        var transitionRequest = new BaselineTransitionPlanningRequest(
                new BaselineTransitionPlanningRequestId("transition-" + timeline.id()),
                timeline, transitionPolicy, Map.of());
        var transitionResult = BaselineTransitionPlanner.plan(transitionRequest);

        if (transitionResult.status() == BaselineTransitionPlanningResultStatus.BLOCKED) {
            issues.add(BasicRenderPlanIssue.blocking(
                    BasicRenderPlanIssueCode.TRANSITION_PLAN_BLOCKED,
                    "Transition plan blocked"));
        } else if (transitionResult.status() == BaselineTransitionPlanningResultStatus.UNSUPPORTED) {
            issues.add(BasicRenderPlanIssue.warning(
                    BasicRenderPlanIssueCode.TRANSITION_PLAN_UNSUPPORTED,
                    "Transition plan has unsupported transitions"));
        } else if (transitionResult.status() == BaselineTransitionPlanningResultStatus.FAILED
                || transitionResult.status() == BaselineTransitionPlanningResultStatus.VALIDATION_FAILED) {
            if (policy.failOnTransitionWarnings()) {
                issues.add(BasicRenderPlanIssue.error(
                        BasicRenderPlanIssueCode.TRANSITION_PLAN_FAILED,
                        "Transition plan validation failed"));
            } else {
                issues.add(BasicRenderPlanIssue.warning(
                        BasicRenderPlanIssueCode.TRANSITION_PLAN_FAILED,
                        "Transition plan validation failed (non-blocking)"));
            }
        }

        return transitionResult.plan();
    }

    private static BasicRenderStage buildTransitionStage(
            BaselineTransitionPlan transitionPlan,
            List<BasicRenderPlanIssue> issues,
            AtomicInteger stageSeq, AtomicInteger stepSeq) {

        List<BasicRenderStep> steps = new ArrayList<>();

        if (transitionPlan != null && transitionPlan.operations() != null) {
            for (var op : transitionPlan.operations()) {
                List<BasicRenderStepParameter> params = new ArrayList<>();
                params.add(new BasicRenderStepParameter(
                        "transitionOperationId", BasicRenderStepParameterType.STRING,
                        op.id().value(), Map.of()));
                params.add(new BasicRenderStepParameter(
                        "operationType", BasicRenderStepParameterType.STRING,
                        op.type().name(), Map.of()));
                if (op.target() != null) {
                    params.add(new BasicRenderStepParameter(
                            "fromClipId", BasicRenderStepParameterType.STRING,
                            op.target().fromClipId(), Map.of()));
                    params.add(new BasicRenderStepParameter(
                            "toClipId", BasicRenderStepParameterType.STRING,
                            op.target().toClipId(), Map.of()));
                }

                steps.add(new BasicRenderStep(
                        new BasicRenderStepId("step-" + stepSeq.incrementAndGet()),
                        BasicRenderStepType.APPLY_TRANSITION_OPERATION,
                        op.target() != null ? new BasicRenderStepTarget(
                                BasicRenderStepTargetType.TRANSITION_OPERATION,
                                op.id().value(), Map.of()) : null,
                        params,
                        BasicRenderStepSource.TRANSITION_PLAN,
                        Map.of()));
            }
        }

        return new BasicRenderStage(
                new BasicRenderStageId("stage-" + stageSeq.incrementAndGet()),
                BasicRenderStageType.PLAN_TRANSITIONS,
                BasicRenderStageStatus.VALID,
                steps, Map.of());
    }

    private static BasicRenderStage buildCaptionOverlayStage(
            TimelineSpec timeline, BasicRenderPolicy policy,
            List<BasicRenderPlanIssue> issues,
            AtomicInteger stageSeq, AtomicInteger stepSeq) {

        List<BasicRenderStep> steps = new ArrayList<>();

        if (timeline.textOverlays() != null) {
            for (TimelineTextOverlay overlay : timeline.textOverlays()) {
                if (policy.requireCaptionOverlayValidation()) {
                    if (overlay.text() == null || overlay.text().isBlank()) {
                        issues.add(BasicRenderPlanIssue.error(
                                BasicRenderPlanIssueCode.CAPTION_OVERLAY_INVALID,
                                "Caption overlay text is blank: " + overlay.id()));
                    }
                    if (overlay.startTime() < 0) {
                        issues.add(BasicRenderPlanIssue.error(
                                BasicRenderPlanIssueCode.CAPTION_OVERLAY_INVALID,
                                "Caption overlay start time is negative: " + overlay.id()));
                    }
                    if (overlay.duration() <= 0) {
                        issues.add(BasicRenderPlanIssue.error(
                                BasicRenderPlanIssueCode.CAPTION_OVERLAY_INVALID,
                                "Caption overlay duration must be positive: " + overlay.id()));
                    }
                }

                List<BasicRenderStepParameter> params = new ArrayList<>();
                params.add(new BasicRenderStepParameter(
                        "captionId", BasicRenderStepParameterType.STRING,
                        overlay.id(), Map.of()));
                params.add(new BasicRenderStepParameter(
                        "startMs", BasicRenderStepParameterType.DECIMAL,
                        overlay.startTime() * 1000, Map.of()));
                params.add(new BasicRenderStepParameter(
                        "endMs", BasicRenderStepParameterType.DECIMAL,
                        (overlay.startTime() + overlay.duration()) * 1000, Map.of()));
                params.add(new BasicRenderStepParameter(
                        "textRef", BasicRenderStepParameterType.STRING,
                        overlay.text(), Map.of()));

                steps.add(new BasicRenderStep(
                        new BasicRenderStepId("step-" + stepSeq.incrementAndGet()),
                        BasicRenderStepType.APPLY_CAPTION_OVERLAY,
                        new BasicRenderStepTarget(
                                BasicRenderStepTargetType.CAPTION,
                                overlay.id(), Map.of()),
                        params,
                        BasicRenderStepSource.CAPTION_OVERLAY,
                        Map.of()));
            }
        }

        return new BasicRenderStage(
                new BasicRenderStageId("stage-" + stageSeq.incrementAndGet()),
                BasicRenderStageType.PLAN_CAPTION_OVERLAYS,
                BasicRenderStageStatus.VALID,
                steps, Map.of());
    }

    private static BasicRenderStage buildWatermarkOverlayStage(
            TimelineSpec timeline, BasicRenderPolicy policy,
            List<BasicRenderPlanIssue> issues,
            AtomicInteger stageSeq, AtomicInteger stepSeq) {

        List<BasicRenderStep> steps = new ArrayList<>();

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
                                issues.add(BasicRenderPlanIssue.error(
                                        BasicRenderPlanIssueCode.WATERMARK_OVERLAY_INVALID,
                                        "Watermark opacity must be 0..1: " + opacity));
                            }
                        } catch (NumberFormatException e) {
                            issues.add(BasicRenderPlanIssue.error(
                                    BasicRenderPlanIssueCode.WATERMARK_OVERLAY_INVALID,
                                    "Watermark opacity is not a valid number"));
                        }
                    }
                }

                List<BasicRenderStepParameter> params = new ArrayList<>();
                params.add(new BasicRenderStepParameter(
                        "watermarkId", BasicRenderStepParameterType.STRING,
                        "watermark-default", Map.of()));
                if (meta.containsKey("watermark.placement")) {
                    params.add(new BasicRenderStepParameter(
                            "placement", BasicRenderStepParameterType.STRING,
                            meta.get("watermark.placement"), Map.of()));
                }
                if (meta.containsKey("watermark.opacity")) {
                    params.add(new BasicRenderStepParameter(
                            "opacity", BasicRenderStepParameterType.PERCENT,
                            meta.get("watermark.opacity"), Map.of()));
                }

                steps.add(new BasicRenderStep(
                        new BasicRenderStepId("step-" + stepSeq.incrementAndGet()),
                        BasicRenderStepType.APPLY_WATERMARK_OVERLAY,
                        new BasicRenderStepTarget(
                                BasicRenderStepTargetType.WATERMARK,
                                "watermark-default", Map.of()),
                        params,
                        BasicRenderStepSource.CAPTION_OVERLAY,
                        Map.of()));
            }
        }

        return new BasicRenderStage(
                new BasicRenderStageId("stage-" + stageSeq.incrementAndGet()),
                BasicRenderStageType.PLAN_WATERMARK_OVERLAYS,
                BasicRenderStageStatus.VALID,
                steps, Map.of());
    }

    private static BasicRenderStage buildFinalAssemblyStage(
            TimelineSpec timeline, AtomicInteger stageSeq, AtomicInteger stepSeq) {

        List<BasicRenderStep> steps = new ArrayList<>();

        if (timeline.tracks() != null) {
            for (TimelineTrack track : timeline.tracks()) {
                List<BasicRenderStepParameter> params = new ArrayList<>();
                params.add(new BasicRenderStepParameter(
                        "trackId", BasicRenderStepParameterType.STRING,
                        track.id(), Map.of()));
                if (track.clips() != null) {
                    List<String> clipIds = track.clips().stream()
                            .sorted(Comparator.comparingDouble(TimelineClip::timelineStart))
                            .map(TimelineClip::id)
                            .toList();
                    params.add(new BasicRenderStepParameter(
                            "clipIds", BasicRenderStepParameterType.STRING,
                            String.join(",", clipIds), Map.of()));
                }

                steps.add(new BasicRenderStep(
                        new BasicRenderStepId("step-" + stepSeq.incrementAndGet()),
                        BasicRenderStepType.ASSEMBLE_CLIP_SEQUENCE,
                        new BasicRenderStepTarget(
                                BasicRenderStepTargetType.TRACK,
                                track.id(), Map.of()),
                        params,
                        BasicRenderStepSource.FINAL_ASSEMBLY,
                        Map.of()));
            }
        }

        return new BasicRenderStage(
                new BasicRenderStageId("stage-" + stageSeq.incrementAndGet()),
                BasicRenderStageType.PLAN_FINAL_ASSEMBLY,
                BasicRenderStageStatus.VALID,
                steps, Map.of());
    }

    private static BasicRenderStage buildOutputEncodingStage(
            TimelineSpec timeline, List<BasicRenderPlanIssue> issues,
            AtomicInteger stageSeq, AtomicInteger stepSeq) {

        List<BasicRenderStep> steps = new ArrayList<>();

        if (timeline.outputSpec() != null) {
            String container = timeline.outputSpec().format();
            String videoCodec = timeline.outputSpec().videoCodec();
            String audioCodec = timeline.outputSpec().audioSpec() != null
                    ? timeline.outputSpec().audioSpec().codec() : null;

            if (container != null && !ALLOWED_CONTAINERS.contains(container.toLowerCase())) {
                issues.add(BasicRenderPlanIssue.blocking(
                        BasicRenderPlanIssueCode.UNSUPPORTED_OUTPUT_CONTAINER,
                        "Unsupported container: " + container));
            }
            if (videoCodec != null && !ALLOWED_VIDEO_CODECS.contains(videoCodec.toLowerCase())) {
                issues.add(BasicRenderPlanIssue.blocking(
                        BasicRenderPlanIssueCode.UNSUPPORTED_VIDEO_CODEC,
                        "Unsupported video codec: " + videoCodec));
            }
            if (audioCodec != null && !ALLOWED_AUDIO_CODECS.contains(audioCodec.toLowerCase())) {
                issues.add(BasicRenderPlanIssue.warning(
                        BasicRenderPlanIssueCode.UNSUPPORTED_AUDIO_CODEC,
                        "Unsupported audio codec: " + audioCodec));
            }

            List<BasicRenderStepParameter> params = new ArrayList<>();
            params.add(new BasicRenderStepParameter(
                    "container", BasicRenderStepParameterType.STRING,
                    container, Map.of()));
            params.add(new BasicRenderStepParameter(
                    "videoCodec", BasicRenderStepParameterType.STRING,
                    videoCodec, Map.of()));
            if (audioCodec != null) {
                params.add(new BasicRenderStepParameter(
                        "audioCodec", BasicRenderStepParameterType.STRING,
                        audioCodec, Map.of()));
            }

            steps.add(new BasicRenderStep(
                    new BasicRenderStepId("step-" + stepSeq.incrementAndGet()),
                    BasicRenderStepType.ENCODE_OUTPUT,
                    new BasicRenderStepTarget(
                            BasicRenderStepTargetType.FINAL_OUTPUT,
                            timeline.id() + "-output", Map.of()),
                    params,
                    BasicRenderStepSource.OUTPUT_PROFILE,
                    Map.of()));
        }

        return new BasicRenderStage(
                new BasicRenderStageId("stage-" + stageSeq.incrementAndGet()),
                BasicRenderStageType.PLAN_OUTPUT_ENCODING,
                BasicRenderStageStatus.VALID,
                steps, Map.of());
    }

    private static BasicRenderStage buildVerificationStage(
            TimelineSpec timeline, AtomicInteger stageSeq, AtomicInteger stepSeq) {

        List<BasicRenderStep> steps = new ArrayList<>();

        List<BasicRenderStepParameter> params = new ArrayList<>();
        if (timeline.outputSpec() != null) {
            params.add(new BasicRenderStepParameter(
                    "expectedContainer", BasicRenderStepParameterType.STRING,
                    timeline.outputSpec().format(), Map.of()));
            params.add(new BasicRenderStepParameter(
                    "expectedVideoCodec", BasicRenderStepParameterType.STRING,
                    timeline.outputSpec().videoCodec(), Map.of()));
        }

        steps.add(new BasicRenderStep(
                new BasicRenderStepId("step-" + stepSeq.incrementAndGet()),
                BasicRenderStepType.VERIFY_OUTPUT,
                new BasicRenderStepTarget(
                        BasicRenderStepTargetType.FINAL_OUTPUT,
                        timeline.id() + "-output", Map.of()),
                params,
                BasicRenderStepSource.VERIFICATION,
                Map.of()));

        return new BasicRenderStage(
                new BasicRenderStageId("stage-" + stageSeq.incrementAndGet()),
                BasicRenderStageType.PLAN_OUTPUT_VERIFICATION,
                BasicRenderStageStatus.VALID,
                steps, Map.of());
    }

    // --- Helpers ---

    private static int countStepsByType(List<BasicRenderStage> stages,
                                         BasicRenderStepType type) {
        return stages.stream()
                .flatMap(s -> s.steps().stream())
                .filter(s -> s.type() == type)
                .mapToInt(s -> 1)
                .sum();
    }

    private static BasicRenderPlanStatus determinePlanStatus(
            List<BasicRenderPlanIssue> issues, BasicRenderPolicy policy) {
        boolean hasBlocking = issues.stream().anyMatch(i ->
                i.severity() == BasicRenderPlanIssueSeverity.BLOCKING);
        boolean hasError = issues.stream().anyMatch(i ->
                i.severity() == BasicRenderPlanIssueSeverity.ERROR);
        boolean hasWarning = issues.stream().anyMatch(i ->
                i.severity() == BasicRenderPlanIssueSeverity.WARNING);

        if (hasBlocking) return BasicRenderPlanStatus.BLOCKED;
        if (hasError) {
            return policy.failOnUnsupportedOutputProfile()
                    ? BasicRenderPlanStatus.INVALID
                    : BasicRenderPlanStatus.VALID_WITH_WARNINGS;
        }
        if (hasWarning && !policy.allowWarnings()) {
            return BasicRenderPlanStatus.INVALID;
        }
        if (hasWarning) return BasicRenderPlanStatus.VALID_WITH_WARNINGS;
        return BasicRenderPlanStatus.READY;
    }
}
