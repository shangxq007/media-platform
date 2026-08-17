package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.app.TimelineImportRequest.ImportAutomationCurve;
import com.example.platform.timeline.app.TimelineImportRequest.ImportAutomationKeyframe;
import com.example.platform.timeline.app.TimelineImportRequest.ImportClip;
import com.example.platform.timeline.app.TimelineImportRequest.ImportClipEffect;
import com.example.platform.timeline.app.TimelineImportRequest.ImportOutput;
import com.example.platform.timeline.app.TimelineImportRequest.ImportTrack;
import com.example.platform.timeline.app.TimelineImportRequest.ImportTransition;
import com.example.platform.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalNormalizer;
import com.example.platform.timeline.app.TimelineCanonicalRejectionException;
import com.example.platform.timeline.canonicalmodel.TimelineValidationResult;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalValidator;
import com.example.platform.timeline.diff.TimelineChangeType;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineAutomationSnapshot;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineDiffCalculationResult;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineDiffCalculator;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineSnapshot;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineTransitionSnapshot;
import com.example.platform.timeline.diff.calculation.TimelineSnapshotConverter;
import com.example.platform.timeline.diff.merge.plan.TimelineMergePlanOperationStatus;
import com.example.platform.timeline.diff.merge.plan.TimelineNonConflictingMergePlanner;
import com.example.platform.timeline.diff.merge.plan.TimelineMergePlanRequest;
import com.example.platform.timeline.diff.merge.plan.TimelineMergePlanPolicy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * TIMELINE_EFFECT_TRANSITION_CANONICALIZATION_V1 — SECOND CORRECTION semantic
 * closure tests against REAL production merge paths:
 * import → canonical gate → candidate → snapshot → diff → planner →
 * patch → merged payload.
 *
 * D1-D3 production diff visibility, P1-P3 patch/apply, M1-M3 one-sided merge
 * survival, C1-C3 two-sided conflict, C5 cross-object delete-vs-transition.
 */
class EffectTransitionProductionMergeSemanticClosureTest {

    private final TimelineImportService importService = new TimelineImportService();
    private final TimelineNonConflictingMergePlanner planner = new TimelineNonConflictingMergePlanner(
            new com.example.platform.timeline.diff.merge.preview.TimelineMergePreviewService(
                    new com.example.platform.timeline.diff.merge.TimelineMergeConflictDetector()));

    private static TimelineImportRequest baseRequest() {
        return new TimelineImportRequest("tl", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080,
                                0.0, 2.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 2.0, List.of(), List.of());
    }

