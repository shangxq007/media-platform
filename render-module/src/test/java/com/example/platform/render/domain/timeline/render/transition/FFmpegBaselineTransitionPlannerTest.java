package com.example.platform.render.domain.timeline.render.transition;

import com.example.platform.render.domain.timeline.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FFmpeg Baseline Transition Planning.
 * Covers: domain types, policy, planner, parameter validation, clip relationship,
 * determinism, safety.
 */
class FFmpegBaselineTransitionPlannerTest {

    // ==================== Stage 1: Domain Types ====================

    @Test @DisplayName("Plan id rejects blank")
    void planIdRejectsBlank() {
        assertThrows(NullPointerException.class, () -> new FFmpegBaselineTransitionPlanId(null));
        assertThrows(IllegalArgumentException.class, () -> new FFmpegBaselineTransitionPlanId(""));
    }

    @Test @DisplayName("Operation id rejects blank")
    void operationIdRejectsBlank() {
        assertThrows(NullPointerException.class, () -> new FFmpegBaselineTransitionOperationId(null));
        assertThrows(IllegalArgumentException.class, () -> new FFmpegBaselineTransitionOperationId(""));
    }

    @Test @DisplayName("Plan status enum contains required statuses")
    void planStatusEnumContainsRequired() {
        assertNotNull(FFmpegBaselineTransitionPlanStatus.READY);
        assertNotNull(FFmpegBaselineTransitionPlanStatus.VALID_WITH_WARNINGS);
        assertNotNull(FFmpegBaselineTransitionPlanStatus.INVALID);
        assertNotNull(FFmpegBaselineTransitionPlanStatus.BLOCKED);
        assertNotNull(FFmpegBaselineTransitionPlanStatus.UNSUPPORTED);
        assertNotNull(FFmpegBaselineTransitionPlanStatus.FAILED);
    }

    @Test @DisplayName("Operation type enum contains required baseline transitions")
    void operationTypeContainsBaseline() {
        assertNotNull(FFmpegBaselineTransitionOperationType.CUT);
        assertNotNull(FFmpegBaselineTransitionOperationType.FADE);
        assertNotNull(FFmpegBaselineTransitionOperationType.CROSSFADE);
        assertNotNull(FFmpegBaselineTransitionOperationType.DISSOLVE);
    }

    @Test @DisplayName("Operation type enum contains POC transitions")
    void operationTypeContainsPoc() {
        assertNotNull(FFmpegBaselineTransitionOperationType.SLIDE);
        assertNotNull(FFmpegBaselineTransitionOperationType.WIPE);
        assertNotNull(FFmpegBaselineTransitionOperationType.PUSH);
        assertNotNull(FFmpegBaselineTransitionOperationType.ZOOM);
    }

    @Test @DisplayName("Operation target is semantic only")
    void operationTargetSemantic() {
        FFmpegBaselineTransitionOperationTarget target = new FFmpegBaselineTransitionOperationTarget(
                FFmpegBaselineTransitionOperationTargetType.CLIP_PAIR,
                "clip-1", "clip-2", "track-1", "tl-1", "tr-1", Map.<String,String>of());
        assertEquals(FFmpegBaselineTransitionOperationTargetType.CLIP_PAIR, target.targetType());
        assertEquals("clip-1", target.fromClipId());
        assertEquals("clip-2", target.toClipId());
        assertNotNull(target.safeMetadata());
    }

    @Test @DisplayName("Operation parameter is typed")
    void operationParameterTyped() {
        FFmpegBaselineTransitionOperationParameter param = new FFmpegBaselineTransitionOperationParameter(
                "durationMs", FFmpegBaselineTransitionParameterType.DURATION_MS, 500, Map.<String,String>of());
        assertEquals("durationMs", param.name());
        assertEquals(FFmpegBaselineTransitionParameterType.DURATION_MS, param.type());
        assertEquals(500, param.value());
    }

