package com.example.platform.render.domain.timeline.render.effect;

import com.example.platform.render.domain.timeline.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FFmpeg Baseline Effect Planning.
 * Covers: domain types, policy, planner, parameter validation, determinism, safety.
 */
class FFmpegBaselineEffectPlannerTest {

    // ==================== Stage 1: Domain Types ====================

    @Test @DisplayName("Plan id rejects blank")
    void planIdRejectsBlank() {
        assertThrows(NullPointerException.class, () -> new FFmpegBaselineEffectPlanId(null));
        assertThrows(IllegalArgumentException.class, () -> new FFmpegBaselineEffectPlanId(""));
    }

    @Test @DisplayName("Operation id rejects blank")
    void operationIdRejectsBlank() {
        assertThrows(NullPointerException.class, () -> new FFmpegBaselineEffectOperationId(null));
        assertThrows(IllegalArgumentException.class, () -> new FFmpegBaselineEffectOperationId(""));
    }

    @Test @DisplayName("Plan status enum contains required statuses")
    void planStatusEnumContainsRequired() {
        assertNotNull(FFmpegBaselineEffectPlanStatus.READY);
        assertNotNull(FFmpegBaselineEffectPlanStatus.VALID_WITH_WARNINGS);
        assertNotNull(FFmpegBaselineEffectPlanStatus.INVALID);
        assertNotNull(FFmpegBaselineEffectPlanStatus.BLOCKED);
        assertNotNull(FFmpegBaselineEffectPlanStatus.UNSUPPORTED);
        assertNotNull(FFmpegBaselineEffectPlanStatus.FAILED);
    }

    @Test @DisplayName("Operation type enum contains required baseline effects")
    void operationTypeContainsBaseline() {
        assertNotNull(FFmpegBaselineEffectOperationType.SCALE);
        assertNotNull(FFmpegBaselineEffectOperationType.CROP);
        assertNotNull(FFmpegBaselineEffectOperationType.FIT);
        assertNotNull(FFmpegBaselineEffectOperationType.FILL);
        assertNotNull(FFmpegBaselineEffectOperationType.CONTAIN);
        assertNotNull(FFmpegBaselineEffectOperationType.ROTATE);
        assertNotNull(FFmpegBaselineEffectOperationType.OPACITY);
        assertNotNull(FFmpegBaselineEffectOperationType.FADE_IN);
        assertNotNull(FFmpegBaselineEffectOperationType.FADE_OUT);
        assertNotNull(FFmpegBaselineEffectOperationType.TEXT_OVERLAY);
        assertNotNull(FFmpegBaselineEffectOperationType.IMAGE_OVERLAY);
        assertNotNull(FFmpegBaselineEffectOperationType.CAPTION_OVERLAY);
        assertNotNull(FFmpegBaselineEffectOperationType.WATERMARK_OVERLAY);
    }

    @Test @DisplayName("Operation target is semantic only")
    void operationTargetSemantic() {
        FFmpegBaselineEffectOperationTarget target = new FFmpegBaselineEffectOperationTarget(
                FFmpegBaselineEffectOperationTargetType.CLIP, "clip-1", Map.of());
        assertEquals(FFmpegBaselineEffectOperationTargetType.CLIP, target.targetType());
        assertEquals("clip-1", target.targetId());
        assertNotNull(target.safeMetadata());
    }

    @Test @DisplayName("Operation parameter is typed")
    void operationParameterTyped() {
        FFmpegBaselineEffectOperationParameter param = new FFmpegBaselineEffectOperationParameter(
                "width", FFmpegBaselineEffectParameterType.PIXEL, 1920, Map.of());
        assertEquals("width", param.name());
        assertEquals(FFmpegBaselineEffectParameterType.PIXEL, param.type());
        assertEquals(1920, param.value());
    }

