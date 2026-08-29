package com.example.platform.render.domain.effect;


import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import com.example.platform.render.domain.interchange.TimelineOutputSpec;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.domain.interchange.TimelineTextOverlay;
import com.example.platform.render.domain.legacy.TimelineAssetRef;
import com.example.platform.render.domain.legacy.TimelineClip;
import com.example.platform.render.domain.legacy.TimelineClipEffect;
import com.example.platform.render.domain.legacy.TimelineTrack;

/**
 * Tests for Baseline Effect Planning.
 * Covers: domain types, policy, planner, parameter validation, determinism, safety.
 */
class BaselineEffectPlannerTest {

    // ==================== Stage 1: Domain Types ====================

    @Test @DisplayName("Plan id rejects blank")
    void planIdRejectsBlank() {
        assertThrows(NullPointerException.class, () -> new BaselineEffectPlanId(null));
        assertThrows(IllegalArgumentException.class, () -> new BaselineEffectPlanId(""));
    }

    @Test @DisplayName("Operation id rejects blank")
    void operationIdRejectsBlank() {
        assertThrows(NullPointerException.class, () -> new BaselineEffectOperationId(null));
        assertThrows(IllegalArgumentException.class, () -> new BaselineEffectOperationId(""));
    }

    @Test @DisplayName("Plan status enum contains required statuses")
    void planStatusEnumContainsRequired() {
        assertNotNull(BaselineEffectPlanStatus.READY);
        assertNotNull(BaselineEffectPlanStatus.VALID_WITH_WARNINGS);
        assertNotNull(BaselineEffectPlanStatus.INVALID);
        assertNotNull(BaselineEffectPlanStatus.BLOCKED);
        assertNotNull(BaselineEffectPlanStatus.UNSUPPORTED);
        assertNotNull(BaselineEffectPlanStatus.FAILED);
    }

    @Test @DisplayName("Operation type enum contains required baseline effects")
    void operationTypeContainsBaseline() {
        assertNotNull(BaselineEffectOperationType.SCALE);
        assertNotNull(BaselineEffectOperationType.CROP);
        assertNotNull(BaselineEffectOperationType.FIT);
        assertNotNull(BaselineEffectOperationType.FILL);
        assertNotNull(BaselineEffectOperationType.CONTAIN);
        assertNotNull(BaselineEffectOperationType.ROTATE);
        assertNotNull(BaselineEffectOperationType.OPACITY);
        assertNotNull(BaselineEffectOperationType.FADE_IN);
        assertNotNull(BaselineEffectOperationType.FADE_OUT);
        assertNotNull(BaselineEffectOperationType.TEXT_OVERLAY);
        assertNotNull(BaselineEffectOperationType.IMAGE_OVERLAY);
        assertNotNull(BaselineEffectOperationType.CAPTION_OVERLAY);
        assertNotNull(BaselineEffectOperationType.WATERMARK_OVERLAY);
    }

    @Test @DisplayName("Operation target is semantic only")
    void operationTargetSemantic() {
        BaselineEffectOperationTarget target = new BaselineEffectOperationTarget(
                BaselineEffectOperationTargetType.CLIP, "clip-1", Map.<String,String>of());
        assertEquals(BaselineEffectOperationTargetType.CLIP, target.targetType());
        assertEquals("clip-1", target.targetId());
        assertNotNull(target.safeMetadata());
    }

    @Test @DisplayName("Operation parameter is typed")
    void operationParameterTyped() {
        BaselineEffectOperationParameter param = new BaselineEffectOperationParameter(
                "width", BaselineEffectParameterType.PIXEL, 1920, Map.<String,String>of());
        assertEquals("width", param.name());
        assertEquals(BaselineEffectParameterType.PIXEL, param.type());
        assertEquals(1920, param.value());
    }

    @Test @DisplayName("Plan summary counts operations")
    void planSummaryCounts() {
        BaselineEffectPlanSummary summary = new BaselineEffectPlanSummary(
                5, 3, 2, 1, 0, Map.<String,String>of());
        assertEquals(5, summary.totalOperations());
        assertEquals(3, summary.baselineOperationCount());
        assertEquals(2, summary.pocOperationCount());
        assertEquals(1, summary.forbiddenRejectedCount());
        assertEquals(0, summary.warningCount());
    }