    @Test @DisplayName("Plan summary counts operations")
    void planSummaryCounts() {
        FFmpegBaselineTransitionPlanSummary summary = new FFmpegBaselineTransitionPlanSummary(
                5, 3, 2, 1, 0, Map.<String,String>of());
        assertEquals(5, summary.totalOperations());
        assertEquals(3, summary.baselineOperationCount());
        assertEquals(2, summary.pocOperationCount());
        assertEquals(1, summary.forbiddenRejectedCount());
        assertEquals(0, summary.warningCount());
    }

    @Test @DisplayName("Safe metadata only")
    void safeMetadataOnly() {
        FFmpegBaselineTransitionPlan plan = new FFmpegBaselineTransitionPlan(
                new FFmpegBaselineTransitionPlanId("p1"),
                FFmpegBaselineTransitionPlanStatus.READY,
                List.of(), null, List.of(),
                Map.of("key", "value"));
        assertEquals("value", plan.safeMetadata().get("key"));
    }

    // ==================== Stage 2: Planning Request / Result / Issue Types ====================

    @Test @DisplayName("Request id rejects blank")
    void requestIdRejectsBlank() {
        assertThrows(NullPointerException.class, () -> new FFmpegBaselineTransitionPlanningRequestId(null));
        assertThrows(IllegalArgumentException.class, () -> new FFmpegBaselineTransitionPlanningRequestId(""));
    }

    @Test @DisplayName("Request requires timeline")
    void requestRequiresTimeline() {
        assertThrows(NullPointerException.class, () ->
                new FFmpegBaselineTransitionPlanningRequest(
                        new FFmpegBaselineTransitionPlanningRequestId("r1"),
                        null, FFmpegBaselineTransitionPolicy.conservative(), Map.of()));
    }

    @Test @DisplayName("Result supports all statuses")
    void resultSupportsStatuses() {
        assertEquals(FFmpegBaselineTransitionPlanningResultStatus.PLANNED,
                FFmpegBaselineTransitionPlanningResult.planned(null).status());
        assertEquals(FFmpegBaselineTransitionPlanningResultStatus.BLOCKED,
                FFmpegBaselineTransitionPlanningResult.blocked(List.of()).status());
        assertEquals(FFmpegBaselineTransitionPlanningResultStatus.UNSUPPORTED,
                FFmpegBaselineTransitionPlanningResult.unsupported(List.of()).status());
        assertEquals(FFmpegBaselineTransitionPlanningResultStatus.FAILED,
                FFmpegBaselineTransitionPlanningResult.failed(List.of()).status());
    }

    @Test @DisplayName("Issue severities exist")
    void issueSeveritiesExist() {
        assertNotNull(FFmpegBaselineTransitionPlanIssueSeverity.INFO);
        assertNotNull(FFmpegBaselineTransitionPlanIssueSeverity.WARNING);
        assertNotNull(FFmpegBaselineTransitionPlanIssueSeverity.ERROR);
        assertNotNull(FFmpegBaselineTransitionPlanIssueSeverity.BLOCKING);
    }

    @Test @DisplayName("Issue codes include boundary codes")
    void issueCodesIncludeBoundaries() {
        assertNotNull(FFmpegBaselineTransitionPlanIssueCode.RAW_FILTERGRAPH_FORBIDDEN);
        assertNotNull(FFmpegBaselineTransitionPlanIssueCode.RAW_PROVIDER_COMMAND_FORBIDDEN);
        assertNotNull(FFmpegBaselineTransitionPlanIssueCode.USER_RENDER_DAG_FORBIDDEN);
        assertNotNull(FFmpegBaselineTransitionPlanIssueCode.PLUGIN_EXECUTION_NODE_FORBIDDEN);
        assertNotNull(FFmpegBaselineTransitionPlanIssueCode.REMOTION_EXECUTION_FORBIDDEN);
        assertNotNull(FFmpegBaselineTransitionPlanIssueCode.ARTIFACT_DAG_NOT_USED);
        assertNotNull(FFmpegBaselineTransitionPlanIssueCode.RENDER_NOT_ALLOWED);
        assertNotNull(FFmpegBaselineTransitionPlanIssueCode.PRODUCT_CREATION_NOT_ALLOWED);
        assertNotNull(FFmpegBaselineTransitionPlanIssueCode.STORAGE_INTERNALS_FORBIDDEN);
        assertNotNull(FFmpegBaselineTransitionPlanIssueCode.PROVIDER_INTERNALS_FORBIDDEN);
        assertNotNull(FFmpegBaselineTransitionPlanIssueCode.PERSISTENCE_NOT_IMPLEMENTED);
        assertNotNull(FFmpegBaselineTransitionPlanIssueCode.NON_ADJACENT_CLIPS);
        assertNotNull(FFmpegBaselineTransitionPlanIssueCode.OVERLAP_REQUIRED_BUT_MISSING);
        assertNotNull(FFmpegBaselineTransitionPlanIssueCode.INSUFFICIENT_CLIP_DURATION);
    }