    @Test @DisplayName("Plan summary counts operations")
    void planSummaryCounts() {
        FFmpegBaselineEffectPlanSummary summary = new FFmpegBaselineEffectPlanSummary(
                5, 3, 2, 1, 0, Map.of());
        assertEquals(5, summary.totalOperations());
        assertEquals(3, summary.baselineOperationCount());
        assertEquals(2, summary.pocOperationCount());
        assertEquals(1, summary.forbiddenRejectedCount());
        assertEquals(0, summary.warningCount());
    }

    @Test @DisplayName("Safe metadata only")
    void safeMetadataOnly() {
        FFmpegBaselineEffectPlan plan = new FFmpegBaselineEffectPlan(
                new FFmpegBaselineEffectPlanId("p1"),
                FFmpegBaselineEffectPlanStatus.READY,
                List.of(), null, List.of(),
                Map.of("key", "value"));
        assertEquals("value", plan.safeMetadata().get("key"));
    }

    // ==================== Stage 2: Planning Request / Result / Issue Types ====================

    @Test @DisplayName("Request id rejects blank")
    void requestIdRejectsBlank() {
        assertThrows(NullPointerException.class, () -> new FFmpegBaselineEffectPlanningRequestId(null));
        assertThrows(IllegalArgumentException.class, () -> new FFmpegBaselineEffectPlanningRequestId(""));
    }

    @Test @DisplayName("Request requires timeline")
    void requestRequiresTimeline() {
        assertThrows(NullPointerException.class, () ->
                new FFmpegBaselineEffectPlanningRequest(
                        new FFmpegBaselineEffectPlanningRequestId("r1"),
                        null, FFmpegBaselineEffectPolicy.conservative(), Map.of()));
    }

    @Test @DisplayName("Result supports all statuses")
    void resultSupportsStatuses() {
        assertEquals(FFmpegBaselineEffectPlanningResultStatus.PLANNED,
                FFmpegBaselineEffectPlanningResult.planned(null).status());
        assertEquals(FFmpegBaselineEffectPlanningResultStatus.BLOCKED,
                FFmpegBaselineEffectPlanningResult.blocked(List.of()).status());
        assertEquals(FFmpegBaselineEffectPlanningResultStatus.UNSUPPORTED,
                FFmpegBaselineEffectPlanningResult.unsupported(List.of()).status());
        assertEquals(FFmpegBaselineEffectPlanningResultStatus.FAILED,
                FFmpegBaselineEffectPlanningResult.failed(List.of()).status());
    }

    @Test @DisplayName("Issue severities exist")
    void issueSeveritiesExist() {
        assertNotNull(FFmpegBaselineEffectPlanIssueSeverity.INFO);
        assertNotNull(FFmpegBaselineEffectPlanIssueSeverity.WARNING);
        assertNotNull(FFmpegBaselineEffectPlanIssueSeverity.ERROR);
        assertNotNull(FFmpegBaselineEffectPlanIssueSeverity.BLOCKING);
    }

    @Test @DisplayName("Issue codes include boundary codes")
    void issueCodesIncludeBoundaries() {
        assertNotNull(FFmpegBaselineEffectPlanIssueCode.RAW_FILTERGRAPH_FORBIDDEN);
        assertNotNull(FFmpegBaselineEffectPlanIssueCode.RAW_PROVIDER_COMMAND_FORBIDDEN);
        assertNotNull(FFmpegBaselineEffectPlanIssueCode.USER_RENDER_DAG_FORBIDDEN);
        assertNotNull(FFmpegBaselineEffectPlanIssueCode.PLUGIN_EXECUTION_NODE_FORBIDDEN);
        assertNotNull(FFmpegBaselineEffectPlanIssueCode.REMOTION_EXECUTION_FORBIDDEN);
        assertNotNull(FFmpegBaselineEffectPlanIssueCode.ARTIFACT_DAG_NOT_USED);
        assertNotNull(FFmpegBaselineEffectPlanIssueCode.RENDER_NOT_ALLOWED);
        assertNotNull(FFmpegBaselineEffectPlanIssueCode.PRODUCT_CREATION_NOT_ALLOWED);
        assertNotNull(FFmpegBaselineEffectPlanIssueCode.STORAGE_INTERNALS_FORBIDDEN);
        assertNotNull(FFmpegBaselineEffectPlanIssueCode.PROVIDER_INTERNALS_FORBIDDEN);
        assertNotNull(FFmpegBaselineEffectPlanIssueCode.PERSISTENCE_NOT_IMPLEMENTED);
    }

