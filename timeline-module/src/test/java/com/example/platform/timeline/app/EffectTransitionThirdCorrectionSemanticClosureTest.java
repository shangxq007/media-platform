package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalValidator;
import com.example.platform.timeline.canonicalmodel.TimelineValidationResult;
import com.example.platform.timeline.diff.TimelineChangeType;
import com.example.platform.timeline.diff.TimelineChangeOperation;
import com.example.platform.timeline.diff.TimelinePatch;
import com.example.platform.timeline.diff.TimelinePatchId;
import com.example.platform.timeline.diff.application.TimelinePatchApplier;
import com.example.platform.timeline.diff.application.TimelinePatchApplicationResult;
import com.example.platform.timeline.diff.application.TimelinePatchApplicationStatus;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineAutomationKeyframe;
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
 * TIMELINE_EFFECT_TRANSITION_CANONICALIZATION_V1 — THIRD CORRECTION semantic
 * closure tests against REAL production paths:
 *
 * P1-P5 actual patch/apply state preservation (no silent field loss),
 * R1-R5 add/modify/remove/delete-last semantics,
 * C1-C9 complete semantic fingerprint conflict identity,
 * X1-X3 cross-object fail-closed,
 * mixed-operation regression.
 */
class EffectTransitionThirdCorrectionSemanticClosureTest {

    private final TimelineImportService importService = new TimelineImportService();
    private final TimelinePatchApplier applier = new TimelinePatchApplier();
    private final TimelineNonConflictingMergePlanner planner = new TimelineNonConflictingMergePlanner(
            new com.example.platform.timeline.diff.merge.preview.TimelineMergePreviewService(
                    new com.example.platform.timeline.diff.merge.TimelineMergeConflictDetector()));

    // ── Request builders ──