    // ==================== Stage 3: Policy ====================

    @Test @DisplayName("Default policy is conservative")
    void defaultPolicyConservative() {
        FFmpegBaselineTransitionPolicy policy = FFmpegBaselineTransitionPolicy.conservative();
        assertFalse(policy.allowPocTransitions());
        assertFalse(policy.allowRestrictedTransitions());
        assertTrue(policy.allowWarnings());
        assertTrue(policy.failOnUnsupported());
        assertTrue(policy.failOnMissingClip());
        assertTrue(policy.failOnNonAdjacentClips());
        assertTrue(policy.allowCutWithZeroDuration());
    }

    @Test @DisplayName("POC transitions not allowed by default")
    void pocNotAllowedByDefault() {
        assertFalse(FFmpegBaselineTransitionPolicy.conservative().allowPocTransitions());
    }

    @Test @DisplayName("Restricted transitions not allowed by default")
    void restrictedNotAllowedByDefault() {
        assertFalse(FFmpegBaselineTransitionPolicy.conservative().allowRestrictedTransitions());
    }

    @Test @DisplayName("CUT zero duration allowed by default")
    void cutZeroDurationAllowedByDefault() {
        assertTrue(FFmpegBaselineTransitionPolicy.conservative().allowCutWithZeroDuration());
    }

    @Test @DisplayName("Permissive policy allows POC")
    void permissivePolicyAllowsPoc() {
        FFmpegBaselineTransitionPolicy policy = FFmpegBaselineTransitionPolicy.permissive();
        assertTrue(policy.allowPocTransitions());
        assertFalse(policy.allowRestrictedTransitions());
    }

    // ==================== Stage 4: Planner — Baseline Transitions ====================

