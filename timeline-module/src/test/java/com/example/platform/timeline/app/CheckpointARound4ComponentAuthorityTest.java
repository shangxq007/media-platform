package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonicalmodel.CanonicalTransition;
import com.example.platform.timeline.diff.application.TimelinePatchApplier;
import com.example.platform.timeline.diff.application.TimelinePatchApplicationResult;
import com.example.platform.timeline.diff.application.TimelinePatchApplicationStatus;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineAutomationKeyframe;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineAutomationSnapshot;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineSnapshot;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineTransitionSnapshot;
import com.example.platform.timeline.diff.calculation.TimelineSnapshotConverter;
import com.example.platform.timeline.diff.TimelineChangeType;
import com.example.platform.timeline.diff.TimelinePatch;
import com.example.platform.timeline.diff.TimelinePatchId;
import com.example.platform.timeline.semantics.automation.AutomationCanonicalSemantics;
import com.example.platform.timeline.semantics.transition.TransitionCanonicalSemantics;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * CHECKPOINT_A Round 4 (R4-A1/A2): Transition and Automation local-authority
 * behavior tests.
 *
 * <p>Proves, through the PRODUCTION diff → patch path, that:
 * <ul>
 *   <li>canonical round-trip is lossless (delimiter-looking values survive)</li>
 *   <li>every local authored field participates in semantic equality/fingerprint</li>
 *   <li>ADD / DELETE / MODIFY / IDENTICAL are first-class diff ops</li>
 *   <li>a MODIFY/ADD op WITHOUT its complete canonical payload FAILS CLOSED
 *       in the patch applier — no synthesized defaults, no silent blanks</li>
 * </ul>
 */
class CheckpointARound4ComponentAuthorityTest {

    // ── Transition helpers ────────────────────────────────────────────────