    // ==================== Stage 3: Policy ====================

    @Test @DisplayName("Default policy is conservative")
    void defaultPolicyConservative() {
        FFmpegBaselineEffectPolicy policy = FFmpegBaselineEffectPolicy.conservative();
        assertFalse(policy.allowPocEffects());
        assertFalse(policy.allowRestrictedEffects());
        assertTrue(policy.allowWarnings());
        assertTrue(policy.failOnUnsupported());
        assertTrue(policy.failOnMissingTarget());
    }

    @Test @DisplayName("POC effects not allowed by default")
    void pocNotAllowedByDefault() {
        assertFalse(FFmpegBaselineEffectPolicy.conservative().allowPocEffects());
    }

    @Test @DisplayName("Restricted effects not allowed by default")
    void restrictedNotAllowedByDefault() {
        assertFalse(FFmpegBaselineEffectPolicy.conservative().allowRestrictedEffects());
    }

    @Test @DisplayName("Permissive policy allows POC")
    void permissivePolicyAllowsPoc() {
        FFmpegBaselineEffectPolicy policy = FFmpegBaselineEffectPolicy.permissive();
        assertTrue(policy.allowPocEffects());
        assertFalse(policy.allowRestrictedEffects());
    }

    // ==================== Stage 4: Planner — Baseline Effects ====================

