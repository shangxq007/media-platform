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

        // CHECKPOINT_A correction: AudioMix and SemanticRelationships join the
        // production semantic diff path — never silent-dropped by merge.
        diffAudioMix(before, after, operations, opSeq);
        diffRelationships(before, after, operations, opSeq);

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
                // CHECKPOINT_A: the FULL typed source semantics must be
                // diff-visible. R4-B: comparison uses the TYPED source binding
                // authority — the flattened strings are projections, never a
                // parallel semantic representation.
                if (!com.example.platform.timeline.semantics.clip
                        .TimelineSourceBindingCanonicalSemantics
                        .localSemanticsEquals(bc.sourceBinding(), ac.sourceBinding())) {
                    Map<String, String> srcMeta = new LinkedHashMap<>();
                    srcMeta.put("sourceBinding",
                            com.example.platform.timeline.semantics.clip
                                    .TimelineSourceBindingCanonicalSemantics
                                    .encode(ac.sourceBinding()));
                    ops.add(new TimelineChangeOperation(
                            new TimelineChangeOperationId("op-" + (++seq[0])),
                            TimelineChangeType.ASSET_BINDING_CHANGED,
                            TimelineChangeScope.ASSET_BINDING,
                            new TimelineChangePath(clipPath + ".sourceSemantics"),
                            TimelineChangePayload.ofString(
                                    com.example.platform.timeline.semantics.clip
                                            .TimelineSourceBindingCanonicalSemantics
                                            .semanticFingerprint(bc.sourceBinding())),
                            TimelineChangePayload.ofString(
                                    com.example.platform.timeline.semantics.clip
                                            .TimelineSourceBindingCanonicalSemantics
                                            .semanticFingerprint(ac.sourceBinding())),
                            srcMeta));
                }
                if (!java.util.Objects.equals(bc.temporalMapping(), ac.temporalMapping())) {
                    String beforeTm = bc.temporalMapping() == null ? "" : temporalMappingJson(bc.temporalMapping());
                    String afterTm = ac.temporalMapping() == null ? "" : temporalMappingJson(ac.temporalMapping());
                    Map<String, String> tmMeta = new LinkedHashMap<>();
                    tmMeta.put("temporalMapping", afterTm);
                    ops.add(new TimelineChangeOperation(
                            new TimelineChangeOperationId("op-" + (++seq[0])),
                            TimelineChangeType.CLIP_SPEED_CHANGED,
                            TimelineChangeScope.CLIP,
                            new TimelineChangePath(clipPath + ".temporalMapping"),
                            TimelineChangePayload.ofString(beforeTm),
                            TimelineChangePayload.ofString(afterTm),
                            tmMeta));
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

    // CHECKPOINT_A: AudioMix is a bounded semantic component — whole-value
    // equality drives AUDIO_MIX_CHANGED; no DSP-level field merge invented here.
    private void diffAudioMix(CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after,
                              List<TimelineChangeOperation> ops, int[] seq) {
        com.example.platform.audio.domain.mix.AudioMix b = before.audioMix();
        com.example.platform.audio.domain.mix.AudioMix a = after.audioMix();
        if (b == null) b = com.example.platform.audio.domain.mix.AudioMix.empty();
        if (a == null) a = com.example.platform.audio.domain.mix.AudioMix.empty();
        if (!b.equals(a)) {
            Map<String, String> meta = new LinkedHashMap<>();
            // R4-A4: AudioMix canonical encoding delegated to the Audio-domain
            // authority (Timeline never owns AudioMix field grammar).
            meta.put("audioMix", com.example.platform.audio.domain.mix.AudioMixCanonicalSemantics
                    .canonicalJson(a));
            ops.add(new TimelineChangeOperation(
                    new TimelineChangeOperationId("op-" + (++seq[0])),
                    TimelineChangeType.AUDIO_MIX_CHANGED,
                    TimelineChangeScope.TIMELINE,
                    new TimelineChangePath("timeline.audioMix"),
                    TimelineChangePayload.ofString(com.example.platform.audio.domain.mix.AudioMixCanonicalSemantics.semanticFingerprint(b)),
                    TimelineChangePayload.ofString(com.example.platform.audio.domain.mix.AudioMixCanonicalSemantics.semanticFingerprint(a)),
                    meta));
        }
    }

    // CHECKPOINT_A: SemanticRelationships are identity-based typed semantics.
    // Conservative identity matching (Group: groupId; Sync: normalized endpoint
    // pair), then member/anchor deltas. No naive field merge, no Map payloads.
    private void diffRelationships(CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after,
                                   List<TimelineChangeOperation> ops, int[] seq) {
        Map<String, com.example.platform.timeline.semantics.relationship.SemanticRelationship> beforeRels =
                relationshipMap(before.semanticRelationships());
        Map<String, com.example.platform.timeline.semantics.relationship.SemanticRelationship> afterRels =
                relationshipMap(after.semanticRelationships());

        for (String key : beforeRels.keySet()) {
            if (!afterRels.containsKey(key)) {
                ops.add(change(seq, TimelineChangeType.RELATIONSHIP_REMOVED,
                        TimelineChangeScope.TIMELINE, "timeline.semanticRelationships." + key,
                        key, null));
            }
        }
        for (String key : afterRels.keySet()) {
            if (!beforeRels.containsKey(key)) {
                Map<String, String> meta = new LinkedHashMap<>();
                meta.put("relationship", relationshipJson(afterRels.get(key)));
                ops.add(new TimelineChangeOperation(
                        new TimelineChangeOperationId("op-" + (++seq[0])),
                        TimelineChangeType.RELATIONSHIP_ADDED,
                        TimelineChangeScope.TIMELINE,
                        new TimelineChangePath("timeline.semanticRelationships." + key),
                        TimelineChangePayload.empty(),
                        TimelineChangePayload.ofString(key),
                        meta));
                continue;
            }
            var b = beforeRels.get(key);
            var a = afterRels.get(key);
            if (b.equals(a)) {
                continue;
            }
            // Same identity, different content → member/anchor deltas.
            if (b instanceof com.example.platform.timeline.semantics.relationship.GroupRelationship bg
                    && a instanceof com.example.platform.timeline.semantics.relationship.GroupRelationship ag) {
                // R4-A3: membership delta delegated to the Relationship-local
                // authority — Timeline never computes Group membership diff itself.
                for (var delta : com.example.platform.timeline.semantics.relationship
                        .RelationshipCanonicalSemantics.groupMemberDelta(b, a)) {
                    if (delta.added()) {
                        ops.add(change(seq, TimelineChangeType.GROUP_MEMBER_ADDED,
                                TimelineChangeScope.TIMELINE, "timeline.semanticRelationships." + key,
                                null, delta.member().value()));
                    } else {
                        ops.add(change(seq, TimelineChangeType.GROUP_MEMBER_REMOVED,
                                TimelineChangeScope.TIMELINE, "timeline.semanticRelationships." + key,
                                delta.member().value(), null));
                    }
                }
            } else if (b instanceof com.example.platform.timeline.semantics.relationship.SyncRelationship bs
                    && a instanceof com.example.platform.timeline.semantics.relationship.SyncRelationship as) {
                // R4-A3: Sync anchor change is a SINGLE typed local op
                // (SYNC_ANCHOR_CHANGED with the complete canonical after
                // payload) — never remove+add. remove+add on the same path
                // reorders badly in the merge planner (ADDED ordinal < REMOVED
                // ordinal → add-then-remove → relationship vanishes).
                if (com.example.platform.timeline.semantics.relationship.RelationshipCanonicalSemantics
                        .syncAnchorChanged(bs, as)) {
                    Map<String, String> meta = new LinkedHashMap<>();
                    meta.put("relationship", relationshipJson(a));
                    ops.add(new TimelineChangeOperation(
                            new TimelineChangeOperationId("op-" + (++seq[0])),
                            TimelineChangeType.SYNC_ANCHOR_CHANGED,
                            TimelineChangeScope.TIMELINE,
                            new TimelineChangePath("timeline.semanticRelationships." + key),
                            TimelineChangePayload.ofString(relationshipJson(b)),
                            TimelineChangePayload.ofString(relationshipJson(a)),
                            meta));
                }
            } else {
                // Conservative whole-relationship replace (unknown variant pairing
                // can never reach here: identity matching guarantees same kind).
                ops.add(change(seq, TimelineChangeType.RELATIONSHIP_REMOVED,
                        TimelineChangeScope.TIMELINE, "timeline.semanticRelationships." + key,
                        key, null));
                Map<String, String> meta = new LinkedHashMap<>();
                meta.put("relationship", relationshipJson(a));
                ops.add(new TimelineChangeOperation(
                        new TimelineChangeOperationId("op-" + (++seq[0])),
                        TimelineChangeType.RELATIONSHIP_ADDED,
                        TimelineChangeScope.TIMELINE,
                        new TimelineChangePath("timeline.semanticRelationships." + key),
                        TimelineChangePayload.empty(),
                        TimelineChangePayload.ofString(key),
                        meta));
            }
        }
    }

    private static Map<String, com.example.platform.timeline.semantics.relationship.SemanticRelationship>
            relationshipMap(java.util.List<com.example.platform.timeline.semantics.relationship.SemanticRelationship> rels) {
        Map<String, com.example.platform.timeline.semantics.relationship.SemanticRelationship> map = new java.util.LinkedHashMap<>();
        if (rels != null) {
            for (var r : rels) {
                map.put(relationshipKey(r), r);
            }
        }
        return map;
    }

    // CHECKPOINT_A Round 3: identity/normalization/encoding owned by
    // RelationshipCanonicalSemantics (Timeline orchestrates, never re-derives).
    private static String relationshipKey(com.example.platform.timeline.semantics.relationship.SemanticRelationship r) {
        return com.example.platform.timeline.semantics.relationship.RelationshipCanonicalSemantics
                .canonicalKey(r);
    }

    private static String relationshipJson(com.example.platform.timeline.semantics.relationship.SemanticRelationship r) {
        return com.example.platform.timeline.semantics.relationship.RelationshipCanonicalSemantics
                .canonicalJson(r);
    }

    private static String temporalMappingJson(com.example.platform.timeline.semantics.temporal.TemporalMapping tm) {
        try {
            return com.example.platform.timeline.app.InternalTimelineJson.mapper().writeValueAsString(tm);
        } catch (Exception e) {
            throw new IllegalStateException("TemporalMapping canonical encoding failed", e);
        }
    }

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

    /** TRANSITION delete op (THIRD CORRECTION): explicit, non-ambiguous.
     *  R4-A1: delete carries the full canonical before payload — Timeline never
     *  enumerates Transition-local fields. */
    private TimelineChangeOperation transitionDeletedOp(int[] seq, String id,
            CanonicalTimelineTransitionSnapshot t) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("deleted", "true");
        meta.put("transition", com.example.platform.timeline.semantics.transition
                .TransitionCanonicalSemantics.encode(t));
        return new TimelineChangeOperation(
                new TimelineChangeOperationId("op-" + (++seq[0])),
                TimelineChangeType.TRANSITION_CHANGED, TimelineChangeScope.TRANSITION,
                new TimelineChangePath("timeline.transitions." + id),
                TimelineChangePayload.ofString(t.semanticFingerprint()),
                TimelineChangePayload.empty(),
                meta);
    }

    /** TRANSITION_CHANGED op with after-state reconstruction data in safeMetadata.
     *  R4-A1: the FULL Transition-local canonical payload rides the op — no
     *  independent field enumeration (definition/version/participants/mediaType/
     *  duration/alignment/policy/parameters all live inside the authority value). */
    private TimelineChangeOperation transitionOp(int[] seq, String id,
            CanonicalTimelineTransitionSnapshot t) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("transition", com.example.platform.timeline.semantics.transition
                .TransitionCanonicalSemantics.encode(t));
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
                meta.put("automation", com.example.platform.timeline.semantics.automation
                        .AutomationCanonicalSemantics.encode(bc));
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

    /** AUTOMATION_CHANGED op with after-state reconstruction data in safeMetadata.
     *  R4-A2: the FULL Automation-local canonical payload rides the op — no
     *  independent field enumeration (target/path/valueType/extrapolation/
     *  keyframes all live inside the authority value). */
    private TimelineChangeOperation automationOp(int[] seq, String id,
            CanonicalTimelineAutomationSnapshot c) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("automation", com.example.platform.timeline.semantics.automation
                .AutomationCanonicalSemantics.encode(c));
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
                // ROADMAP #19 (CORRECTION 1 / TT-C1): deletion carries the
                // COMPLETE canonical before payload — never just the id.
                Map<String, String> delMeta = new java.util.LinkedHashMap<>();
                delMeta.put("deleted", "true");
                ops.add(new TimelineChangeOperation(
                        new TimelineChangeOperationId("op-" + (++seq[0])),
                        TimelineChangeType.TEXT_ELEMENT_CHANGED, TimelineChangeScope.TEXT_ELEMENT,
                        new TimelineChangePath("timeline.textElements." + id),
                        TimelineChangePayload.ofString(TimedTextCanonicalSemantics.semanticFingerprint(b)),
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
                // ROADMAP #19 (CORRECTION 1 / TT-C1): ADD carries the COMPLETE
                // canonical after payload — never just the id.
                ops.add(change(seq, TimelineChangeType.TEXT_ELEMENT_CHANGED,
                        TimelineChangeScope.TEXT_ELEMENT, "timeline.textElements." + id,
                        null,
                        TimedTextCanonicalSemantics.semanticFingerprint(afterMap.get(id))));
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
        // CHECKPOINT_A: full typed source semantics ride the CLIP_ADDED op.
        // R4-B: the TYPED binding payload is the authoritative source-semantics
        // carrier (flat fields remain serialization projections for legacy
        // consumers — never the semantic authority).
        if (clip.sourceBinding() != null) {
            meta.put("sourceBinding", com.example.platform.timeline.semantics.clip
                    .TimelineSourceBindingCanonicalSemantics.encode(clip.sourceBinding()));
        }
        if (clip.sourceKind() != null) {
            meta.put("sourceKind", clip.sourceKind());
        }
        if (clip.mediaStreamId() != null) {
            meta.put("mediaStreamId", clip.mediaStreamId());
        }
        if (clip.artifactId() != null) {
            meta.put("artifactId", clip.artifactId());
        }
        if (clip.contentDigest() != null) {
            meta.put("contentDigest", clip.contentDigest());
        }
        if (clip.temporalMapping() != null) {
            try {
                meta.put("temporalMapping", com.example.platform.timeline.app.InternalTimelineJson.mapper()
                        .writeValueAsString(clip.temporalMapping()));
            } catch (Exception e) {
                throw new IllegalStateException("TemporalMapping canonical encoding failed", e);
            }
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