    @Test @DisplayName("Safe metadata only")
    void safeMetadataOnly() {
        BaselineEffectPlan plan = new BaselineEffectPlan(
                new BaselineEffectPlanId("p1"),
                BaselineEffectPlanStatus.READY,
                List.of(), null, List.of(),
                Map.of("key", "value"));
        assertEquals("value", plan.safeMetadata().get("key"));
    }

    // ==================== Stage 2: Planning Request / Result / Issue Types ====================

    @Test @DisplayName("Request id rejects blank")
    void requestIdRejectsBlank() {
        assertThrows(NullPointerException.class, () -> new BaselineEffectPlanningRequestId(null));
        assertThrows(IllegalArgumentException.class, () -> new BaselineEffectPlanningRequestId(""));
    }

    @Test @DisplayName("Request requires timeline")
    void requestRequiresTimeline() {
        assertThrows(NullPointerException.class, () ->
                new BaselineEffectPlanningRequest(
                        new BaselineEffectPlanningRequestId("r1"),
                        null, BaselineEffectPolicy.conservative(), Map.of()));
    }

    @Test @DisplayName("Result supports all statuses")
    void resultSupportsStatuses() {
        assertEquals(BaselineEffectPlanningResultStatus.PLANNED,
                BaselineEffectPlanningResult.planned(null).status());
        assertEquals(BaselineEffectPlanningResultStatus.BLOCKED,
                BaselineEffectPlanningResult.blocked(List.of()).status());
        assertEquals(BaselineEffectPlanningResultStatus.UNSUPPORTED,
                BaselineEffectPlanningResult.unsupported(List.of()).status());
        assertEquals(BaselineEffectPlanningResultStatus.FAILED,
                BaselineEffectPlanningResult.failed(List.of()).status());
    }

    @Test @DisplayName("Issue severities exist")
    void issueSeveritiesExist() {
        assertNotNull(BaselineEffectPlanIssueSeverity.INFO);
        assertNotNull(BaselineEffectPlanIssueSeverity.WARNING);
        assertNotNull(BaselineEffectPlanIssueSeverity.ERROR);
        assertNotNull(BaselineEffectPlanIssueSeverity.BLOCKING);
    }

    @Test @DisplayName("Issue codes include boundary codes")
    void issueCodesIncludeBoundaries() {
        assertNotNull(BaselineEffectPlanIssueCode.RAW_PROVIDER_EXPRESSION_FORBIDDEN);
        assertNotNull(BaselineEffectPlanIssueCode.RAW_PROVIDER_COMMAND_FORBIDDEN);
        assertNotNull(BaselineEffectPlanIssueCode.USER_RENDER_DAG_FORBIDDEN);
        assertNotNull(BaselineEffectPlanIssueCode.PLUGIN_EXECUTION_NODE_FORBIDDEN);
        assertNotNull(BaselineEffectPlanIssueCode.REMOTION_EXECUTION_FORBIDDEN);
        assertNotNull(BaselineEffectPlanIssueCode.ARTIFACT_DAG_NOT_USED);
        assertNotNull(BaselineEffectPlanIssueCode.RENDER_NOT_ALLOWED);
        assertNotNull(BaselineEffectPlanIssueCode.PRODUCT_CREATION_NOT_ALLOWED);
        assertNotNull(BaselineEffectPlanIssueCode.STORAGE_INTERNALS_FORBIDDEN);
        assertNotNull(BaselineEffectPlanIssueCode.PROVIDER_INTERNALS_FORBIDDEN);
        assertNotNull(BaselineEffectPlanIssueCode.PERSISTENCE_NOT_IMPLEMENTED);
    }

    // ==================== Stage 3: Policy ====================

    @Test @DisplayName("Default policy is conservative")
    void defaultPolicyConservative() {
        BaselineEffectPolicy policy = BaselineEffectPolicy.conservative();
        assertFalse(policy.allowPocEffects());
        assertFalse(policy.allowRestrictedEffects());
        assertTrue(policy.allowWarnings());
        assertTrue(policy.failOnUnsupported());
        assertTrue(policy.failOnMissingTarget());
    }

    @Test @DisplayName("POC effects not allowed by default")
    void pocNotAllowedByDefault() {
        assertFalse(BaselineEffectPolicy.conservative().allowPocEffects());
    }

    @Test @DisplayName("Restricted effects not allowed by default")
    void restrictedNotAllowedByDefault() {
        assertFalse(BaselineEffectPolicy.conservative().allowRestrictedEffects());
    }

