package com.example.platform.timeline.diff.calculation;

import com.example.platform.timeline.canonical.TimedTextCanonicalSemantics;
import com.example.platform.timeline.canonicalmodel.EffectCanonicalSemantics;
import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import com.example.platform.timeline.diff.*;
import java.util.*;

/**
 * Canonical Timeline Diff Calculator — compares two platform-owned timeline snapshots.
 *
 * <p>Internal domain service. Provider-neutral, storage-neutral, side-effect free.
 * Does not call vedit, OTIO, StorageRuntime, ProductRuntime, or render pipeline.</p>
 */
public class CanonicalTimelineDiffCalculator {

    /**
     * Calculate diff between two canonical timeline snapshots.
     */
    public CanonicalTimelineDiffCalculationResult calculate(
            CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after) {

        if (before == null || after == null) {
            return CanonicalTimelineDiffCalculationResult.failure("Both snapshots must be present");
        }

        List<TimelineChangeOperation> operations = new ArrayList<>();
        int[] opSeq = {0};

        // Duration
        if (!before.duration().isEqualTo(after.duration())) {
            operations.add(change(opSeq, TimelineChangeType.TIMELINE_DURATION_CHANGED,
                    TimelineChangeScope.TIMELINE, "timeline.duration",
                    before.duration().toString(), after.duration().toString()));
        }

        // Tracks
        diffTracks(before, after, operations, opSeq);

        // EFFECT_TRANSITION_CANONICALIZATION_V1 (second correction): first-class
        // transitions and automations join the production merge diff path.
        diffTransitions(before, after, operations, opSeq);
        diffAutomations(before, after, operations, opSeq);

        // Captions
        diffCaptions(before, after, operations, opSeq);

        // Watermarks
        diffWatermarks(before, after, operations, opSeq);

        // Template applications
        diffTemplateApplications(before, after, operations, opSeq);

        // ROADMAP_19 (C58): authored TextElements (semantic diff; execution-only fields excluded)
        diffTextElements(before, after, operations, opSeq);

        // Workflow steps
        diffWorkflowSteps(before, after, operations, opSeq);

        // Output profile
        diffOutputProfile(before, after, operations, opSeq);

        // Metadata
        diffMetadata(before, after, operations, opSeq);

        // Sort by type priority then path
        operations.sort(Comparator.comparingInt((TimelineChangeOperation op) -> typePriority(op.type()))
                .thenComparing(op -> op.path().value()));

        TimelineRenderImpact impact = estimateImpact(operations);

        TimelineDiff diff = new TimelineDiff(
                new TimelineDiffId("diff-" + before.revisionId() + "-" + after.revisionId()),
                before.revisionId(), after.revisionId(),
                operations, List.of(), impact, Map.of());

        return CanonicalTimelineDiffCalculationResult.success(diff);
    }

    // --- Track diff ---