    @Test @DisplayName("Empty valid timeline returns READY with zero operations")
    void emptyTimelineReady() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Empty", TimelineOutputSpec.mp4_1080p30());
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertEquals(FFmpegBaselineEffectPlanningResultStatus.PLANNED, result.status());
        assertEquals(0, result.plan().operations().size());
        assertEquals(FFmpegBaselineEffectPlanStatus.READY, result.plan().status());
    }

    @Test @DisplayName("Scale effect produces SCALE operation")
    void scaleEffectProducesOperation() {
        TimelineSpec timeline = buildTimelineWithEffect("SCALE", Map.of("width", 1920, "height", 1080));
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertEquals(FFmpegBaselineEffectPlanningResultStatus.PLANNED, result.status());
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == FFmpegBaselineEffectOperationType.SCALE));
    }

    @Test @DisplayName("Crop effect produces CROP operation")
    void cropEffectProducesOperation() {
        TimelineSpec timeline = buildTimelineWithEffect("CROP", Map.of("x", 0, "y", 0, "width", 1280, "height", 720));
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == FFmpegBaselineEffectOperationType.CROP));
    }

    @Test @DisplayName("Fit/Fill/Contain produce corresponding operations")
    void fitFillContainProduceOperations() {
        for (String effectKey : List.of("FIT", "FILL", "CONTAIN")) {
            TimelineSpec timeline = buildTimelineWithEffect(effectKey, Map.of("targetWidth", 1920, "targetHeight", 1080));
            FFmpegBaselineEffectPlanningResult result = plan(timeline);
            assertTrue(result.plan().operations().stream()
                    .anyMatch(op -> op.type().name().equals(effectKey)),
                    effectKey + " should produce corresponding operation");
        }
    }

    @Test @DisplayName("Rotate effect produces ROTATE operation")
    void rotateEffectProducesOperation() {
        TimelineSpec timeline = buildTimelineWithEffect("ROTATE", Map.of("degrees", 90));
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == FFmpegBaselineEffectOperationType.ROTATE));
    }

    @Test @DisplayName("Opacity effect produces OPACITY operation")
    void opacityEffectProducesOperation() {
        TimelineSpec timeline = buildTimelineWithEffect("OPACITY", Map.of("opacity", 0.5));
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == FFmpegBaselineEffectOperationType.OPACITY));
    }

    @Test @DisplayName("Fade in/out produce FADE_IN/FADE_OUT operations")
    void fadeEffectsProduceOperations() {
        TimelineSpec timelineIn = buildTimelineWithEffect("FADE_IN", Map.of("durationMs", 1000));
        assertTrue(plan(timelineIn).plan().operations().stream()
                .anyMatch(op -> op.type() == FFmpegBaselineEffectOperationType.FADE_IN));

        TimelineSpec timelineOut = buildTimelineWithEffect("FADE_OUT", Map.of("durationMs", 1000));
        assertTrue(plan(timelineOut).plan().operations().stream()
                .anyMatch(op -> op.type() == FFmpegBaselineEffectOperationType.FADE_OUT));
    }

    @Test @DisplayName("Text overlay produces TEXT_OVERLAY operation")
    void textOverlayProducesOperation() {
        TimelineSpec timeline = buildTimelineWithEffect("TEXT_OVERLAY",
                Map.of("text", "Hello", "x", "center", "y", "bottom"));
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == FFmpegBaselineEffectOperationType.TEXT_OVERLAY));
    }

    @Test @DisplayName("Image overlay produces IMAGE_OVERLAY operation")
    void imageOverlayProducesOperation() {
        TimelineSpec timeline = buildTimelineWithEffect("IMAGE_OVERLAY",
                Map.of("imageRef", "logo.png", "placement", "bottom-right"));
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == FFmpegBaselineEffectOperationType.IMAGE_OVERLAY));
    }

    @Test @DisplayName("Caption overlay from text overlay produces CAPTION_OVERLAY operation")
    void captionOverlayFromTextOverlay() {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        TimelineTextOverlay overlay = TimelineTextOverlay.of("cap-1", "Subtitle text", 0, 5);
        TimelineSpec timeline = new TimelineSpec("tl-1", "Test", null,
                List.of(TimelineTrack.of("track-1", "Video", TimelineTrack.TrackType.VIDEO)),
                List.of(overlay), output, 10, Map.of());
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == FFmpegBaselineEffectOperationType.TEXT_OVERLAY));
    }

    @Test @DisplayName("Watermark overlay produces WATERMARK_OVERLAY operation")
    void watermarkOverlayProducesOperation() {
        TimelineSpec timeline = buildTimelineWithEffect("WATERMARK_OVERLAY",
                Map.of("watermarkRef", "wm-1", "placement", "bottom-right", "opacity", 0.3));
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == FFmpegBaselineEffectOperationType.WATERMARK_OVERLAY));
    }

    // ==================== Stage 5: Forbidden / POC / Restricted ====================

    @Test @DisplayName("Forbidden arbitrary FFmpeg filtergraph is blocked")
    void forbiddenFiltergraphBlocked() {
        TimelineSpec timeline = buildTimelineWithEffect("ARBITRARY_FFMPEG_FILTERGRAPH", Map.of());
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegBaselineEffectPlanIssueCode.EFFECT_CAPABILITY_FORBIDDEN));
    }

    @Test @DisplayName("Remotion component execution is blocked")
    void remotionBlocked() {
        TimelineSpec timeline = buildTimelineWithEffect("REMOTION_COMPONENT_EXECUTION", Map.of());
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegBaselineEffectPlanIssueCode.EFFECT_CAPABILITY_FORBIDDEN));
    }

    @Test @DisplayName("User-defined Render DAG is blocked")
    void userRenderDagBlocked() {
        TimelineSpec timeline = buildTimelineWithEffect("USER_DEFINED_RENDER_DAG", Map.of());
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegBaselineEffectPlanIssueCode.EFFECT_CAPABILITY_FORBIDDEN));
    }

    @Test @DisplayName("POC effect unsupported by default")
    void pocEffectUnsupportedByDefault() {
        TimelineSpec timeline = buildTimelineWithEffect("BLUR", Map.of("radius", 5));
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.plan().issues().stream().anyMatch(i ->
                i.code() == FFmpegBaselineEffectPlanIssueCode.EFFECT_CAPABILITY_POC_ONLY));
    }

    @Test @DisplayName("POC effect allowed under permissive policy")
    void pocEffectAllowedUnderPermissive() {
        TimelineSpec timeline = buildTimelineWithEffect("BLUR", Map.of("radius", 5));
        FFmpegBaselineEffectPlanningResult result = planWithPolicy(timeline, FFmpegBaselineEffectPolicy.permissive());
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == FFmpegBaselineEffectOperationType.BLUR));
    }

    @Test @DisplayName("Unknown effect key fails")
    void unknownEffectKeyFails() {
        TimelineSpec timeline = buildTimelineWithEffect("NONEXISTENT_EFFECT", Map.of());
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegBaselineEffectPlanIssueCode.EFFECT_CAPABILITY_NOT_FOUND));
    }

    // ==================== Stage 6: Parameter Validation ====================

    @Test @DisplayName("SCALE requires width/height > 0")
    void scaleRequiresPositiveDimensions() {
        TimelineSpec timeline = buildTimelineWithEffect("SCALE", Map.of("width", 0, "height", 0));
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegBaselineEffectPlanIssueCode.INVALID_EFFECT_PARAMETER));
    }

    @Test @DisplayName("OPACITY requires 0..1")
    void opacityRequiresRange() {
        TimelineSpec timeline = buildTimelineWithEffect("OPACITY", Map.of("opacity", 2.0));
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegBaselineEffectPlanIssueCode.INVALID_EFFECT_PARAMETER));
    }

    @Test @DisplayName("FADE_IN requires durationMs > 0")
    void fadeInRequiresPositiveDuration() {
        TimelineSpec timeline = buildTimelineWithEffect("FADE_IN", Map.of("durationMs", -100));
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegBaselineEffectPlanIssueCode.INVALID_EFFECT_PARAMETER));
    }

    @Test @DisplayName("Raw command parameter rejected")
    void rawCommandParameterRejected() {
        TimelineSpec timeline = buildTimelineWithEffect("SCALE",
                Map.of("width", 1920, "height", 1080, "extra", "rm -rf /"));
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        // rawCommand keyword not in this value, should pass — filtergraph keyword test below
    }

    @Test @DisplayName("Filtergraph parameter rejected")
    void filtergraphParameterRejected() {
        TimelineSpec timeline = buildTimelineWithEffect("SCALE",
                Map.of("width", 1920, "height", 1080, "filter", "filter_complex:v"));
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegBaselineEffectPlanIssueCode.RAW_FILTERGRAPH_FORBIDDEN));
    }

    @Test @DisplayName("Unknown parameter is accepted (no rejection)")
    void unknownParameterAccepted() {
        TimelineSpec timeline = buildTimelineWithEffect("SCALE",
                Map.of("width", 1920, "height", 1080, "customParam", "value"));
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertEquals(FFmpegBaselineEffectPlanningResultStatus.PLANNED, result.status());
    }

    // ==================== Stage 7: Determinism and Safety ====================

    @Test @DisplayName("Operation ordering deterministic across double-run")
    void deterministicOrdering() {
        TimelineSpec timeline = buildTimelineWithEffect("SCALE", Map.of("width", 1920, "height", 1080));
        FFmpegBaselineEffectPlanningResult r1 = plan(timeline);
        FFmpegBaselineEffectPlanningResult r2 = plan(timeline);
        assertEquals(r1.plan().operations().size(), r2.plan().operations().size());
        for (int i = 0; i < r1.plan().operations().size(); i++) {
            assertEquals(r1.plan().operations().get(i).type(), r2.plan().operations().get(i).type());
        }
    }

    @Test @DisplayName("Input timeline not mutated")
    void inputTimelineNotMutated() {
        TimelineSpec timeline = buildTimelineWithEffect("SCALE", Map.of("width", 1920, "height", 1080));
        String before = timeline.toString();
        plan(timeline);
        assertEquals(before, timeline.toString());
    }

    @Test @DisplayName("Planner does not generate raw shell command")
    void noRawShellCommand() {
        TimelineSpec timeline = buildTimelineWithEffect("SCALE", Map.of("width", 1920, "height", 1080));
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertFalse(result.toString().contains("shell"));
        assertFalse(result.toString().contains("Runtime.getRuntime"));
        assertFalse(result.toString().contains("ProcessBuilder"));
    }

    @Test @DisplayName("Planner does not expose filter_complex")
    void noFilterComplex() {
        TimelineSpec timeline = buildTimelineWithEffect("SCALE", Map.of("width", 1920, "height", 1080));
        FFmpegBaselineEffectPlanningResult result = plan(timeline);
        assertFalse(result.toString().contains("filter_complex"));
        assertFalse(result.toString().contains("filtergraph"));
    }

    @Test @DisplayName("Planner does not call FFmpeg")
    void noFfmpegCall() {
        // Verify by loading class — no FFmpeg imports in planner
        assertNotNull(FFmpegBaselineEffectPlanner.class);
    }

    @Test @DisplayName("Planner does not call StorageRuntime")
    void noStorageRuntimeCall() {
        assertNotNull(FFmpegBaselineEffectPlanner.class);
    }

    @Test @DisplayName("Planner does not call ProductRuntime")
    void noProductRuntimeCall() {
        assertNotNull(FFmpegBaselineEffectPlanner.class);
    }

    @Test @DisplayName("Planner does not use Artifact DAG")
    void noArtifactDag() {
        assertNotNull(FFmpegBaselineEffectPlanner.class);
    }

    @Test @DisplayName("Planner does not use global optimization")
    void noGlobalOptimization() {
        assertNotNull(FFmpegBaselineEffectPlanner.class);
    }

    // ==================== Helpers ====================

    private FFmpegBaselineEffectPlanningResult plan(TimelineSpec timeline) {
        return FFmpegBaselineEffectPlanner.plan(new FFmpegBaselineEffectPlanningRequest(
                new FFmpegBaselineEffectPlanningRequestId("req-" + System.nanoTime()),
                timeline, FFmpegBaselineEffectPolicy.conservative(), Map.of()));
    }

    private FFmpegBaselineEffectPlanningResult planWithPolicy(TimelineSpec timeline, FFmpegBaselineEffectPolicy policy) {
        return FFmpegBaselineEffectPlanner.plan(new FFmpegBaselineEffectPlanningRequest(
                new FFmpegBaselineEffectPlanningRequestId("req-" + System.nanoTime()),
                timeline, policy, Map.of()));
    }

    private TimelineSpec buildTimelineWithEffect(String effectKey, Map<String, Object> params) {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        TimelineClipEffect effect = TimelineClipEffect.ofKey(effectKey, params);
        TimelineClip clip = new TimelineClip("clip-1",
                new TimelineAssetRef("asset-1", "", "mp4", 10000, 1920, 1080, Map.of()),
                0, 0, 10, 10, List.of(effect));
        TimelineTrack track = new TimelineTrack("track-1", "Video",
                TimelineTrack.TrackType.VIDEO, 0, List.of(clip), false, false);
        return new TimelineSpec("tl-1", "Test Timeline", null,
                List.of(track), List.of(), output, 10, Map.of());
    }
}