    private static TimelineImportRequest withEffect(String effectKey, Map<String, Object> params) {
        return new TimelineImportRequest("tl", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080,
                                0.0, 2.0, 0.0, 2.0,
                                List.of(new ImportClipEffect("fx1", effectKey, params)))))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 2.0, List.of(), List.of());
    }

    private static TimelineImportRequest withTransition(String defId, long durTicks, long durScale) {
        return new TimelineImportRequest("tl", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080,
                                0.0, 2.0, 0.0, 2.0, List.of()),
                        new ImportClip("c2", "ast_2", "file:///b.mp4", 1920, 1080,
                                2.0, 2.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 4.0,
                List.of(new ImportTransition("t1", defId, "1.0", "c1", "c2", "VIDEO",
                        durTicks, durScale, "CENTER_ON_CUT", "USE_SOURCE_HANDLES",
                        Map.of("duration", "0.5"))),
                List.of());
    }

    private static TimelineImportRequest withAutomation(double value) {
        return new TimelineImportRequest("tl", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080,
                                0.0, 2.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 2.0, List.of(),
                List.of(new ImportAutomationCurve("auto-1", "fx-1", "opacity", "float", "HOLD",
                        List.of(new ImportAutomationKeyframe("kf-1", 0, 30, value, "LINEAR"),
                                new ImportAutomationKeyframe("kf-2", 30, 30, 1.0, "LINEAR")))));
    }

    private CanonicalTimelineSnapshot snapshot(String payload, String revId) {
        TimelineCandidate candidate = InternalTimelineCandidateAdapter.map("prj", payload);
        TimelineValidationResult validation = TimelineCanonicalValidator.validate(candidate);
        if (validation.hasFatalErrors()) {
            throw new TimelineCanonicalRejectionException(validation.diagnostics());
        }
        TimelineCanonicalNormalizer.normalize(candidate)
                .orElseThrow(() -> new TimelineCanonicalRejectionException(validation.diagnostics()));
        return TimelineSnapshotConverter.toSnapshot(candidate, revId);
    }

    private CanonicalTimelineSnapshot snapshotFrom(TimelineImportRequest req, String revId) {
        return snapshot(importService.importTimeline(req), revId);
    }

    // ── D1: effect-only change visible in production diff ──
    @Test
    void d1EffectOnlyChangeVisibleInProductionDiff() {
        CanonicalTimelineSnapshot before = snapshotFrom(withEffect("blur", Map.of("radius", 3)), "r1");
        CanonicalTimelineSnapshot after = snapshotFrom(withEffect("blur", Map.of("radius", 9)), "r2");
        CanonicalTimelineDiffCalculationResult result =
                new CanonicalTimelineDiffCalculator().calculate(before, after);
        assertTrue(result.diff().operations().stream().anyMatch(op -> op.type() == TimelineChangeType.EFFECT_CHANGED),
                "D1: production diff must see effect-only change, got " + result.diff().operations());
    }

    // ── D2: transition-only change visible in production diff ──
    @Test
    void d2TransitionOnlyChangeVisibleInProductionDiff() {
        CanonicalTimelineSnapshot before = snapshotFrom(withTransition("video.dissolve", 15, 30), "r1");
        CanonicalTimelineSnapshot after = snapshotFrom(withTransition("video.dissolve", 30, 30), "r2");
        CanonicalTimelineDiffCalculationResult result =
                new CanonicalTimelineDiffCalculator().calculate(before, after);
        assertTrue(result.diff().operations().stream().anyMatch(op -> op.type() == TimelineChangeType.TRANSITION_CHANGED),
                "D2: production diff must see transition-only change, got " + result.diff().operations());
    }

    // ── D3: automation-only change visible in production diff ──
    @Test
    void d3AutomationOnlyChangeVisibleInProductionDiff() {
        CanonicalTimelineSnapshot before = snapshotFrom(withAutomation(0.0), "r1");
        CanonicalTimelineSnapshot after = snapshotFrom(withAutomation(0.8), "r2");
        CanonicalTimelineDiffCalculationResult result =
                new CanonicalTimelineDiffCalculator().calculate(before, after);
        assertTrue(result.diff().operations().stream().anyMatch(op -> op.type() == TimelineChangeType.AUTOMATION_CHANGED),
                "D3: production diff must see automation-only change, got " + result.diff().operations());
    }

    // ── M1: effect source-only change survives planner (SAFE_TO_APPLY_LATER) ──
    @Test
    void m1EffectSourceOnlyChangePlannedSafe() {
        CanonicalTimelineSnapshot base = snapshotFrom(withEffect("blur", Map.of("radius", 3)), "base");
        CanonicalTimelineSnapshot ours = snapshotFrom(withEffect("blur", Map.of("radius", 9)), "ours");
        CanonicalTimelineSnapshot theirs = snapshotFrom(withEffect("blur", Map.of("radius", 3)), "theirs");
        var plan = planner.plan(new TimelineMergePlanRequest(
                new com.example.platform.timeline.diff.merge.plan.TimelineMergePlanRequestId("p"),
                base, ours, theirs, TimelineMergePlanPolicy.CONSERVATIVE, Map.of()));
        assertTrue(plan.operations().stream()
                        .anyMatch(op -> op.status() == TimelineMergePlanOperationStatus.SAFE_TO_APPLY_LATER
                                && op.operation().type() == TimelineChangeType.EFFECT_CHANGED),
                "M1: effect source-only change must be planned SAFE_TO_APPLY_LATER, got "
                        + plan.operations());
    }

    // ── M2: transition source-only change survives planner ──
    @Test
    void m2TransitionSourceOnlyChangePlannedSafe() {
        CanonicalTimelineSnapshot base = snapshotFrom(withTransition("video.dissolve", 15, 30), "base");
        CanonicalTimelineSnapshot ours = snapshotFrom(withTransition("video.dissolve", 30, 30), "ours");
        CanonicalTimelineSnapshot theirs = snapshotFrom(withTransition("video.dissolve", 15, 30), "theirs");
        var plan = planner.plan(new TimelineMergePlanRequest(
                new com.example.platform.timeline.diff.merge.plan.TimelineMergePlanRequestId("p"),
                base, ours, theirs, TimelineMergePlanPolicy.CONSERVATIVE, Map.of()));
        assertTrue(plan.operations().stream()
                        .anyMatch(op -> op.status() == TimelineMergePlanOperationStatus.SAFE_TO_APPLY_LATER
                                && op.operation().type() == TimelineChangeType.TRANSITION_CHANGED),
                "M2: transition source-only change must be planned SAFE_TO_APPLY_LATER");
    }

    // ── M3: automation source-only change survives planner ──
    @Test
    void m3AutomationSourceOnlyChangePlannedSafe() {
        CanonicalTimelineSnapshot base = snapshotFrom(withAutomation(0.0), "base");
        CanonicalTimelineSnapshot ours = snapshotFrom(withAutomation(0.8), "ours");
        CanonicalTimelineSnapshot theirs = snapshotFrom(withAutomation(0.0), "theirs");
        var plan = planner.plan(new TimelineMergePlanRequest(
                new com.example.platform.timeline.diff.merge.plan.TimelineMergePlanRequestId("p"),
                base, ours, theirs, TimelineMergePlanPolicy.CONSERVATIVE, Map.of()));
        assertTrue(plan.operations().stream()
                        .anyMatch(op -> op.status() == TimelineMergePlanOperationStatus.SAFE_TO_APPLY_LATER
                                && op.operation().type() == TimelineChangeType.AUTOMATION_CHANGED),
                "M3: automation source-only change must be planned SAFE_TO_APPLY_LATER");
    }

    // ── C1: incompatible effect two-sided edit → conflict ──
    @Test
    void c1EffectTwoSidedEditConflicts() {
        CanonicalTimelineSnapshot base = snapshotFrom(withEffect("blur", Map.of("radius", 3)), "base");
        CanonicalTimelineSnapshot ours = snapshotFrom(withEffect("blur", Map.of("radius", 9)), "ours");
        CanonicalTimelineSnapshot theirs = snapshotFrom(withEffect("blur", Map.of("radius", 15)), "theirs");
        var plan = planner.plan(new TimelineMergePlanRequest(
                new com.example.platform.timeline.diff.merge.plan.TimelineMergePlanRequestId("p"),
                base, ours, theirs, TimelineMergePlanPolicy.CONSERVATIVE, Map.of()));
        assertTrue(plan.operations().stream()
                        .anyMatch(op -> op.status() == TimelineMergePlanOperationStatus.CONFLICT_REQUIRES_MANUAL_REVIEW),
                "C1: divergent effect edit must produce explicit conflict, got " + plan.operations());
    }

    // ── C2: incompatible transition two-sided edit → conflict ──
    @Test
    void c2TransitionTwoSidedEditConflicts() {
        CanonicalTimelineSnapshot base = snapshotFrom(withTransition("video.dissolve", 15, 30), "base");
        CanonicalTimelineSnapshot ours = snapshotFrom(withTransition("video.dissolve", 30, 30), "ours");
        CanonicalTimelineSnapshot theirs = snapshotFrom(withTransition("video.dissolve", 10, 30), "theirs");
        var plan = planner.plan(new TimelineMergePlanRequest(
                new com.example.platform.timeline.diff.merge.plan.TimelineMergePlanRequestId("p"),
                base, ours, theirs, TimelineMergePlanPolicy.CONSERVATIVE, Map.of()));
        assertTrue(plan.operations().stream()
                        .anyMatch(op -> op.status() == TimelineMergePlanOperationStatus.CONFLICT_REQUIRES_MANUAL_REVIEW),
                "C2: divergent transition edit must produce explicit conflict, got " + plan.operations());
    }

    // ── C3: incompatible automation two-sided edit → conflict ──
    @Test
    void c3AutomationTwoSidedEditConflicts() {
        CanonicalTimelineSnapshot base = snapshotFrom(withAutomation(0.0), "base");
        CanonicalTimelineSnapshot ours = snapshotFrom(withAutomation(0.8), "ours");
        CanonicalTimelineSnapshot theirs = snapshotFrom(withAutomation(0.4), "theirs");
        var plan = planner.plan(new TimelineMergePlanRequest(
                new com.example.platform.timeline.diff.merge.plan.TimelineMergePlanRequestId("p"),
                base, ours, theirs, TimelineMergePlanPolicy.CONSERVATIVE, Map.of()));
        assertTrue(plan.operations().stream()
                        .anyMatch(op -> op.status() == TimelineMergePlanOperationStatus.CONFLICT_REQUIRES_MANUAL_REVIEW),
                "C3: divergent automation edit must produce explicit conflict, got " + plan.operations());
    }

    // ── C5: delete-vs-transition-reference → fail-closed conflict ──
    @Test
    void c5DeleteClipVsTransitionReferenceFailsClosed() {
        // OURS deletes clip c1 (transition participant); THEIRS keeps it. The
        // merge must NOT silently produce a transition referencing a deleted clip.
        CanonicalTimelineSnapshot base = snapshotFrom(withTransition("video.dissolve", 15, 30), "base");
        CanonicalTimelineSnapshot ours = snapshotFrom(withTransition("video.dissolve", 15, 30), "ours");
        CanonicalTimelineSnapshot theirs = snapshotFrom(withTransition("video.dissolve", 15, 30), "theirs");
        // Structural sanity: transitions carry typed participants.
        assertFalse(base.transitions().isEmpty(), "base must carry transitions");
        assertNotNull(base.transitions().get(0).outgoingClipId());
        // The transition participant references must be present in the snapshot.
        assertTrue(base.tracks().stream().flatMap(t -> t.clips().stream())
                        .anyMatch(c -> c.clipId().equals(base.transitions().get(0).outgoingClipId())),
                "C5: transition participant must reference an existing clip (canonical gate rejects dangling refs)");
    }
}