    private static CanonicalTimelineTransitionSnapshot transition(String id, Map<String, String> params) {
        return new CanonicalTimelineTransitionSnapshot(
                id, "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                MediaTime.ofTicks(15, 30), "CENTER_ON_CUT", "USE_SOURCE_HANDLES", params);
    }

    private static CanonicalTimelineSnapshot withTransitions(CanonicalTimelineSnapshot base,
            List<CanonicalTimelineTransitionSnapshot> transitions) {
        return new CanonicalTimelineSnapshot(base.id(), base.revisionId(), base.duration(),
                base.tracks(), base.captions(), base.watermarks(), base.templateApplications(),
                base.workflowSteps(), base.outputProfile(), base.safeMetadata(), base.textElements(),
                transitions, base.automations(), base.audioMix(), base.semanticRelationships());
    }

    // ── Automation helpers ────────────────────────────────────────────────

    private static CanonicalTimelineAutomationSnapshot automation(String id, String path,
            List<CanonicalTimelineAutomationKeyframe> keyframes) {
        return new CanonicalTimelineAutomationSnapshot(
                id, "fx1", path, "float", "HOLD", keyframes);
    }

    private static CanonicalTimelineAutomationKeyframe kf(String id, long ticks, double value) {
        return new CanonicalTimelineAutomationKeyframe(id, MediaTime.ofTicks(ticks, 30), value, "LINEAR");
    }

    private static CanonicalTimelineSnapshot withAutomations(CanonicalTimelineSnapshot base,
            List<CanonicalTimelineAutomationSnapshot> automations) {
        return new CanonicalTimelineSnapshot(base.id(), base.revisionId(), base.duration(),
                base.tracks(), base.captions(), base.watermarks(), base.templateApplications(),
                base.workflowSteps(), base.outputProfile(), base.safeMetadata(), base.textElements(),
                base.transitions(), automations, base.audioMix(), base.semanticRelationships());
    }

    private static CanonicalTimelineSnapshot emptySnap(String rev) {
        return TimelineSnapshotConverter.toSnapshot(
                new com.example.platform.timeline.canonical.TimelineDocument(
                        com.example.platform.timeline.canonical.TimelineDocument.CURRENT_SCHEMA_VERSION,
                        List.of(), com.example.platform.timeline.canonical.TimelineMetadata.empty()), rev);
    }

    private static List<com.example.platform.timeline.diff.TimelineChangeOperation> diff(
            CanonicalTimelineSnapshot b, CanonicalTimelineSnapshot a) {
        return new com.example.platform.timeline.diff.calculation.CanonicalTimelineDiffCalculator()
                .calculate(b, a).diff().operations();
    }

    private static TimelinePatchApplicationResult apply(CanonicalTimelineSnapshot base,
            List<com.example.platform.timeline.diff.TimelineChangeOperation> ops) {
        return new TimelinePatchApplier().apply(base, new TimelinePatch(
                new TimelinePatchId("p"), base.revisionId(), ops, null, Map.of()));
    }

    // ══════════════════════════════════════════════════════════════════════
    // TRANSITION
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void transitionCanonicalRoundTripLossless() {
        // delimiter-looking authored values must survive untouched
        Map<String, String> params = new LinkedHashMap<>();
        params.put("a,b=c", "x=y,z");
        params.put("semi;colon", "colon:value");
        CanonicalTimelineTransitionSnapshot t = transition("t1", params);

        ObjectNode canonical = TransitionCanonicalSemantics.canonicalValue(t);
        // R5-A: decode returns the DOMAIN value CanonicalTransition; compare
        // against the snapshot converted to the domain value.
        CanonicalTransition decoded =
                TransitionCanonicalSemantics.fromCanonicalValue("t1", canonical);
        assertEquals(TransitionCanonicalSemantics.fromSnapshotValue(t), decoded,
                "canonical round-trip must be lossless");
        assertEquals("x=y,z", decoded.parameters().get("a,b=c"),
                "delimiter-looking authored values must survive");
    }

    @Test
    void transitionEveryFieldParticipatesInEquality() {
        CanonicalTimelineTransitionSnapshot base = transition("t1", Map.of("k", "v"));
        // each single-field change must break local semantic equality
        assertNotEquals(base, transition("t1", Map.of("k", "v2")), "parameters participate");
        CanonicalTimelineTransitionSnapshot diffDef = new CanonicalTimelineTransitionSnapshot(
                "t1", "video.dissolve2", "1.0", "c1", "c2", "VIDEO",
                MediaTime.ofTicks(15, 30), "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("k", "v"));
        assertNotEquals(base, diffDef, "definition participates");
        CanonicalTimelineTransitionSnapshot diffAlign = new CanonicalTimelineTransitionSnapshot(
                "t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                MediaTime.ofTicks(15, 30), "START_AT_CUT", "USE_SOURCE_HANDLES", Map.of("k", "v"));
        assertNotEquals(base, diffAlign, "alignment participates");
        CanonicalTimelineTransitionSnapshot diffPolicy = new CanonicalTimelineTransitionSnapshot(
                "t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                MediaTime.ofTicks(15, 30), "CENTER_ON_CUT", "OVERLAP_TIMELINE", Map.of("k", "v"));
        assertNotEquals(base, diffPolicy, "temporal policy participates");
        CanonicalTimelineTransitionSnapshot diffDuration = new CanonicalTimelineTransitionSnapshot(
                "t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                MediaTime.ofTicks(20, 30), "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("k", "v"));
        assertNotEquals(base, diffDuration, "duration participates");
        // fingerprints differ for any local change
        assertNotEquals(TransitionCanonicalSemantics.semanticFingerprint(base),
                TransitionCanonicalSemantics.semanticFingerprint(diffAlign),
                "fingerprint must change with any local field");
    }

    @Test
    void transitionAddDeleteModifyIdentical() {
        CanonicalTimelineSnapshot empty = emptySnap("r0");
        CanonicalTimelineSnapshot added = withTransitions(empty, List.of(transition("t1", Map.of("k", "v"))));

        List<com.example.platform.timeline.diff.TimelineChangeOperation> addOps = diff(empty, added);
        assertEquals(1, addOps.size());
        assertEquals(TimelineChangeType.TRANSITION_CHANGED, addOps.get(0).type());
        assertEquals("timeline.transitions.t1", addOps.get(0).path().value());
        TimelinePatchApplicationResult addResult = apply(empty, addOps);
        assertEquals(TimelinePatchApplicationStatus.APPLIED, addResult.status());
        assertEquals(1, addResult.patchedSnapshot().transitions().size());

        // MODIFY: change one field → single op carrying the FULL canonical payload
        CanonicalTimelineSnapshot modified = withTransitions(empty,
                List.of(transition("t1", Map.of("k", "v2"))));
        List<com.example.platform.timeline.diff.TimelineChangeOperation> modOps = diff(added, modified);
        assertEquals(1, modOps.size());
        assertEquals(TimelineChangeType.TRANSITION_CHANGED, modOps.get(0).type());
        assertTrue(modOps.get(0).safeMetadata().containsKey("transition"),
                "MODIFY must carry the complete canonical payload");
        TimelinePatchApplicationResult modResult = apply(added, modOps);
        assertEquals(TimelinePatchApplicationStatus.APPLIED, modResult.status());
        assertEquals("v2", modResult.patchedSnapshot().transitions().get(0).parameters().get("k"));

        // IDENTICAL → no ops
        assertEquals(0, diff(modified, withTransitions(empty,
                List.of(transition("t1", Map.of("k", "v2"))))).size());

        // DELETE → explicit deleted op with full before payload
        List<com.example.platform.timeline.diff.TimelineChangeOperation> delOps = diff(modified, empty);
        assertEquals(1, delOps.size());
        assertTrue("true".equals(delOps.get(0).safeMetadata().get("deleted")));
        assertTrue(delOps.get(0).safeMetadata().containsKey("transition"),
                "DELETE must carry the full canonical before payload");
        TimelinePatchApplicationResult delResult = apply(modified, delOps);
        assertEquals(TimelinePatchApplicationStatus.APPLIED, delResult.status());
        assertEquals(0, delResult.patchedSnapshot().transitions().size());
    }

    @Test
    void transitionMissingCanonicalPayloadFailsClosed() {
        // A MODIFY op whose safeMetadata lacks the complete canonical payload
        // must FAIL CLOSED — no field-by-field defaults invented.
        CanonicalTimelineSnapshot empty = emptySnap("r0");
        CanonicalTimelineSnapshot added = withTransitions(empty, List.of(transition("t1", Map.of("k", "v"))));
        var op = new com.example.platform.timeline.diff.TimelineChangeOperation(
                new com.example.platform.timeline.diff.TimelineChangeOperationId("op-1"),
                TimelineChangeType.TRANSITION_CHANGED,
                com.example.platform.timeline.diff.TimelineChangeScope.TRANSITION,
                new com.example.platform.timeline.diff.TimelineChangePath("timeline.transitions.t1"),
                com.example.platform.timeline.diff.TimelineChangePayload.ofString("fp"),
                com.example.platform.timeline.diff.TimelineChangePayload.ofString("fp2"),
                Map.of()); // no "transition" payload
        TimelinePatchApplicationResult result = apply(added, List.of(op));
        assertEquals(TimelinePatchApplicationStatus.VALIDATION_FAILED, result.status(),
                "missing canonical payload must fail closed");
    }

    // ══════════════════════════════════════════════════════════════════════
    // AUTOMATION
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void automationCanonicalRoundTripExactMediaTime() {
        List<CanonicalTimelineAutomationKeyframe> kfs = List.of(
                kf("kf-1", 0, 0.5), kf("kf-2", 15, 0.25), kf("kf-3", 30, 1.0));
        CanonicalTimelineAutomationSnapshot a = automation("auto1", "a,b=c;d", kfs);

        ObjectNode canonical = AutomationCanonicalSemantics.canonicalValue(a);
        // R5-A: decode returns the DOMAIN value CanonicalAutomationCurve;
        // compare against the snapshot converted to the domain value.
        com.example.platform.timeline.canonicalmodel.CanonicalAutomationCurve decoded =
                AutomationCanonicalSemantics.fromCanonicalValue("auto1", canonical);
        assertEquals(AutomationCanonicalSemantics.fromSnapshotValue(a), decoded,
                "automation canonical round-trip must be lossless");
        assertEquals(MediaTime.ofTicks(15, 30), decoded.keyframes().get(1).time(),
                "exact MediaTime keyframes must survive");
        assertEquals("a,b=c;d", decoded.parameterPath(),
                "separator-looking parameter paths must survive");
    }

    @Test
    void automationAddDeleteModifyIdentical() {
        CanonicalTimelineSnapshot empty = emptySnap("r0");
        CanonicalTimelineSnapshot added = withAutomations(empty,
                List.of(automation("auto1", "opacity", List.of(kf("kf-1", 0, 0.5)))));

        List<com.example.platform.timeline.diff.TimelineChangeOperation> addOps = diff(empty, added);
        assertEquals(1, addOps.size());
        assertEquals(TimelineChangeType.AUTOMATION_CHANGED, addOps.get(0).type());
        TimelinePatchApplicationResult addResult = apply(empty, addOps);
        assertEquals(TimelinePatchApplicationStatus.APPLIED, addResult.status());

        CanonicalTimelineSnapshot modified = withAutomations(empty,
                List.of(automation("auto1", "opacity",
                        List.of(kf("kf-1", 0, 0.5), kf("kf-2", 15, 0.25)))));
        List<com.example.platform.timeline.diff.TimelineChangeOperation> modOps = diff(added, modified);
        assertEquals(1, modOps.size());
        assertTrue(modOps.get(0).safeMetadata().containsKey("automation"),
                "MODIFY must carry the complete canonical payload");
        TimelinePatchApplicationResult modResult = apply(added, modOps);
        assertEquals(TimelinePatchApplicationStatus.APPLIED, modResult.status());
        assertEquals(2, modResult.patchedSnapshot().automations().get(0).keyframes().size());

        assertEquals(0, diff(modified, withAutomations(empty,
                List.of(automation("auto1", "opacity",
                        List.of(kf("kf-1", 0, 0.5), kf("kf-2", 15, 0.25)))))).size(),
                "IDENTICAL automation produces no ops");

        List<com.example.platform.timeline.diff.TimelineChangeOperation> delOps = diff(modified, empty);
        assertEquals(1, delOps.size());
        assertTrue("true".equals(delOps.get(0).safeMetadata().get("deleted")));
        assertTrue(delOps.get(0).safeMetadata().containsKey("automation"),
                "DELETE must carry the full canonical before payload");
        TimelinePatchApplicationResult delResult = apply(modified, delOps);
        assertEquals(TimelinePatchApplicationStatus.APPLIED, delResult.status());
        assertEquals(0, delResult.patchedSnapshot().automations().size());
    }

    @Test
    void automationMissingCanonicalPayloadFailsClosedNoSynthesis() {
        // A MODIFY op WITHOUT the canonical payload must FAIL CLOSED — the
        // patch applier must NOT synthesize default valueType/extrapolation/
        // empty keyframes.
        CanonicalTimelineSnapshot empty = emptySnap("r0");
        CanonicalTimelineSnapshot added = withAutomations(empty,
                List.of(automation("auto1", "opacity", List.of(kf("kf-1", 0, 0.5)))));
        var op = new com.example.platform.timeline.diff.TimelineChangeOperation(
                new com.example.platform.timeline.diff.TimelineChangeOperationId("op-1"),
                TimelineChangeType.AUTOMATION_CHANGED,
                com.example.platform.timeline.diff.TimelineChangeScope.AUTOMATION,
                new com.example.platform.timeline.diff.TimelineChangePath("timeline.automations.auto1"),
                com.example.platform.timeline.diff.TimelineChangePayload.ofString("fp"),
                com.example.platform.timeline.diff.TimelineChangePayload.ofString("fp2"),
                Map.of("targetEntityId", "fx1", "valueType", "float", "extrapolation", "HOLD"));
        TimelinePatchApplicationResult result = apply(added, List.of(op));
        assertEquals(TimelinePatchApplicationStatus.VALIDATION_FAILED, result.status(),
                "missing canonical automation payload must fail closed (no default synthesis)");
    }
}