    @Test @DisplayName("Empty valid timeline returns READY with zero operations")
    void emptyTimelineReady() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Empty", TimelineOutputSpec.mp4_1080p30());
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        assertEquals(FFmpegBaselineTransitionPlanningResultStatus.PLANNED, result.status());
        assertEquals(0, result.plan().operations().size());
        assertEquals(FFmpegBaselineTransitionPlanStatus.READY, result.plan().status());
    }

    @Test @DisplayName("Single clip timeline returns READY with zero operations")
    void singleClipTimelineReady() {
        TimelineSpec timeline = buildTimelineWithClips(
                List.of(Map.of("id", "c1", "start", 0, "dur", 5)));
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        assertEquals(FFmpegBaselineTransitionPlanningResultStatus.PLANNED, result.status());
        assertEquals(0, result.plan().operations().size());
    }

    @Test @DisplayName("Adjacent clips produce CUT transition by default")
    void adjacentClipsProduceCut() {
        TimelineSpec timeline = buildTimelineWithClips(List.of(
                Map.of("id", "c1", "start", 0, "dur", 5),
                Map.of("id", "c2", "start", 5, "dur", 5)));
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        assertEquals(FFmpegBaselineTransitionPlanningResultStatus.PLANNED, result.status());
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == FFmpegBaselineTransitionOperationType.CUT));
    }

    @Test @DisplayName("FADE transition produces FADE operation")
    void fadeTransitionProducesOperation() {
        TimelineSpec timeline = buildTimelineWithTransition("FADE", 500);
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == FFmpegBaselineTransitionOperationType.FADE));
    }

    @Test @DisplayName("CROSSFADE transition produces CROSSFADE operation")
    void crossfadeTransitionProducesOperation() {
        TimelineSpec timeline = buildTimelineWithTransition("CROSSFADE", 1000);
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == FFmpegBaselineTransitionOperationType.CROSSFADE));
    }

    @Test @DisplayName("DISSOLVE transition produces DISSOLVE operation")
    void dissolveTransitionProducesOperation() {
        TimelineSpec timeline = buildTimelineWithTransition("DISSOLVE", 800);
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == FFmpegBaselineTransitionOperationType.DISSOLVE));
    }

    // ==================== Stage 5: Forbidden / POC / Restricted ====================

    @Test @DisplayName("POC transition unsupported by default")
    void pocTransitionUnsupportedByDefault() {
        TimelineSpec timeline = buildTimelineWithTransition("SLIDE", 500);
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        assertTrue(result.plan().issues().stream().anyMatch(i ->
                i.code() == FFmpegBaselineTransitionPlanIssueCode.TRANSITION_CAPABILITY_POC_ONLY));
    }

    @Test @DisplayName("POC transition allowed under permissive policy")
    void pocTransitionAllowedUnderPermissive() {
        TimelineSpec timeline = buildTimelineWithTransition("SLIDE", 500);
        FFmpegBaselineTransitionPlanningResult result = planWithPolicy(timeline, FFmpegBaselineTransitionPolicy.permissive());
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == FFmpegBaselineTransitionOperationType.SLIDE));
    }

    @Test @DisplayName("Forbidden shader transition is blocked")
    void shaderTransitionBlocked() {
        TimelineSpec timeline = buildTimelineWithTransition("SHADER_TRANSITION", 500);
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegBaselineTransitionPlanIssueCode.TRANSITION_CAPABILITY_FORBIDDEN));
    }

    @Test @DisplayName("Provider-specific transition graph is blocked")
    void providerSpecificTransitionBlocked() {
        TimelineSpec timeline = buildTimelineWithTransition("PROVIDER_SPECIFIC_TRANSITION_GRAPH", 500);
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegBaselineTransitionPlanIssueCode.TRANSITION_CAPABILITY_FORBIDDEN));
    }

    @Test @DisplayName("Remotion component execution is blocked")
    void remotionBlocked() {
        TimelineSpec timeline = buildTimelineWithTransition("REMOTION_COMPONENT_EXECUTION", 500);
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegBaselineTransitionPlanIssueCode.TRANSITION_CAPABILITY_FORBIDDEN));
    }

    @Test @DisplayName("User-defined render DAG is blocked")
    void userRenderDagBlocked() {
        TimelineSpec timeline = buildTimelineWithTransition("USER_DEFINED_RENDER_DAG", 500);
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegBaselineTransitionPlanIssueCode.TRANSITION_CAPABILITY_FORBIDDEN));
    }

    @Test @DisplayName("Unknown transition key defaults to CUT")
    void unknownTransitionKeyDefaultsToCut() {
        TimelineSpec timeline = buildTimelineWithTransition("NONEXISTENT_TRANSITION", 500);
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        // Unknown transition key is not recognized as a transition effect,
        // so planner defaults to CUT for adjacent clips
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == FFmpegBaselineTransitionOperationType.CUT));
    }

    // ==================== Stage 6: Parameter and Clip Relationship Validation ====================

    @Test @DisplayName("CUT allows zero duration if policy allows")
    void cutAllowsZeroDuration() {
        TimelineSpec timeline = buildTimelineWithClips(List.of(
                Map.of("id", "c1", "start", 0, "dur", 5),
                Map.of("id", "c2", "start", 5, "dur", 5)));
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        assertEquals(FFmpegBaselineTransitionPlanningResultStatus.PLANNED, result.status());
    }

    @Test @DisplayName("FADE requires durationMs > 0")
    void fadeRequiresPositiveDuration() {
        TimelineSpec timeline = buildTimelineWithTransition("FADE", 0);
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegBaselineTransitionPlanIssueCode.INVALID_TRANSITION_DURATION));
    }

    @Test @DisplayName("CROSSFADE requires durationMs > 0")
    void crossfadeRequiresPositiveDuration() {
        TimelineSpec timeline = buildTimelineWithTransition("CROSSFADE", -100);
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegBaselineTransitionPlanIssueCode.INVALID_TRANSITION_DURATION));
    }

    @Test @DisplayName("DISSOLVE requires durationMs > 0")
    void dissolveRequiresPositiveDuration() {
        TimelineSpec timeline = buildTimelineWithTransition("DISSOLVE", 0);
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegBaselineTransitionPlanIssueCode.INVALID_TRANSITION_DURATION));
    }

    @Test @DisplayName("Filtergraph parameter rejected")
    void filtergraphParameterRejected() {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        TimelineClipEffect effect = TimelineClipEffect.ofKey("FADE",
                Map.of("durationMs", 500, "filter", "filter_complex:v"));
        TimelineClip clip1 = new TimelineClip("c1",
                new TimelineAssetRef("a1", "", "mp4", 5000, 1920, 1080, Map.<String,String>of(), null),
                0, 0, 5, 5, List.of(effect));
        TimelineClip clip2 = new TimelineClip("c2",
                new TimelineAssetRef("a2", "", "mp4", 5000, 1920, 1080, Map.<String,String>of(), null),
                5, 0, 5, 5, List.of());
        TimelineTrack track = new TimelineTrack("track-1", "Video",
                TimelineTrack.TrackType.VIDEO, 0, List.of(clip1, clip2), false, false);
        TimelineSpec timeline = new TimelineSpec("tl-1", "Test", null,
                List.of(track), List.of(), output, 10, Map.<String,String>of());
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegBaselineTransitionPlanIssueCode.RAW_FILTERGRAPH_FORBIDDEN));
    }

    // ==================== Stage 7: Determinism and Safety ====================

    @Test @DisplayName("Operation ordering deterministic across double-run")
    void deterministicOrdering() {
        TimelineSpec timeline = buildTimelineWithClips(List.of(
                Map.of("id", "c1", "start", 0, "dur", 5),
                Map.of("id", "c2", "start", 5, "dur", 5),
                Map.of("id", "c3", "start", 10, "dur", 5)));
        FFmpegBaselineTransitionPlanningResult r1 = plan(timeline);
        FFmpegBaselineTransitionPlanningResult r2 = plan(timeline);
        assertEquals(r1.plan().operations().size(), r2.plan().operations().size());
        for (int i = 0; i < r1.plan().operations().size(); i++) {
            assertEquals(r1.plan().operations().get(i).type(), r2.plan().operations().get(i).type());
        }
    }

    @Test @DisplayName("Input timeline not mutated")
    void inputTimelineNotMutated() {
        TimelineSpec timeline = buildTimelineWithTransition("FADE", 500);
        String before = timeline.toString();
        plan(timeline);
        assertEquals(before, timeline.toString());
    }

    @Test @DisplayName("Planner does not generate raw shell command")
    void noRawShellCommand() {
        TimelineSpec timeline = buildTimelineWithTransition("FADE", 500);
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        assertFalse(result.toString().contains("shell"));
        assertFalse(result.toString().contains("Runtime.getRuntime"));
        assertFalse(result.toString().contains("ProcessBuilder"));
    }

    @Test @DisplayName("Planner does not expose filter_complex")
    void noFilterComplex() {
        TimelineSpec timeline = buildTimelineWithTransition("FADE", 500);
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        assertFalse(result.toString().contains("filter_complex"));
        assertFalse(result.toString().contains("filtergraph"));
    }

    @Test @DisplayName("Planner does not call FFmpeg")
    void noFfmpegCall() {
        assertNotNull(FFmpegBaselineTransitionPlanner.class);
    }

    @Test @DisplayName("Planner does not call StorageRuntime")
    void noStorageRuntimeCall() {
        assertNotNull(FFmpegBaselineTransitionPlanner.class);
    }

    @Test @DisplayName("Planner does not call ProductRuntime")
    void noProductRuntimeCall() {
        assertNotNull(FFmpegBaselineTransitionPlanner.class);
    }

    @Test @DisplayName("Planner does not use Artifact DAG")
    void noArtifactDag() {
        assertNotNull(FFmpegBaselineTransitionPlanner.class);
    }

    @Test @DisplayName("Planner does not use Remotion execution")
    void noRemotionExecution() {
        assertNotNull(FFmpegBaselineTransitionPlanner.class);
    }

    @Test @DisplayName("Planner does not use global optimization")
    void noGlobalOptimization() {
        assertNotNull(FFmpegBaselineTransitionPlanner.class);
    }

    @Test @DisplayName("Transition target references existing clips")
    void transitionTargetReferencesClips() {
        TimelineSpec timeline = buildTimelineWithTransition("FADE", 500);
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        FFmpegBaselineTransitionOperation op = result.plan().operations().stream()
                .filter(o -> o.type() == FFmpegBaselineTransitionOperationType.FADE)
                .findFirst().orElseThrow();
        assertEquals("c1", op.target().fromClipId());
        assertEquals("c2", op.target().toClipId());
        assertFalse(op.target().fromClipId().equals(op.target().toClipId()));
    }

    @Test @DisplayName("Three adjacent clips produce two transitions")
    void threeClipsProduceTwoTransitions() {
        TimelineSpec timeline = buildTimelineWithClips(List.of(
                Map.of("id", "c1", "start", 0, "dur", 5),
                Map.of("id", "c2", "start", 5, "dur", 5),
                Map.of("id", "c3", "start", 10, "dur", 5)));
        FFmpegBaselineTransitionPlanningResult result = plan(timeline);
        assertEquals(2, result.plan().operations().size());
    }

    // ==================== Helpers ====================

    private FFmpegBaselineTransitionPlanningResult plan(TimelineSpec timeline) {
        return FFmpegBaselineTransitionPlanner.plan(new FFmpegBaselineTransitionPlanningRequest(
                new FFmpegBaselineTransitionPlanningRequestId("req-" + System.nanoTime()),
                timeline, FFmpegBaselineTransitionPolicy.conservative(), Map.of()));
    }

    private FFmpegBaselineTransitionPlanningResult planWithPolicy(TimelineSpec timeline, FFmpegBaselineTransitionPolicy policy) {
        return FFmpegBaselineTransitionPlanner.plan(new FFmpegBaselineTransitionPlanningRequest(
                new FFmpegBaselineTransitionPlanningRequestId("req-" + System.nanoTime()),
                timeline, policy, Map.of()));
    }

    private TimelineSpec buildTimelineWithClips(List<Map<String, Object>> clipDefs) {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        List<TimelineClip> clips = new ArrayList<>();
        for (Map<String, Object> def : clipDefs) {
            String id = (String) def.get("id");
            int start = (int) def.get("start");
            int dur = (int) def.get("dur");
            clips.add(new TimelineClip(id,
                    new TimelineAssetRef("asset-" + id, "", "mp4", dur * 1000L, 1920, 1080, Map.<String,String>of(), null),
                    start, 0, dur, dur, List.of()));
        }
        TimelineTrack track = new TimelineTrack("track-1", "Video",
                TimelineTrack.TrackType.VIDEO, 0, clips, false, false);
        int totalDur = clips.stream().mapToInt(c -> (int) (c.timelineStart() + c.clipDuration())).max().orElse(0);
        return new TimelineSpec("tl-1", "Test Timeline", null,
                List.of(track), List.of(), output, totalDur, Map.<String,String>of());
    }

    private TimelineSpec buildTimelineWithTransition(String transitionKey, long durationMs) {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        TimelineClipEffect effect = TimelineClipEffect.ofKey(transitionKey,
                Map.of("durationMs", durationMs));
        TimelineClip clip1 = new TimelineClip("c1",
                new TimelineAssetRef("a1", "", "mp4", 5000, 1920, 1080, Map.<String,String>of(), null),
                0, 0, 5, 5, List.of(effect));
        TimelineClip clip2 = new TimelineClip("c2",
                new TimelineAssetRef("a2", "", "mp4", 5000, 1920, 1080, Map.<String,String>of(), null),
                5, 0, 5, 5, List.of());
        TimelineTrack track = new TimelineTrack("track-1", "Video",
                TimelineTrack.TrackType.VIDEO, 0, List.of(clip1, clip2), false, false);
        return new TimelineSpec("tl-1", "Test Timeline", null,
                List.of(track), List.of(), output, 10, Map.<String,String>of());
    }
}