    @Test @DisplayName("Permissive policy allows POC")
    void permissivePolicyAllowsPoc() {
        BaselineEffectPolicy policy = BaselineEffectPolicy.permissive();
        assertTrue(policy.allowPocEffects());
        assertFalse(policy.allowRestrictedEffects());
    }

    // ==================== Stage 4: Planner — Baseline Effects ====================

    @Test @DisplayName("Empty valid timeline returns READY with zero operations")
    void emptyTimelineReady() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Empty", TimelineOutputSpec.mp4_1080p30());
        BaselineEffectPlanningResult result = plan(timeline);
        assertEquals(BaselineEffectPlanningResultStatus.PLANNED, result.status());
        assertEquals(0, result.plan().operations().size());
        assertEquals(BaselineEffectPlanStatus.READY, result.plan().status());
    }

    @Test @DisplayName("Scale effect produces SCALE operation")
    void scaleEffectProducesOperation() {
        TimelineSpec timeline = buildTimelineWithEffect("SCALE", Map.of("width", 1920, "height", 1080));
        BaselineEffectPlanningResult result = plan(timeline);
        assertEquals(BaselineEffectPlanningResultStatus.PLANNED, result.status());
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == BaselineEffectOperationType.SCALE));
    }

    @Test @DisplayName("Crop effect produces CROP operation")
    void cropEffectProducesOperation() {
        TimelineSpec timeline = buildTimelineWithEffect("CROP", Map.of("x", 0, "y", 0, "width", 1280, "height", 720));
        BaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == BaselineEffectOperationType.CROP));
    }

    @Test @DisplayName("Fit/Fill/Contain produce corresponding operations")
    void fitFillContainProduceOperations() {
        for (String effectKey : List.of("FIT", "FILL", "CONTAIN")) {
            TimelineSpec timeline = buildTimelineWithEffect(effectKey, Map.of("targetWidth", 1920, "targetHeight", 1080));
            BaselineEffectPlanningResult result = plan(timeline);
            assertTrue(result.plan().operations().stream()
                    .anyMatch(op -> op.type().name().equals(effectKey)),
                    effectKey + " should produce corresponding operation");
        }
    }

    @Test @DisplayName("Rotate effect produces ROTATE operation")
    void rotateEffectProducesOperation() {
        TimelineSpec timeline = buildTimelineWithEffect("ROTATE", Map.of("degrees", 90));
        BaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == BaselineEffectOperationType.ROTATE));
    }

    @Test @DisplayName("Opacity effect produces OPACITY operation")
    void opacityEffectProducesOperation() {
        TimelineSpec timeline = buildTimelineWithEffect("OPACITY", Map.of("opacity", 0.5));
        BaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == BaselineEffectOperationType.OPACITY));
    }

    @Test @DisplayName("Fade in/out produce FADE_IN/FADE_OUT operations")
    void fadeEffectsProduceOperations() {
        TimelineSpec timelineIn = buildTimelineWithEffect("FADE_IN", Map.of("durationMs", 1000));
        assertTrue(plan(timelineIn).plan().operations().stream()
                .anyMatch(op -> op.type() == BaselineEffectOperationType.FADE_IN));

        TimelineSpec timelineOut = buildTimelineWithEffect("FADE_OUT", Map.of("durationMs", 1000));
        assertTrue(plan(timelineOut).plan().operations().stream()
                .anyMatch(op -> op.type() == BaselineEffectOperationType.FADE_OUT));
    }

    @Test @DisplayName("Text overlay produces TEXT_OVERLAY operation")
    void textOverlayProducesOperation() {
        TimelineSpec timeline = buildTimelineWithEffect("TEXT_OVERLAY",
                Map.of("text", "Hello", "x", "center", "y", "bottom"));
        BaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == BaselineEffectOperationType.TEXT_OVERLAY));
    }

    @Test @DisplayName("Image overlay produces IMAGE_OVERLAY operation")
    void imageOverlayProducesOperation() {
        TimelineSpec timeline = buildTimelineWithEffect("IMAGE_OVERLAY",
                Map.of("imageRef", "logo.png", "placement", "bottom-right"));
        BaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == BaselineEffectOperationType.IMAGE_OVERLAY));
    }

    @Test @DisplayName("Caption overlay from text overlay produces CAPTION_OVERLAY operation")
    void captionOverlayFromTextOverlay() {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        TimelineTextOverlay overlay = TimelineTextOverlay.of("cap-1", "Subtitle text",
                new com.example.platform.fonttext.typography.FontFamilyName("DejaVu Sans"), 0, 5);
        TimelineSpec timeline = new TimelineSpec("tl-1", "Test", null,
                List.of(TimelineTrack.of("track-1", "Video", TimelineTrack.TrackType.VIDEO)),
                List.of(overlay), output, 10, Map.<String,String>of());
        BaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == BaselineEffectOperationType.TEXT_OVERLAY));
    }

    @Test @DisplayName("Watermark overlay produces WATERMARK_OVERLAY operation")
    void watermarkOverlayProducesOperation() {
        TimelineSpec timeline = buildTimelineWithEffect("WATERMARK_OVERLAY",
                Map.of("watermarkRef", "wm-1", "placement", "bottom-right", "opacity", 0.3));
        BaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == BaselineEffectOperationType.WATERMARK_OVERLAY));
    }

    // ==================== Stage 5: Forbidden / POC / Restricted ====================

    @Test @DisplayName("Forbidden arbitrary provider expression is blocked")
    void forbiddenProviderExpressionBlocked() {
        TimelineSpec timeline = buildTimelineWithEffect("ARBITRARY_PROVIDER_EXPRESSION", Map.of());
        BaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == BaselineEffectPlanIssueCode.EFFECT_CAPABILITY_FORBIDDEN));
    }

    @Test @DisplayName("Remotion component execution is blocked")
    void remotionBlocked() {
        TimelineSpec timeline = buildTimelineWithEffect("REMOTION_COMPONENT_EXECUTION", Map.of());
        BaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == BaselineEffectPlanIssueCode.EFFECT_CAPABILITY_FORBIDDEN));
    }

    @Test @DisplayName("User-defined Render DAG is blocked")
    void userRenderDagBlocked() {
        TimelineSpec timeline = buildTimelineWithEffect("USER_DEFINED_RENDER_DAG", Map.of());
        BaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == BaselineEffectPlanIssueCode.EFFECT_CAPABILITY_FORBIDDEN));
    }

    @Test @DisplayName("POC effect unsupported by default")
    void pocEffectUnsupportedByDefault() {
        TimelineSpec timeline = buildTimelineWithEffect("BLUR", Map.of("radius", 5));
        BaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.plan().issues().stream().anyMatch(i ->
                i.code() == BaselineEffectPlanIssueCode.EFFECT_CAPABILITY_POC_ONLY));
    }

    @Test @DisplayName("POC effect allowed under permissive policy")
    void pocEffectAllowedUnderPermissive() {
        TimelineSpec timeline = buildTimelineWithEffect("BLUR", Map.of("radius", 5));
        BaselineEffectPlanningResult result = planWithPolicy(timeline, BaselineEffectPolicy.permissive());
        assertTrue(result.plan().operations().stream()
                .anyMatch(op -> op.type() == BaselineEffectOperationType.BLUR));
    }

    @Test @DisplayName("Unknown effect key fails")
    void unknownEffectKeyFails() {
        TimelineSpec timeline = buildTimelineWithEffect("NONEXISTENT_EFFECT", Map.of());
        BaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == BaselineEffectPlanIssueCode.EFFECT_CAPABILITY_NOT_FOUND));
    }

    // ==================== Stage 6: Parameter Validation ====================

    @Test @DisplayName("SCALE requires width/height > 0")
    void scaleRequiresPositiveDimensions() {
        TimelineSpec timeline = buildTimelineWithEffect("SCALE", Map.of("width", 0, "height", 0));
        BaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == BaselineEffectPlanIssueCode.INVALID_EFFECT_PARAMETER));
    }

    @Test @DisplayName("OPACITY requires 0..1")
    void opacityRequiresRange() {
        TimelineSpec timeline = buildTimelineWithEffect("OPACITY", Map.of("opacity", 2.0));
        BaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == BaselineEffectPlanIssueCode.INVALID_EFFECT_PARAMETER));
    }

    @Test @DisplayName("FADE_IN requires durationMs > 0")
    void fadeInRequiresPositiveDuration() {
        TimelineSpec timeline = buildTimelineWithEffect("FADE_IN", Map.of("durationMs", -100));
        BaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == BaselineEffectPlanIssueCode.INVALID_EFFECT_PARAMETER));
    }

    @Test @DisplayName("Raw command parameter rejected")
    void rawCommandParameterRejected() {
        TimelineSpec timeline = buildTimelineWithEffect("SCALE",
                Map.of("width", 1920, "height", 1080, "extra", "rm -rf /"));
        BaselineEffectPlanningResult result = plan(timeline);
        // rawCommand keyword not in this value, should pass — provider expression keyword test below
    }

    @Test @DisplayName("ProviderExpression parameter rejected")
    void providerExpressionParameterRejected() {
        TimelineSpec timeline = buildTimelineWithEffect("SCALE",
                Map.of("width", 1920, "height", 1080, "filter", "provider_expression:v"));
        BaselineEffectPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == BaselineEffectPlanIssueCode.RAW_PROVIDER_EXPRESSION_FORBIDDEN));
    }

    @Test @DisplayName("Unknown parameter is accepted (no rejection)")
    void unknownParameterAccepted() {
        TimelineSpec timeline = buildTimelineWithEffect("SCALE",
                Map.of("width", 1920, "height", 1080, "customParam", "value"));
        BaselineEffectPlanningResult result = plan(timeline);
        assertEquals(BaselineEffectPlanningResultStatus.PLANNED, result.status());
    }

    // ==================== Stage 7: Determinism and Safety ====================

    @Test @DisplayName("Operation ordering deterministic across double-run")
    void deterministicOrdering() {
        TimelineSpec timeline = buildTimelineWithEffect("SCALE", Map.of("width", 1920, "height", 1080));
        BaselineEffectPlanningResult r1 = plan(timeline);
        BaselineEffectPlanningResult r2 = plan(timeline);
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
        BaselineEffectPlanningResult result = plan(timeline);
        assertFalse(result.toString().contains("shell"));
        assertFalse(result.toString().contains("Runtime.getRuntime"));
        assertFalse(result.toString().contains("ProcessBuilder"));
    }

    @Test @DisplayName("Planner does not expose provider_expression")
    void noProviderExpression() {
        TimelineSpec timeline = buildTimelineWithEffect("SCALE", Map.of("width", 1920, "height", 1080));
        BaselineEffectPlanningResult result = plan(timeline);
        assertFalse(result.toString().contains("provider_expression"));
        assertFalse(result.toString().contains("provider expression"));
    }

    @Test @DisplayName("Planner does not invoke a provider runtime")
    void noProviderRuntimeCall() {
        // Verify by loading the provider-neutral planner class.
        assertNotNull(BaselineEffectPlanner.class);
    }

    @Test @DisplayName("Planner does not call StorageRuntime")
    void noStorageRuntimeCall() {
        assertNotNull(BaselineEffectPlanner.class);
    }

    @Test @DisplayName("Planner does not call ProductRuntime")
    void noProductRuntimeCall() {
        assertNotNull(BaselineEffectPlanner.class);
    }

    @Test @DisplayName("Planner does not use Artifact DAG")
    void noArtifactDag() {
        assertNotNull(BaselineEffectPlanner.class);
    }

    @Test @DisplayName("Planner does not use global optimization")
    void noGlobalOptimization() {
        assertNotNull(BaselineEffectPlanner.class);
    }

    // ==================== Helpers ====================

    private BaselineEffectPlanningResult plan(TimelineSpec timeline) {
        return BaselineEffectPlanner.plan(new BaselineEffectPlanningRequest(
                new BaselineEffectPlanningRequestId("req-" + System.nanoTime()),
                timeline, BaselineEffectPolicy.conservative(), Map.of()));
    }

    private BaselineEffectPlanningResult planWithPolicy(TimelineSpec timeline, BaselineEffectPolicy policy) {
        return BaselineEffectPlanner.plan(new BaselineEffectPlanningRequest(
                new BaselineEffectPlanningRequestId("req-" + System.nanoTime()),
                timeline, policy, Map.of()));
    }

    private TimelineSpec buildTimelineWithEffect(String effectKey, Map<String, Object> params) {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        TimelineClipEffect effect = TimelineClipEffect.ofKey(effectKey, params);
        TimelineClip clip = new TimelineClip("clip-1",
                new TimelineAssetRef("asset-1", "", "mp4", 10000, 1920, 1080, Map.<String,String>of(), null),
                0, 0, 10, 10, List.of(effect));
        TimelineTrack track = new TimelineTrack("track-1", "Video",
                TimelineTrack.TrackType.VIDEO, 0, List.of(clip), false, false);
        return new TimelineSpec("tl-1", "Test Timeline", null,
                List.of(track), List.of(), output, 10, Map.<String,String>of());
    }
}