    private void diffTracks(CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after,
                             List<TimelineChangeOperation> ops, int[] seq) {
        Map<String, CanonicalTimelineTrackSnapshot> beforeTracks = toMap(before.tracks());
        Map<String, CanonicalTimelineTrackSnapshot> afterTracks = toMap(after.tracks());

        // Removed tracks
        for (String id : beforeTracks.keySet()) {
            if (!afterTracks.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.TRACK_REMOVED,
                        TimelineChangeScope.TRACK, "timeline.tracks." + id,
                        id, null));
            }
        }

        // Added tracks
        for (String id : afterTracks.keySet()) {
            if (!beforeTracks.containsKey(id)) {
                // C1-CNM1 field-preservation correction: TRACK_ADDED carries
                // the added track's full clip reconstruction data (identity,
                // exact timing/rate, opaque effects) so the patch applier
                // materializes the track WITH its clips — a source-added track
                // must never degrade to an empty shell.
                CanonicalTimelineTrackSnapshot at = afterTracks.get(id);
                ops.add(trackAddedOp(seq, id, at));
            }
        }

        // Modified tracks
        for (String id : beforeTracks.keySet()) {
            if (afterTracks.containsKey(id)) {
                CanonicalTimelineTrackSnapshot bt = beforeTracks.get(id);
                CanonicalTimelineTrackSnapshot at = afterTracks.get(id);

                // Track reordered
                if (bt.order() != at.order()) {
                    ops.add(change(seq, TimelineChangeType.TRACK_REORDERED,
                            TimelineChangeScope.TRACK, "timeline.tracks." + id + ".order",
                            String.valueOf(bt.order()), String.valueOf(at.order())));
                }

                // Clip diffs within track
                diffClips(bt, at, ops, seq);
            }
        }
    }

    // --- Clip diff ---

    private void diffClips(CanonicalTimelineTrackSnapshot beforeTrack,
                            CanonicalTimelineTrackSnapshot afterTrack,
                            List<TimelineChangeOperation> ops, int[] seq) {
        Map<String, CanonicalTimelineClipSnapshot> beforeClips = toClipMap(beforeTrack.clips());
        Map<String, CanonicalTimelineClipSnapshot> afterClips = toClipMap(afterTrack.clips());
        String trackPath = "timeline.tracks." + beforeTrack.trackId() + ".clips";

        // Removed
        for (String id : beforeClips.keySet()) {
            if (!afterClips.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.CLIP_REMOVED,
                        TimelineChangeScope.CLIP, trackPath + "." + id, id, null));
            }
        }

        // Added
        for (String id : afterClips.keySet()) {
            if (!beforeClips.containsKey(id)) {
                // C1-CNM1 SOURCE_BINDING/field-preservation correction: the
                // added clip's full reconstruction data (asset binding, exact
                // times, exact rate, opaque effects) rides in safeMetadata so
                // the patch applier materializes the clip EXACTLY — never a
                // blank-asset/timing stub (identity authority preserved).
                CanonicalTimelineClipSnapshot ac = afterClips.get(id);
                ops.add(clipAddedOp(seq, trackPath + "." + id, ac));
            }
        }

        // Modified
        for (String id : beforeClips.keySet()) {
            if (afterClips.containsKey(id)) {
                CanonicalTimelineClipSnapshot bc = beforeClips.get(id);
                CanonicalTimelineClipSnapshot ac = afterClips.get(id);
                String clipPath = trackPath + "." + id;

                if (!bc.start().isEqualTo(ac.start())) {
                    ops.add(change(seq, TimelineChangeType.CLIP_MOVED,
                            TimelineChangeScope.CLIP, clipPath + ".start",
                            bc.start().toString(), ac.start().toString()));
                }
                if (!bc.duration().isEqualTo(ac.duration())
                        || !bc.sourceStart().isEqualTo(ac.sourceStart())
                        || !bc.sourceDuration().isEqualTo(ac.sourceDuration())) {
                    ops.add(change(seq, TimelineChangeType.CLIP_TRIMMED,
                            TimelineChangeScope.CLIP, clipPath + ".duration",
                            bc.duration().toString(), ac.duration().toString()));
                }
                if (!bc.rate().equals(ac.rate())) {
                    // C1-CNM1: rate is a first-class merge-sensitive clip field.
                    ops.add(change(seq, TimelineChangeType.CLIP_SPEED_CHANGED,
                            TimelineChangeScope.CLIP, clipPath + ".rate",
                            bc.rate().toString(), ac.rate().toString()));
                }
                if (!Objects.equals(bc.assetBindingId(), ac.assetBindingId())) {
                    ops.add(change(seq, TimelineChangeType.ASSET_BINDING_CHANGED,
                            TimelineChangeScope.ASSET_BINDING, clipPath + ".assetBindingId",
                            bc.assetBindingId(), ac.assetBindingId()));
                }
                // EFFECT_TRANSITION_CANONICALIZATION_V1 (FIFTH CORRECTION):
                // effects are authored Timeline semantics owned by
                // TimelineClipEffect.semanticFingerprint() and reconstructed
                // through EffectCanonicalSemantics — the diff consumes that
                // single local semantic authority (no central field encoding,
                // no List.toString()/Map.toString() signatures, no custom
                // delimiter grammar). Coarse EFFECT_CHANGED op is
                // deterministic, patchable, merge-visible, conflict-visible.
                if (!effectFingerprint(bc.effects()).equals(effectFingerprint(ac.effects()))) {
                    Map<String, String> meta = new LinkedHashMap<>();
                    // after-state rides as the canonical Effect encoding
                    // (EffectCanonicalSemantics.encodeEffects — lossless/typed).
                    meta.put("effects", EffectCanonicalSemantics.encodeEffects(ac.effects()));
                    ops.add(new TimelineChangeOperation(
                            new TimelineChangeOperationId("op-" + (++seq[0])),
                            TimelineChangeType.EFFECT_CHANGED,
                            TimelineChangeScope.CLIP,
                            new TimelineChangePath(clipPath + ".effects"),
                            TimelineChangePayload.ofString(effectFingerprint(bc.effects())),
                            TimelineChangePayload.ofString(effectFingerprint(ac.effects())),
                            meta));
                }
            }
        }
    }

    // --- Transition / Automation diff (EFFECT_TRANSITION_CANONICALIZATION_V1) ---

    /**
     * Effect local semantic fingerprint consumed by production diff: delegates
     * to TimelineClipEffect.semanticFingerprint() (single local authority).
     */
    private String effectFingerprint(List<TimelineClipEffect> effects) {
        if (effects == null || effects.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (TimelineClipEffect e : effects) {
            if (sb.length() > 0) sb.append('\u001f');
            sb.append(e.semanticFingerprint());
        }
        return sb.toString();
    }

    private void diffTransitions(CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after,
                                 List<TimelineChangeOperation> ops, int[] seq) {
        Map<String, CanonicalTimelineTransitionSnapshot> beforeTx = new java.util.LinkedHashMap<>();
        for (var t : before.transitions()) beforeTx.put(t.transitionId(), t);
        Map<String, CanonicalTimelineTransitionSnapshot> afterTx = new java.util.LinkedHashMap<>();
        for (var t : after.transitions()) afterTx.put(t.transitionId(), t);

        for (String id : beforeTx.keySet()) {
            CanonicalTimelineTransitionSnapshot bt = beforeTx.get(id);
            if (!afterTx.containsKey(id)) {
                // THIRD CORRECTION: deletion is first-class semantic behavior.
                ops.add(transitionDeletedOp(seq, id, bt));
            } else {
                CanonicalTimelineTransitionSnapshot at = afterTx.get(id);
                if (!bt.localSemanticsEquals(at)) {
                    ops.add(transitionOp(seq, id, at));
                }
            }
        }
        for (String id : afterTx.keySet()) {
            if (!beforeTx.containsKey(id)) {
                CanonicalTimelineTransitionSnapshot at = afterTx.get(id);
                ops.add(transitionOp(seq, id, at));
            }
        }
    }

    /** TRANSITION delete op (THIRD CORRECTION): explicit, non-ambiguous. */
    private TimelineChangeOperation transitionDeletedOp(int[] seq, String id,
            CanonicalTimelineTransitionSnapshot t) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("deleted", "true");
        meta.put("transitionDefinitionId", t.transitionDefinitionId());
        return new TimelineChangeOperation(
                new TimelineChangeOperationId("op-" + (++seq[0])),
                TimelineChangeType.TRANSITION_CHANGED, TimelineChangeScope.TRANSITION,
                new TimelineChangePath("timeline.transitions." + id),
                TimelineChangePayload.ofString(t.semanticFingerprint()),
                TimelineChangePayload.empty(),
                meta);
    }

    /** TRANSITION_CHANGED op with after-state reconstruction data in safeMetadata. */
    private TimelineChangeOperation transitionOp(int[] seq, String id,
            CanonicalTimelineTransitionSnapshot t) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("transitionDefinitionId", t.transitionDefinitionId());
        meta.put("transitionDefinitionVersion", t.transitionDefinitionVersion());
        meta.put("outgoingClipId", t.outgoingClipId());
        meta.put("incomingClipId", t.incomingClipId());
        meta.put("mediaType", t.mediaType());
        meta.put("durationTicks", String.valueOf(t.duration().ticks()));
        meta.put("durationTimeScale", String.valueOf(t.duration().timeScale()));
        meta.put("alignment", t.alignment());
        meta.put("temporalPolicy", t.temporalPolicy());
        if (t.parameters() != null && !t.parameters().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            new java.util.TreeMap<>(t.parameters()).forEach((k, v) -> {
                if (sb.length() > 0) sb.append(',');
                sb.append(k).append('=').append(v);
            });
            meta.put("parameters", sb.toString());
        }
        return new TimelineChangeOperation(
                new TimelineChangeOperationId("op-" + (++seq[0])),
                TimelineChangeType.TRANSITION_CHANGED, TimelineChangeScope.TRANSITION,
                new TimelineChangePath("timeline.transitions." + id),
                TimelineChangePayload.ofString(t.semanticFingerprint()),
                // THIRD CORRECTION: afterValue = COMPLETE semantic fingerprint
                // (definition/version/participants/mediaType/duration/alignment/
                // policy/parameters) so divergent two-sided edits in ANY
                // merge-relevant field produce explicit conflict.
                TimelineChangePayload.ofString(t.semanticFingerprint()),
                meta);
    }

    private void diffAutomations(CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after,
                                 List<TimelineChangeOperation> ops, int[] seq) {
        Map<String, CanonicalTimelineAutomationSnapshot> beforeAuto = new java.util.LinkedHashMap<>();
        for (var c : before.automations()) beforeAuto.put(c.automationId(), c);
        Map<String, CanonicalTimelineAutomationSnapshot> afterAuto = new java.util.LinkedHashMap<>();
        for (var c : after.automations()) afterAuto.put(c.automationId(), c);

        for (String id : beforeAuto.keySet()) {
            CanonicalTimelineAutomationSnapshot bc = beforeAuto.get(id);
            if (!afterAuto.containsKey(id)) {
                // THIRD CORRECTION: deletion is first-class semantic behavior.
                Map<String, String> meta = new LinkedHashMap<>();
                meta.put("deleted", "true");
                meta.put("targetEntityId", bc.targetEntityId());
                ops.add(new TimelineChangeOperation(
                        new TimelineChangeOperationId("op-" + (++seq[0])),
                        TimelineChangeType.AUTOMATION_CHANGED, TimelineChangeScope.AUTOMATION,
                        new TimelineChangePath("timeline.automations." + id),
                        TimelineChangePayload.ofString(bc.semanticFingerprint()),
                        TimelineChangePayload.empty(),
                        meta));
            } else {
                CanonicalTimelineAutomationSnapshot ac = afterAuto.get(id);
                if (!bc.localSemanticsEquals(ac)) {
                    ops.add(automationOp(seq, id, ac));
                }
            }
        }
        for (String id : afterAuto.keySet()) {
            if (!beforeAuto.containsKey(id)) {
                CanonicalTimelineAutomationSnapshot ac = afterAuto.get(id);
                ops.add(automationOp(seq, id, ac));
            }
        }
    }

    /** AUTOMATION_CHANGED op with after-state reconstruction data in safeMetadata. */
    private TimelineChangeOperation automationOp(int[] seq, String id,
            CanonicalTimelineAutomationSnapshot c) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("targetEntityId", c.targetEntityId());
        meta.put("parameterPath", c.parameterPath());
        meta.put("valueType", c.valueType());
        meta.put("extrapolation", c.extrapolation());
        StringBuilder kf = new StringBuilder();
        for (var k : c.keyframes()) {
            if (kf.length() > 0) kf.append('\u001f');
            kf.append(k.keyframeId()).append('\u001e')
                    .append(k.time().ticks()).append('\u001e')
                    .append(k.time().timeScale()).append('\u001e')
                    .append(k.value()).append('\u001e')
                    .append(k.interpolation());
        }
        meta.put("keyframes", kf.toString());
        return new TimelineChangeOperation(
                new TimelineChangeOperationId("op-" + (++seq[0])),
                TimelineChangeType.AUTOMATION_CHANGED, TimelineChangeScope.AUTOMATION,
                new TimelineChangePath("timeline.automations." + id),
                TimelineChangePayload.ofString(c.semanticFingerprint()),
                // THIRD CORRECTION: afterValue = COMPLETE semantic fingerprint
                // (target/path/valueType/extrapolation/keyframes) so divergent
                // two-sided edits in ANY merge-relevant field conflict.
                TimelineChangePayload.ofString(c.semanticFingerprint()),
                meta);
    }

    // --- Caption diff ---

    private void diffCaptions(CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after,
                               List<TimelineChangeOperation> ops, int[] seq) {
        Map<String, CanonicalTimelineCaptionSnapshot> beforeCaptions = toCaptionMap(before.captions());
        Map<String, CanonicalTimelineCaptionSnapshot> afterCaptions = toCaptionMap(after.captions());

        for (String id : beforeCaptions.keySet()) {
            CanonicalTimelineCaptionSnapshot bc = beforeCaptions.get(id);
            if (!afterCaptions.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.CAPTION_SEGMENT_CHANGED,
                        TimelineChangeScope.CAPTION, "timeline.captions." + id,
                        bc.text(), null));
            } else {
                CanonicalTimelineCaptionSnapshot ac = afterCaptions.get(id);
                if (!Objects.equals(bc.text(), ac.text())
                        || !bc.start().isEqualTo(ac.start())
                        || !bc.end().isEqualTo(ac.end())) {
                    ops.add(change(seq, TimelineChangeType.CAPTION_SEGMENT_CHANGED,
                            TimelineChangeScope.CAPTION, "timeline.captions." + id + ".text",
                            bc.text(), ac.text()));
                }
                if (!Objects.equals(bc.style(), ac.style())) {
                    ops.add(change(seq, TimelineChangeType.TEXT_STYLE_CHANGED,
                            TimelineChangeScope.TEXT_OVERLAY, "timeline.captions." + id + ".style",
                            String.valueOf(bc.style()), String.valueOf(ac.style())));
                }
            }
        }
        for (String id : afterCaptions.keySet()) {
            if (!beforeCaptions.containsKey(id)) {
                CanonicalTimelineCaptionSnapshot ac = afterCaptions.get(id);
                ops.add(change(seq, TimelineChangeType.CAPTION_SEGMENT_CHANGED,
                        TimelineChangeScope.CAPTION, "timeline.captions." + id,
                        null, ac.text()));
            }
        }
    }

    // --- Watermark diff ---

    private void diffWatermarks(CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after,
                                 List<TimelineChangeOperation> ops, int[] seq) {
        Map<String, CanonicalTimelineWatermarkSnapshot> beforeWm = toWatermarkMap(before.watermarks());
        Map<String, CanonicalTimelineWatermarkSnapshot> afterWm = toWatermarkMap(after.watermarks());

        for (String id : beforeWm.keySet()) {
            CanonicalTimelineWatermarkSnapshot bw = beforeWm.get(id);
            if (!afterWm.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.WATERMARK_CHANGED,
                        TimelineChangeScope.WATERMARK, "timeline.watermarks." + id,
                        bw.watermarkId(), null));
            } else {
                CanonicalTimelineWatermarkSnapshot aw = afterWm.get(id);
                if (!Objects.equals(bw.assetBindingId(), aw.assetBindingId())
                        || !Objects.equals(bw.position(), aw.position())
                        || bw.opacityPercent() != aw.opacityPercent()) {
                    ops.add(change(seq, TimelineChangeType.WATERMARK_CHANGED,
                            TimelineChangeScope.WATERMARK, "timeline.watermarks." + id,
                            bw.position() + ":" + bw.opacityPercent(),
                            aw.position() + ":" + aw.opacityPercent()));
                }
            }
        }
        for (String id : afterWm.keySet()) {
            if (!beforeWm.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.WATERMARK_CHANGED,
                        TimelineChangeScope.WATERMARK, "timeline.watermarks." + id,
                        null, afterWm.get(id).watermarkId()));
            }
        }
    }

    // --- ROADMAP_19 TextElement diff ---

    private void diffTextElements(CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after,
                                  List<TimelineChangeOperation> ops, int[] seq) {
        Map<String, com.example.platform.timeline.canonical.TextElement> beforeMap =
                toTextElementMap(before.textElements());
        Map<String, com.example.platform.timeline.canonical.TextElement> afterMap =
                toTextElementMap(after.textElements());

        for (String id : beforeMap.keySet()) {
            var b = beforeMap.get(id);
            if (!afterMap.containsKey(id)) {
                // ROADMAP #19: deletion is first-class semantic behavior —
                // explicit deleted op, never ambiguous with an empty change.
                Map<String, String> delMeta = new java.util.LinkedHashMap<>();
                delMeta.put("deleted", "true");
                ops.add(new TimelineChangeOperation(
                        new TimelineChangeOperationId("op-" + (++seq[0])),
                        TimelineChangeType.TEXT_ELEMENT_CHANGED, TimelineChangeScope.TEXT_ELEMENT,
                        new TimelineChangePath("timeline.textElements." + id),
                        TimelineChangePayload.ofString(b.id().value()),
                        TimelineChangePayload.empty(),
                        delMeta));
            } else {
                var a = afterMap.get(id);
                if (!b.equals(a)) {
                    ops.add(change(seq, TimelineChangeType.TEXT_ELEMENT_CHANGED,
                            TimelineChangeScope.TEXT_ELEMENT, "timeline.textElements." + id,
                            TimedTextCanonicalSemantics.semanticFingerprint(b),
                            TimedTextCanonicalSemantics.semanticFingerprint(a)));
                }
            }
        }
        for (String id : afterMap.keySet()) {
            if (!beforeMap.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.TEXT_ELEMENT_CHANGED,
                        TimelineChangeScope.TEXT_ELEMENT, "timeline.textElements." + id,
                        null, afterMap.get(id).id().value()));
            }
        }
    }

    private static Map<String, com.example.platform.timeline.canonical.TextElement> toTextElementMap(
            List<com.example.platform.timeline.canonical.TextElement> elements) {
        java.util.LinkedHashMap<String, com.example.platform.timeline.canonical.TextElement> m =
                new java.util.LinkedHashMap<>();
        for (var e : elements) {
            m.put(e.id().value(), e);
        }
        return m;
    }

    // --- Template application diff ---

    private void diffTemplateApplications(CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after,
                                           List<TimelineChangeOperation> ops, int[] seq) {
        Map<String, CanonicalTimelineTemplateApplicationSnapshot> beforeTa = toTemplateMap(before.templateApplications());
        Map<String, CanonicalTimelineTemplateApplicationSnapshot> afterTa = toTemplateMap(after.templateApplications());

        for (String id : beforeTa.keySet()) {
            CanonicalTimelineTemplateApplicationSnapshot bt = beforeTa.get(id);
            if (!afterTa.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.TEMPLATE_PARAMETER_CHANGED,
                        TimelineChangeScope.TEMPLATE_APPLICATION,
                        "timeline.templateApplications." + id, bt.templateId(), null));
            } else {
                CanonicalTimelineTemplateApplicationSnapshot at = afterTa.get(id);
                if (!Objects.equals(bt.templateId(), at.templateId())
                        || !Objects.equals(bt.templateVersion(), at.templateVersion())) {
                    ops.add(change(seq, TimelineChangeType.TEMPLATE_PROFILE_CHANGED,
                            TimelineChangeScope.TEMPLATE_APPLICATION,
                            "timeline.templateApplications." + id + ".templateId",
                            bt.templateId(), at.templateId()));
                }
                if (!Objects.equals(bt.parameters(), at.parameters())) {
                    ops.add(change(seq, TimelineChangeType.TEMPLATE_PARAMETER_CHANGED,
                            TimelineChangeScope.TEMPLATE_APPLICATION,
                            "timeline.templateApplications." + id + ".parameters",
                            String.valueOf(bt.parameters()), String.valueOf(at.parameters())));
                }
            }
        }
        for (String id : afterTa.keySet()) {
            if (!beforeTa.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.TEMPLATE_PARAMETER_CHANGED,
                        TimelineChangeScope.TEMPLATE_APPLICATION,
                        "timeline.templateApplications." + id, null, afterTa.get(id).templateId()));
            }
        }
    }

    // --- Workflow step diff ---

    private void diffWorkflowSteps(CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after,
                                    List<TimelineChangeOperation> ops, int[] seq) {
        Map<String, CanonicalTimelineWorkflowStepSnapshot> beforeWs = toWorkflowMap(before.workflowSteps());
        Map<String, CanonicalTimelineWorkflowStepSnapshot> afterWs = toWorkflowMap(after.workflowSteps());

        for (String id : beforeWs.keySet()) {
            CanonicalTimelineWorkflowStepSnapshot bw = beforeWs.get(id);
            if (!afterWs.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.WORKFLOW_APPLY_TEMPLATE_STEP_CHANGED,
                        TimelineChangeScope.WORKFLOW_STEP,
                        "timeline.workflowSteps." + id, bw.stepType(), null));
            } else {
                CanonicalTimelineWorkflowStepSnapshot aw = afterWs.get(id);
                if (!Objects.equals(bw.stepType(), aw.stepType())
                        || !Objects.equals(bw.templateApplicationId(), aw.templateApplicationId())) {
                    ops.add(change(seq, TimelineChangeType.WORKFLOW_APPLY_TEMPLATE_STEP_CHANGED,
                            TimelineChangeScope.WORKFLOW_STEP,
                            "timeline.workflowSteps." + id,
                            bw.templateApplicationId(), aw.templateApplicationId()));
                }
            }
        }
        for (String id : afterWs.keySet()) {
            if (!beforeWs.containsKey(id)) {
                ops.add(change(seq, TimelineChangeType.WORKFLOW_APPLY_TEMPLATE_STEP_CHANGED,
                        TimelineChangeScope.WORKFLOW_STEP,
                        "timeline.workflowSteps." + id, null, afterWs.get(id).stepType()));
            }
        }
    }

    // --- Output profile diff ---

    private void diffOutputProfile(CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after,
                                    List<TimelineChangeOperation> ops, int[] seq) {
        CanonicalTimelineOutputProfileSnapshot bp = before.outputProfile();
        CanonicalTimelineOutputProfileSnapshot ap = after.outputProfile();
        if (bp == null && ap == null) return;
        if (bp == null || ap == null) {
            ops.add(change(seq, TimelineChangeType.OUTPUT_PROFILE_CHANGED,
                    TimelineChangeScope.OUTPUT_PROFILE, "timeline.outputProfile",
                    String.valueOf(bp), String.valueOf(ap)));
            return;
        }
        if (bp.width() != ap.width() || bp.height() != ap.height()
                || !Objects.equals(bp.format(), ap.format())
                || !Objects.equals(bp.aspectRatio(), ap.aspectRatio())) {
            ops.add(change(seq, TimelineChangeType.OUTPUT_PROFILE_CHANGED,
                    TimelineChangeScope.OUTPUT_PROFILE, "timeline.outputProfile",
                    bp.width() + "x" + bp.height(), ap.width() + "x" + ap.height()));
        }
    }

    // --- Metadata diff ---

    private void diffMetadata(CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after,
                               List<TimelineChangeOperation> ops, int[] seq) {
        Map<String, String> bm = before.safeMetadata() != null ? before.safeMetadata() : Map.of();
        Map<String, String> am = after.safeMetadata() != null ? after.safeMetadata() : Map.of();
        if (!bm.equals(am)) {
            ops.add(change(seq, TimelineChangeType.METADATA_CHANGED,
                    TimelineChangeScope.METADATA, "timeline.metadata",
                    String.valueOf(bm), String.valueOf(am)));
        }
    }

    // --- Impact estimation ---

    private TimelineRenderImpact estimateImpact(List<TimelineChangeOperation> ops) {
        if (ops.isEmpty()) return new TimelineRenderImpact(
                TimelineRenderImpactLevel.NONE, List.of(), List.of(), Map.of());

        boolean hasFullRerender = ops.stream().anyMatch(op ->
                op.type() == TimelineChangeType.TIMELINE_DURATION_CHANGED
                || op.type() == TimelineChangeType.OUTPUT_PROFILE_CHANGED
                || op.type() == TimelineChangeType.TRACK_ADDED
                || op.type() == TimelineChangeType.TRACK_REMOVED);

        boolean hasPartialRerender = ops.stream().anyMatch(op ->
                op.type() == TimelineChangeType.CLIP_ADDED
                || op.type() == TimelineChangeType.CLIP_REMOVED
                || op.type() == TimelineChangeType.CLIP_MOVED
                || op.type() == TimelineChangeType.CLIP_TRIMMED
                || op.type() == TimelineChangeType.CAPTION_SEGMENT_CHANGED
                || op.type() == TimelineChangeType.TEXT_STYLE_CHANGED
                || op.type() == TimelineChangeType.WATERMARK_CHANGED
                || op.type() == TimelineChangeType.TEMPLATE_PARAMETER_CHANGED
                || op.type() == TimelineChangeType.TEMPLATE_PROFILE_CHANGED);

        boolean metadataOnly = ops.stream().allMatch(op ->
                op.type() == TimelineChangeType.METADATA_CHANGED);

        TimelineRenderImpactLevel level;
        if (metadataOnly) level = TimelineRenderImpactLevel.METADATA_ONLY;
        else if (hasFullRerender) level = TimelineRenderImpactLevel.FULL_RERENDER;
        else if (hasPartialRerender) level = TimelineRenderImpactLevel.PARTIAL_RERENDER;
        else level = TimelineRenderImpactLevel.NONE;

        List<String> affectedPaths = ops.stream()
                .map(op -> op.path().value()).distinct().toList();

        return new TimelineRenderImpact(level, affectedPaths, List.of(), Map.of());
    }

    // --- Priority ordering ---

    private int typePriority(TimelineChangeType type) {
        return switch (type) {
            case TIMELINE_DURATION_CHANGED -> 1;
            case TRACK_REMOVED -> 2;
            case TRACK_ADDED -> 3;
            case TRACK_REORDERED -> 4;
            case CLIP_REMOVED -> 5;
            case CLIP_ADDED -> 6;
            case CLIP_MOVED -> 7;
            case CLIP_TRIMMED -> 8;
            case ASSET_BINDING_CHANGED -> 9;
            case CAPTION_SEGMENT_CHANGED -> 10;
            case TEXT_STYLE_CHANGED -> 11;
            case WATERMARK_CHANGED -> 12;
            case TEMPLATE_PROFILE_CHANGED -> 13;
            case TEMPLATE_PARAMETER_CHANGED -> 14;
            case WORKFLOW_APPLY_TEMPLATE_STEP_CHANGED -> 15;
            case OUTPUT_PROFILE_CHANGED -> 16;
            case METADATA_CHANGED -> 17;
            default -> 99;
        };
    }

    // --- Helpers ---

    private TimelineChangeOperation change(int[] seq, TimelineChangeType type,
                                             TimelineChangeScope scope, String path,
                                             String before, String after) {
        return new TimelineChangeOperation(
                new TimelineChangeOperationId("op-" + (++seq[0])),
                type, scope, new TimelineChangePath(path),
                before != null ? TimelineChangePayload.ofString(before) : TimelineChangePayload.empty(),
                after != null ? TimelineChangePayload.ofString(after) : TimelineChangePayload.empty(),
                Map.of());
    }

    /**
     * C1-CNM1: TRACK_ADDED op carrying the added track's full reconstruction
     * data — the track kind/order and every clip (identity, exact timing/rate,
     * opaque effects) — so the patch applier materializes the track WITH its
     * clips (a source-added track never degrades to an empty shell).
     */
    private TimelineChangeOperation trackAddedOp(int[] seq, String trackId, CanonicalTimelineTrackSnapshot track) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("trackKind", track.kind() != null ? track.kind() : "VIDEO");
        if (track.clips() != null && !track.clips().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (CanonicalTimelineClipSnapshot clip : track.clips()) {
                if (sb.length() > 0) sb.append('\u001f');
                sb.append(clip.clipId()).append('\u001e')
                        .append(clip.assetBindingId()).append('\u001e')
                        .append(clip.start().toString()).append('\u001e')
                        .append(clip.duration().toString()).append('\u001e')
                        .append(clip.sourceStart().toString()).append('\u001e')
                        .append(clip.sourceDuration().toString()).append('\u001e')
                        .append(clip.rate().numerator().toString()).append('\u001e')
                        .append(clip.rate().denominator()).append('\u001e');
                if (clip.effects() != null && !clip.effects().isEmpty()) {
                    // FIFTH CORRECTION: canonical Effect encoding (lossless/typed).
                    sb.append("effects=").append(EffectCanonicalSemantics.encodeEffects(clip.effects())).append('\u001e');
                }
            }
            meta.put("clips", sb.toString());
        }
        return new TimelineChangeOperation(
                new TimelineChangeOperationId("op-" + (++seq[0])),
                TimelineChangeType.TRACK_ADDED, TimelineChangeScope.TRACK,
                new TimelineChangePath("timeline.tracks." + trackId),
                TimelineChangePayload.empty(),
                TimelineChangePayload.ofString(trackId),
                meta);
    }

    /**
     * C1-CNM1: CLIP_ADDED op carrying the added clip's full reconstruction
     * data (identity + exact timing/rate + opaque effects) in safeMetadata.
     * The patch applier materializes the clip EXACTLY from this data, so a
     * source-added clip never degrades to a blank-asset/timing stub.
     */
    private TimelineChangeOperation clipAddedOp(int[] seq, String path, CanonicalTimelineClipSnapshot clip) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("assetBindingId", clip.assetBindingId());
        meta.put("start", clip.start().toString());
        meta.put("duration", clip.duration().toString());
        meta.put("sourceStart", clip.sourceStart().toString());
        meta.put("sourceDuration", clip.sourceDuration().toString());
        meta.put("rateNum", clip.rate().numerator().toString());
        meta.put("rateDen", String.valueOf(clip.rate().denominator()));
        if (clip.effects() != null && !clip.effects().isEmpty()) {
            // FIFTH CORRECTION: canonical Effect encoding (lossless/typed) —
            // single local semantic codec authority.
            meta.put("effects", EffectCanonicalSemantics.encodeEffects(clip.effects()));
        }
        return new TimelineChangeOperation(
                new TimelineChangeOperationId("op-" + (++seq[0])),
                TimelineChangeType.CLIP_ADDED, TimelineChangeScope.CLIP,
                new TimelineChangePath(path),
                TimelineChangePayload.empty(),
                TimelineChangePayload.ofString(clip.clipId()),
                meta);
    }

    private Map<String, CanonicalTimelineTrackSnapshot> toMap(List<CanonicalTimelineTrackSnapshot> tracks) {
        Map<String, CanonicalTimelineTrackSnapshot> map = new LinkedHashMap<>();
        if (tracks != null) tracks.forEach(t -> map.put(t.trackId(), t));
        return map;
    }

    private Map<String, CanonicalTimelineClipSnapshot> toClipMap(List<CanonicalTimelineClipSnapshot> clips) {
        Map<String, CanonicalTimelineClipSnapshot> map = new LinkedHashMap<>();
        if (clips != null) clips.forEach(c -> map.put(c.clipId(), c));
        return map;
    }

    private Map<String, CanonicalTimelineCaptionSnapshot> toCaptionMap(List<CanonicalTimelineCaptionSnapshot> captions) {
        Map<String, CanonicalTimelineCaptionSnapshot> map = new LinkedHashMap<>();
        if (captions != null) captions.forEach(c -> map.put(c.captionId(), c));
        return map;
    }

    private Map<String, CanonicalTimelineWatermarkSnapshot> toWatermarkMap(List<CanonicalTimelineWatermarkSnapshot> watermarks) {
        Map<String, CanonicalTimelineWatermarkSnapshot> map = new LinkedHashMap<>();
        if (watermarks != null) watermarks.forEach(w -> map.put(w.watermarkId(), w));
        return map;
    }

    private Map<String, CanonicalTimelineTemplateApplicationSnapshot> toTemplateMap(List<CanonicalTimelineTemplateApplicationSnapshot> apps) {
        Map<String, CanonicalTimelineTemplateApplicationSnapshot> map = new LinkedHashMap<>();
        if (apps != null) apps.forEach(a -> map.put(a.templateApplicationId(), a));
        return map;
    }

    private Map<String, CanonicalTimelineWorkflowStepSnapshot> toWorkflowMap(List<CanonicalTimelineWorkflowStepSnapshot> steps) {
        Map<String, CanonicalTimelineWorkflowStepSnapshot> map = new LinkedHashMap<>();
        if (steps != null) steps.forEach(s -> map.put(s.workflowStepId(), s));
        return map;
    }
}