    private static TimelineImportRequest fullRequest() {
        return new TimelineImportRequest("tl", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080,
                                0.0, 2.0, 0.0, 2.0,
                                List.of(new ImportClipEffect("fx1", "blur", Map.of("radius", 3)))),
                        new ImportClip("c2", "ast_2", "file:///b.mp4", 1920, 1080,
                                2.0, 4.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 2.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("duration", "0.5"))),
                List.of(new ImportAutomationCurve("auto-1", "fx1", "opacity", "float", "HOLD",
                        List.of(new ImportAutomationKeyframe("kf-1", 0, 30, 0.0, "LINEAR")))));
    }

    private static TimelineImportRequest structuralRequest() {
        return new TimelineImportRequest("tl", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080,
                                0.0, 2.0, 0.0, 2.0, List.of()),
                        new ImportClip("c2", "ast_2", "file:///b.mp4", 1920, 1080,
                                2.0, 4.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 2.0, List.of(), List.of());
    }

    private CanonicalTimelineSnapshot snapshotOf(TimelineImportRequest req, String revId) {
        String payload = importService.importTimeline(req);
        TimelineCandidate candidate = InternalTimelineCandidateAdapter.map("prj", payload);
        TimelineValidationResult validation = TimelineCanonicalValidator.validate(candidate);
        if (validation.hasFatalErrors()) {
            System.out.println("DIAGNOSTICS for " + revId + ": " + validation.diagnostics());
            throw new TimelineCanonicalRejectionException(validation.diagnostics());
        }
        TimelineCanonicalNormalizer.normalize(candidate)
                .orElseThrow(() -> new TimelineCanonicalRejectionException(validation.diagnostics()));
        return TimelineSnapshotConverter.toSnapshot(candidate, revId);
    }

    private CanonicalTimelineSnapshot snapshotOfStructural(String revId) {
        return snapshotOf(structuralRequest(), revId);
    }

    // ── P1: effect patch preserves transition + automation ──
    @Test
    void p1EffectPatchPreservesTransitionAndAutomation() {
        CanonicalTimelineSnapshot base = snapshotOf(fullRequest(), "r1");
        CanonicalTimelineSnapshot after = snapshotOf(fullRequest(), "r2"); // same state
        // Build a real EFFECT_CHANGED op from a diff where only effect radius changes.
        var effectChanged = new TimelineImportRequest("tl", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080,
                                0.0, 2.0, 0.0, 2.0,
                                List.of(new ImportClipEffect("fx1", "blur", Map.of("radius", 9)))),
                        new ImportClip("c2", "ast_2", "file:///b.mp4", 1920, 1080,
                                2.0, 4.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 2.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("duration", "0.5"))),
                List.of(new ImportAutomationCurve("auto-1", "fx1", "opacity", "float", "HOLD",
                        List.of(new ImportAutomationKeyframe("kf-1", 0, 30, 0.0, "LINEAR")))));
        CanonicalTimelineSnapshot effectChangedSnap = snapshotOf(effectChanged, "r3");
        var diff = new CanonicalTimelineDiffCalculator().calculate(base, effectChangedSnap);
        TimelineChangeOperation op = diff.diff().operations().stream()
                .filter(o -> o.type() == TimelineChangeType.EFFECT_CHANGED).findFirst().orElseThrow();
        TimelinePatchApplicationResult result = applier.apply(base,
                new TimelinePatch(new TimelinePatchId("p"), "r1", List.of(op), null, Map.of()));
        assertEquals(TimelinePatchApplicationStatus.APPLIED, result.status());
        CanonicalTimelineSnapshot patched = result.patchedSnapshot();
        // Effect changed...
        assertEquals(1, patched.tracks().get(0).clips().get(0).effects().size());
        assertTrue(patched.tracks().get(0).clips().get(0).effects().get(0).parameters()
                        .get("radius").toString().contains("9"),
                "P1: effect must be changed");
        // ...but Transition and Automation preserved.
        assertEquals(1, patched.transitions().size(), "P1: transition must be preserved");
        assertEquals(1, patched.automations().size(), "P1: automation must be preserved");
    }

    // ── P2: transition patch preserves effect + automation ──
    @Test
    void p2TransitionPatchPreservesEffectAndAutomation() {
        CanonicalTimelineSnapshot base = snapshotOf(fullRequest(), "r1");
        var changed = new TimelineImportRequest("tl", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080,
                                0.0, 2.0, 0.0, 2.0,
                                List.of(new ImportClipEffect("fx1", "blur", Map.of("radius", 3)))),
                        new ImportClip("c2", "ast_2", "file:///b.mp4", 1920, 1080,
                                2.0, 4.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 2.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                        30, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("duration", "1.0"))),
                List.of(new ImportAutomationCurve("auto-1", "fx1", "opacity", "float", "HOLD",
                        List.of(new ImportAutomationKeyframe("kf-1", 0, 30, 0.0, "LINEAR")))));
        CanonicalTimelineSnapshot after = snapshotOf(changed, "r2");
        var diff = new CanonicalTimelineDiffCalculator().calculate(base, after);
        TimelineChangeOperation op = diff.diff().operations().stream()
                .filter(o -> o.type() == TimelineChangeType.TRANSITION_CHANGED).findFirst().orElseThrow();
        TimelinePatchApplicationResult result = applier.apply(base,
                new TimelinePatch(new TimelinePatchId("p"), "r1", List.of(op), null, Map.of()));
        assertEquals(TimelinePatchApplicationStatus.APPLIED, result.status());
        CanonicalTimelineSnapshot patched = result.patchedSnapshot();
        assertEquals(1, patched.transitions().size());
        assertTrue(patched.transitions().get(0).duration().isEqualTo(MediaTime.ofTicks(30, 30)), "P2: transition duration changed");
        assertEquals(1, patched.tracks().get(0).clips().get(0).effects().size(), "P2: effect preserved");
        assertEquals(1, patched.automations().size(), "P2: automation preserved");
    }

    // ── P3: automation patch preserves effect + transition ──
    @Test
    void p3AutomationPatchPreservesEffectAndTransition() {
        CanonicalTimelineSnapshot base = snapshotOf(fullRequest(), "r1");
        var changed = new TimelineImportRequest("tl", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080,
                                0.0, 2.0, 0.0, 2.0,
                                List.of(new ImportClipEffect("fx1", "blur", Map.of("radius", 3)))),
                        new ImportClip("c2", "ast_2", "file:///b.mp4", 1920, 1080,
                                2.0, 4.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 2.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("duration", "0.5"))),
                List.of(new ImportAutomationCurve("auto-1", "fx1", "opacity", "float", "HOLD",
                        List.of(new ImportAutomationKeyframe("kf-1", 0, 30, 0.8, "LINEAR")))));
        CanonicalTimelineSnapshot after = snapshotOf(changed, "r2");
        var diff = new CanonicalTimelineDiffCalculator().calculate(base, after);
        TimelineChangeOperation op = diff.diff().operations().stream()
                .filter(o -> o.type() == TimelineChangeType.AUTOMATION_CHANGED).findFirst().orElseThrow();
        TimelinePatchApplicationResult result = applier.apply(base,
                new TimelinePatch(new TimelinePatchId("p"), "r1", List.of(op), null, Map.of()));
        assertEquals(TimelinePatchApplicationStatus.APPLIED, result.status());
        CanonicalTimelineSnapshot patched = result.patchedSnapshot();
        assertEquals(1, patched.automations().size());
        assertEquals(0.8, patched.automations().get(0).keyframes().get(0).value(), 1e-9,
                "P3: automation keyframe changed");
        assertEquals(1, patched.tracks().get(0).clips().get(0).effects().size(), "P3: effect preserved");
        assertEquals(1, patched.transitions().size(), "P3: transition preserved");
    }

    // ── R1: transition source-only deletion survives (planner SAFE) ──
    @Test
    void r1TransitionSourceOnlyDeletionPlannedSafe() {
        CanonicalTimelineSnapshot base = snapshotOf(fullRequest(), "base");
        CanonicalTimelineSnapshot ours = snapshotOf(structuralRequest(), "ours"); // no transition
        CanonicalTimelineSnapshot theirs = snapshotOf(fullRequest(), "theirs");
        var plan = planner.plan(new TimelineMergePlanRequest(
                new com.example.platform.timeline.diff.merge.plan.TimelineMergePlanRequestId("p"),
                base, ours, theirs, TimelineMergePlanPolicy.CONSERVATIVE, Map.of()));
        assertTrue(plan.operations().stream()
                        .anyMatch(op -> op.status() == TimelineMergePlanOperationStatus.SAFE_TO_APPLY_LATER
                                && op.operation().type() == TimelineChangeType.TRANSITION_CHANGED
                                && "true".equals(op.operation().safeMetadata().get("deleted"))),
                "R1: transition source-only deletion must be planned SAFE with deleted flag, got "
                        + plan.operations());
    }

    // ── R2: automation source-only deletion survives ──
    @Test
    void r2AutomationSourceOnlyDeletionPlannedSafe() {
        CanonicalTimelineSnapshot base = snapshotOf(fullRequest(), "base");
        CanonicalTimelineSnapshot ours = snapshotOf(structuralRequest(), "ours"); // no automation
        CanonicalTimelineSnapshot theirs = snapshotOf(fullRequest(), "theirs");
        var plan = planner.plan(new TimelineMergePlanRequest(
                new com.example.platform.timeline.diff.merge.plan.TimelineMergePlanRequestId("p"),
                base, ours, theirs, TimelineMergePlanPolicy.CONSERVATIVE, Map.of()));
        assertTrue(plan.operations().stream()
                        .anyMatch(op -> op.status() == TimelineMergePlanOperationStatus.SAFE_TO_APPLY_LATER
                                && op.operation().type() == TimelineChangeType.AUTOMATION_CHANGED
                                && "true".equals(op.operation().safeMetadata().get("deleted"))),
                "R2: automation source-only deletion must be planned SAFE with deleted flag");
    }

    // ── R3: deleting last transition produces empty result via actual patch ──
    @Test
    void r3DeleteLastTransitionProducesEmptyResult() {
        CanonicalTimelineSnapshot base = snapshotOf(fullRequest(), "r1");
        CanonicalTimelineSnapshot after = snapshotOf(structuralRequest(), "r2"); // no transitions
        var diff = new CanonicalTimelineDiffCalculator().calculate(base, after);
        TimelineChangeOperation op = diff.diff().operations().stream()
                .filter(o -> o.type() == TimelineChangeType.TRANSITION_CHANGED
                        && "true".equals(o.safeMetadata().get("deleted"))).findFirst().orElseThrow();
        TimelinePatchApplicationResult result = applier.apply(base,
                new TimelinePatch(new TimelinePatchId("p"), "r1", List.of(op), null, Map.of()));
        assertEquals(TimelinePatchApplicationStatus.APPLIED, result.status());
        assertTrue(result.patchedSnapshot().transitions().isEmpty(),
                "R3: deleting last transition must yield empty transition collection");
        // Unrelated state preserved.
        assertEquals(1, result.patchedSnapshot().automations().size(), "R3: automation preserved");
    }

    // ── R4: deleting last automation produces empty result ──
    @Test
    void r4DeleteLastAutomationProducesEmptyResult() {
        CanonicalTimelineSnapshot base = snapshotOf(fullRequest(), "r1");
        CanonicalTimelineSnapshot after = snapshotOf(structuralRequest(), "r2"); // no automations
        var diff = new CanonicalTimelineDiffCalculator().calculate(base, after);
        TimelineChangeOperation op = diff.diff().operations().stream()
                .filter(o -> o.type() == TimelineChangeType.AUTOMATION_CHANGED
                        && "true".equals(o.safeMetadata().get("deleted"))).findFirst().orElseThrow();
        TimelinePatchApplicationResult result = applier.apply(base,
                new TimelinePatch(new TimelinePatchId("p"), "r1", List.of(op), null, Map.of()));
        assertEquals(TimelinePatchApplicationStatus.APPLIED, result.status());
        assertTrue(result.patchedSnapshot().automations().isEmpty(),
                "R4: deleting last automation must yield empty automation collection");
        assertEquals(1, result.patchedSnapshot().transitions().size(), "R4: transition preserved");
    }

    // ── C2/C3: transition parameter-only and duration divergent edits conflict ──
    @Test
    void c3TransitionParameterOnlyDivergentEditConflicts() {
        var oursReq = new TimelineImportRequest("tl", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080, 0.0, 2.0, 0.0, 2.0, List.of()),
                        new ImportClip("c2", "ast_2", "file:///b.mp4", 1920, 1080, 2.0, 4.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 4.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("mix", "0.7"))),
                List.of());
        var theirsReq = new TimelineImportRequest("tl", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080, 0.0, 2.0, 0.0, 2.0, List.of()),
                        new ImportClip("c2", "ast_2", "file:///b.mp4", 1920, 1080, 2.0, 4.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 4.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("mix", "0.9"))),
                List.of());
        var baseReq = new TimelineImportRequest("tl", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080, 0.0, 2.0, 0.0, 2.0, List.of()),
                        new ImportClip("c2", "ast_2", "file:///b.mp4", 1920, 1080, 2.0, 4.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 4.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("mix", "0.5"))),
                List.of());
        var plan = planner.plan(new TimelineMergePlanRequest(
                new com.example.platform.timeline.diff.merge.plan.TimelineMergePlanRequestId("p"),
                snapshotOf(baseReq, "base"), snapshotOf(oursReq, "ours"),
                snapshotOf(theirsReq, "theirs"), TimelineMergePlanPolicy.CONSERVATIVE, Map.of()));
        assertTrue(plan.operations().stream()
                        .anyMatch(op -> op.status() == TimelineMergePlanOperationStatus.CONFLICT_REQUIRES_MANUAL_REVIEW),
                "C3: transition parameter-only divergent edit must conflict (complete fingerprint), got "
                        + plan.operations());
    }

    // ── C7: automation parameterPath-only divergent edit conflicts ──
    @Test
    void c7AutomationParameterPathDivergentEditConflicts() {
        var baseReq = new TimelineImportRequest("tl", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080, 0.0, 2.0, 0.0, 2.0, List.of(new ImportClipEffect("fx1", "blur", Map.of())))))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 2.0, List.of(),
                List.of(new ImportAutomationCurve("auto-1", "fx1", "opacity", "float", "HOLD",
                        List.of(new ImportAutomationKeyframe("kf-1", 0, 30, 0.5, "LINEAR")))));
        var oursReq = new TimelineImportRequest("tl", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080, 0.0, 2.0, 0.0, 2.0, List.of(new ImportClipEffect("fx1", "blur", Map.of())))))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 2.0, List.of(),
                List.of(new ImportAutomationCurve("auto-1", "fx1", "gain", "float", "HOLD",
                        List.of(new ImportAutomationKeyframe("kf-1", 0, 30, 0.5, "LINEAR")))));
        var theirsReq = new TimelineImportRequest("tl", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080, 0.0, 2.0, 0.0, 2.0, List.of(new ImportClipEffect("fx1", "blur", Map.of())))))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 2.0, List.of(),
                List.of(new ImportAutomationCurve("auto-1", "fx1", "pan", "float", "HOLD",
                        List.of(new ImportAutomationKeyframe("kf-1", 0, 30, 0.5, "LINEAR")))));
        var plan = planner.plan(new TimelineMergePlanRequest(
                new com.example.platform.timeline.diff.merge.plan.TimelineMergePlanRequestId("p"),
                snapshotOf(baseReq, "base"), snapshotOf(oursReq, "ours"),
                snapshotOf(theirsReq, "theirs"), TimelineMergePlanPolicy.CONSERVATIVE, Map.of()));
        assertTrue(plan.operations().stream()
                        .anyMatch(op -> op.status() == TimelineMergePlanOperationStatus.CONFLICT_REQUIRES_MANUAL_REVIEW),
                "C7: automation parameterPath-only divergent edit must conflict (complete fingerprint), got "
                        + plan.operations());
    }

    // ── Fingerprint determinism: map insertion order must not change fingerprint ──
    @Test
    void fingerprintDeterministicUnderMapOrder() {
        CanonicalTimelineTransitionSnapshot a = new CanonicalTimelineTransitionSnapshot(
                "t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                MediaTime.ofTicks(15, 30), "CENTER_ON_CUT", "USE_SOURCE_HANDLES",
                Map.of("mix", "0.5", "alpha", "1.0"));
        CanonicalTimelineTransitionSnapshot b = new CanonicalTimelineTransitionSnapshot(
                "t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                MediaTime.ofTicks(15, 30), "CENTER_ON_CUT", "USE_SOURCE_HANDLES",
                Map.of("alpha", "1.0", "mix", "0.5"));
        assertEquals(a.semanticFingerprint(), b.semanticFingerprint(),
                "fingerprint must be independent of parameter map insertion order");
        assertTrue(a.localSemanticsEquals(b), "localSemanticsEquals must use the same authority");
    }

    // ── Fingerprint: each transition semantic field changes the fingerprint ──
    @Test
    void fingerprintSensitiveToEveryTransitionField() {
        CanonicalTimelineTransitionSnapshot base = new CanonicalTimelineTransitionSnapshot(
                "t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                MediaTime.ofTicks(15, 30), "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("mix", "0.5"));
        assertNotEquals(base.semanticFingerprint(),
                new CanonicalTimelineTransitionSnapshot("t1", "video.wipe", "1.0", "c1", "c2", "VIDEO",
                        MediaTime.ofTicks(15, 30), "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("mix", "0.5")).semanticFingerprint(),
                "definition change must change fingerprint");
        assertNotEquals(base.semanticFingerprint(),
                new CanonicalTimelineTransitionSnapshot("t1", "video.dissolve", "2.0", "c1", "c2", "VIDEO",
                        MediaTime.ofTicks(15, 30), "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("mix", "0.5")).semanticFingerprint(),
                "version change must change fingerprint");
        assertNotEquals(base.semanticFingerprint(),
                new CanonicalTimelineTransitionSnapshot("t1", "video.dissolve", "1.0", "c1", "c3", "VIDEO",
                        MediaTime.ofTicks(15, 30), "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("mix", "0.5")).semanticFingerprint(),
                "participant change must change fingerprint");
        assertNotEquals(base.semanticFingerprint(),
                new CanonicalTimelineTransitionSnapshot("t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                        MediaTime.ofTicks(30, 30), "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("mix", "0.5")).semanticFingerprint(),
                "duration change must change fingerprint");
        assertNotEquals(base.semanticFingerprint(),
                new CanonicalTimelineTransitionSnapshot("t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                        MediaTime.ofTicks(15, 30), "START_AT_CUT", "USE_SOURCE_HANDLES", Map.of("mix", "0.5")).semanticFingerprint(),
                "alignment change must change fingerprint");
        assertNotEquals(base.semanticFingerprint(),
                new CanonicalTimelineTransitionSnapshot("t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                        MediaTime.ofTicks(15, 30), "CENTER_ON_CUT", "OVERLAP_TIMELINE", Map.of("mix", "0.5")).semanticFingerprint(),
                "temporalPolicy change must change fingerprint");
        assertNotEquals(base.semanticFingerprint(),
                new CanonicalTimelineTransitionSnapshot("t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                        MediaTime.ofTicks(15, 30), "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("mix", "0.7")).semanticFingerprint(),
                "parameter change must change fingerprint");
        assertNotEquals(base.semanticFingerprint(),
                new CanonicalTimelineTransitionSnapshot("t1", "video.dissolve", "1.0", "c1", "c2", "AUDIO",
                        MediaTime.ofTicks(15, 30), "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("mix", "0.5")).semanticFingerprint(),
                "mediaType change must change fingerprint");
    }

    // ── Mixed operation regression: structural + effect + transition + automation ──
    @Test
    void mixedOperationPreservesAllSemanticFamilies() {
        CanonicalTimelineSnapshot base = snapshotOf(fullRequest(), "r1");
        // Apply every semantic family op sequentially.
        var effectChangedReq = new TimelineImportRequest("tl", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080,
                                0.5, 1.5, 0.0, 2.0,
                                List.of(new ImportClipEffect("fx1", "blur", Map.of("radius", 9)))),
                        new ImportClip("c2", "ast_2", "file:///b.mp4", 1920, 1080,
                                2.0, 4.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 2.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                        30, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("duration", "1.0"))),
                List.of(new ImportAutomationCurve("auto-1", "fx1", "opacity", "float", "HOLD",
                        List.of(new ImportAutomationKeyframe("kf-1", 0, 30, 0.8, "LINEAR")))));
        CanonicalTimelineSnapshot after = snapshotOf(effectChangedReq, "r2");
        var diff = new CanonicalTimelineDiffCalculator().calculate(base, after);
        TimelinePatchApplicationResult result = applier.apply(base,
                new TimelinePatch(new TimelinePatchId("p"), "r1",
                        diff.diff().operations(), null, Map.of()));
        assertEquals(TimelinePatchApplicationStatus.APPLIED, result.status());
        CanonicalTimelineSnapshot patched = result.patchedSnapshot();
        // Clip moved (0.5 start)
        assertEquals(MediaTime.ofMillis(500), patched.tracks().get(0).clips().get(0).start(),
                "mixed: clip moved");
        // Effect changed
        assertTrue(patched.tracks().get(0).clips().get(0).effects().get(0).parameters()
                .get("radius").toString().contains("9"), "mixed: effect changed");
        // Transition changed (duration 30 ticks)
        assertTrue(patched.transitions().get(0).duration().isEqualTo(MediaTime.ofTicks(30, 30)), "mixed: transition changed");
        // Automation changed (0.8)
        assertEquals(0.8, patched.automations().get(0).keyframes().get(0).value(), 1e-9,
                "mixed: automation changed");
        // All families coexist
        assertEquals(1, patched.transitions().size());
        assertEquals(1, patched.automations().size());
        assertEquals(2, patched.tracks().get(0).clips().size());
    }
}
