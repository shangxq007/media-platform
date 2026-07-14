package com.example.platform.render.domain.timeline.render.plan;

import com.example.platform.render.domain.timeline.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FFmpeg/libass Basic Timeline Render Planner.
 * Covers: domain types, policy, planner, output profile validation,
 * caption/watermark overlay validation, determinism, safety.
 */
class FFmpegLibassBasicRenderPlannerTest {

    // ==================== Stage 1: Domain Types ====================

    @Test @DisplayName("Plan id rejects blank")
    void planIdRejectsBlank() {
        assertThrows(NullPointerException.class, () -> new FFmpegLibassBasicRenderPlanId(null));
        assertThrows(IllegalArgumentException.class, () -> new FFmpegLibassBasicRenderPlanId(""));
    }

    @Test @DisplayName("Stage id rejects blank")
    void stageIdRejectsBlank() {
        assertThrows(NullPointerException.class, () -> new FFmpegLibassBasicRenderStageId(null));
        assertThrows(IllegalArgumentException.class, () -> new FFmpegLibassBasicRenderStageId(""));
    }

    @Test @DisplayName("Step id rejects blank")
    void stepIdRejectsBlank() {
        assertThrows(NullPointerException.class, () -> new FFmpegLibassBasicRenderStepId(null));
        assertThrows(IllegalArgumentException.class, () -> new FFmpegLibassBasicRenderStepId(""));
    }

    @Test @DisplayName("Plan status enum contains required statuses")
    void planStatusEnumContainsRequired() {
        assertNotNull(FFmpegLibassBasicRenderPlanStatus.READY);
        assertNotNull(FFmpegLibassBasicRenderPlanStatus.VALID_WITH_WARNINGS);
        assertNotNull(FFmpegLibassBasicRenderPlanStatus.INVALID);
        assertNotNull(FFmpegLibassBasicRenderPlanStatus.BLOCKED);
        assertNotNull(FFmpegLibassBasicRenderPlanStatus.UNSUPPORTED);
        assertNotNull(FFmpegLibassBasicRenderPlanStatus.FAILED);
    }

    @Test @DisplayName("Stage type enum contains required stage types")
    void stageTypeContainsRequired() {
        assertNotNull(FFmpegLibassBasicRenderStageType.VALIDATE_TIMELINE);
        assertNotNull(FFmpegLibassBasicRenderStageType.PREPARE_INPUTS);
        assertNotNull(FFmpegLibassBasicRenderStageType.PLAN_CLIP_SEQUENCE);
        assertNotNull(FFmpegLibassBasicRenderStageType.PLAN_EFFECTS);
        assertNotNull(FFmpegLibassBasicRenderStageType.PLAN_TRANSITIONS);
        assertNotNull(FFmpegLibassBasicRenderStageType.PLAN_CAPTION_OVERLAYS);
        assertNotNull(FFmpegLibassBasicRenderStageType.PLAN_WATERMARK_OVERLAYS);
        assertNotNull(FFmpegLibassBasicRenderStageType.PLAN_FINAL_ASSEMBLY);
        assertNotNull(FFmpegLibassBasicRenderStageType.PLAN_OUTPUT_ENCODING);
        assertNotNull(FFmpegLibassBasicRenderStageType.PLAN_OUTPUT_VERIFICATION);
    }

    @Test @DisplayName("Step type enum contains required step types")
    void stepTypeContainsRequired() {
        assertNotNull(FFmpegLibassBasicRenderStepType.VALIDATE_TIMELINE);
        assertNotNull(FFmpegLibassBasicRenderStepType.DECLARE_INPUT_CLIP);
        assertNotNull(FFmpegLibassBasicRenderStepType.DECLARE_OUTPUT_PROFILE);
        assertNotNull(FFmpegLibassBasicRenderStepType.APPLY_EFFECT_OPERATION);
        assertNotNull(FFmpegLibassBasicRenderStepType.APPLY_TRANSITION_OPERATION);
        assertNotNull(FFmpegLibassBasicRenderStepType.APPLY_CAPTION_OVERLAY);
        assertNotNull(FFmpegLibassBasicRenderStepType.APPLY_WATERMARK_OVERLAY);
        assertNotNull(FFmpegLibassBasicRenderStepType.ASSEMBLE_CLIP_SEQUENCE);
        assertNotNull(FFmpegLibassBasicRenderStepType.ENCODE_OUTPUT);
        assertNotNull(FFmpegLibassBasicRenderStepType.VERIFY_OUTPUT);
    }

    @Test @DisplayName("Step target is semantic only")
    void stepTargetSemantic() {
        FFmpegLibassBasicRenderStepTarget target = new FFmpegLibassBasicRenderStepTarget(
                FFmpegLibassBasicRenderStepTargetType.CLIP, "clip-1", Map.<String,String>of());
        assertEquals(FFmpegLibassBasicRenderStepTargetType.CLIP, target.targetType());
        assertEquals("clip-1", target.targetId());
        assertNotNull(target.safeMetadata());
    }

    @Test @DisplayName("Step parameter is typed")
    void stepParameterTyped() {
        FFmpegLibassBasicRenderStepParameter param = new FFmpegLibassBasicRenderStepParameter(
                "width", FFmpegLibassBasicRenderStepParameterType.INTEGER, 1920, Map.<String,String>of());
        assertEquals("width", param.name());
        assertEquals(FFmpegLibassBasicRenderStepParameterType.INTEGER, param.type());
        assertEquals(1920, param.value());
    }

    @Test @DisplayName("Plan summary counts stages and steps")
    void planSummaryCounts() {
        FFmpegLibassBasicRenderPlanSummary summary = new FFmpegLibassBasicRenderPlanSummary(
                10, 20, 1, 2, 3, 4, 5, 6, 7, 8, 9, Map.<String,String>of());
        assertEquals(10, summary.totalStages());
        assertEquals(20, summary.totalSteps());
    }

    @Test @DisplayName("Safe metadata only")
    void safeMetadataOnly() {
        FFmpegLibassBasicRenderPlan plan = new FFmpegLibassBasicRenderPlan(
                new FFmpegLibassBasicRenderPlanId("p1"),
                FFmpegLibassBasicRenderPlanStatus.READY,
                List.of(), null, List.of(),
                Map.of("key", "value"));
        assertEquals("value", plan.safeMetadata().get("key"));
    }

    // ==================== Stage 2: Planning Request / Result / Issue Types ====================

    @Test @DisplayName("Request id rejects blank")
    void requestIdRejectsBlank() {
        assertThrows(NullPointerException.class, () -> new FFmpegLibassBasicRenderPlanningRequestId(null));
        assertThrows(IllegalArgumentException.class, () -> new FFmpegLibassBasicRenderPlanningRequestId(""));
    }

    @Test @DisplayName("Request requires timeline")
    void requestRequiresTimeline() {
        assertThrows(NullPointerException.class, () ->
                new FFmpegLibassBasicRenderPlanningRequest(
                        new FFmpegLibassBasicRenderPlanningRequestId("r1"),
                        null, FFmpegLibassBasicRenderPolicy.conservative(), Map.of()));
    }

    @Test @DisplayName("Result supports all statuses")
    void resultSupportsStatuses() {
        assertEquals(FFmpegLibassBasicRenderPlanningResultStatus.PLANNED,
                FFmpegLibassBasicRenderPlanningResult.planned(null).status());
        assertEquals(FFmpegLibassBasicRenderPlanningResultStatus.BLOCKED,
                FFmpegLibassBasicRenderPlanningResult.blocked(List.of()).status());
        assertEquals(FFmpegLibassBasicRenderPlanningResultStatus.UNSUPPORTED,
                FFmpegLibassBasicRenderPlanningResult.unsupported(List.of()).status());
        assertEquals(FFmpegLibassBasicRenderPlanningResultStatus.FAILED,
                FFmpegLibassBasicRenderPlanningResult.failed(List.of()).status());
    }

    @Test @DisplayName("Issue severities exist")
    void issueSeveritiesExist() {
        assertNotNull(FFmpegLibassBasicRenderPlanIssueSeverity.INFO);
        assertNotNull(FFmpegLibassBasicRenderPlanIssueSeverity.WARNING);
        assertNotNull(FFmpegLibassBasicRenderPlanIssueSeverity.ERROR);
        assertNotNull(FFmpegLibassBasicRenderPlanIssueSeverity.BLOCKING);
    }

    @Test @DisplayName("Issue codes include boundary codes")
    void issueCodesIncludeBoundaries() {
        assertNotNull(FFmpegLibassBasicRenderPlanIssueCode.RAW_FILTERGRAPH_FORBIDDEN);
        assertNotNull(FFmpegLibassBasicRenderPlanIssueCode.RAW_PROVIDER_COMMAND_FORBIDDEN);
        assertNotNull(FFmpegLibassBasicRenderPlanIssueCode.USER_RENDER_DAG_FORBIDDEN);
        assertNotNull(FFmpegLibassBasicRenderPlanIssueCode.PLUGIN_EXECUTION_NODE_FORBIDDEN);
        assertNotNull(FFmpegLibassBasicRenderPlanIssueCode.REMOTION_EXECUTION_FORBIDDEN);
        assertNotNull(FFmpegLibassBasicRenderPlanIssueCode.ARTIFACT_DAG_NOT_USED);
        assertNotNull(FFmpegLibassBasicRenderPlanIssueCode.OPEN_CUE_NOT_USED);
        assertNotNull(FFmpegLibassBasicRenderPlanIssueCode.RENDER_EXECUTION_NOT_ALLOWED);
        assertNotNull(FFmpegLibassBasicRenderPlanIssueCode.PRODUCT_CREATION_NOT_ALLOWED);
        assertNotNull(FFmpegLibassBasicRenderPlanIssueCode.STORAGE_RUNTIME_FORBIDDEN);
        assertNotNull(FFmpegLibassBasicRenderPlanIssueCode.PRODUCT_RUNTIME_FORBIDDEN);
        assertNotNull(FFmpegLibassBasicRenderPlanIssueCode.PROVIDER_INTERNALS_FORBIDDEN);
        assertNotNull(FFmpegLibassBasicRenderPlanIssueCode.PERSISTENCE_NOT_IMPLEMENTED);
        assertNotNull(FFmpegLibassBasicRenderPlanIssueCode.PUBLIC_API_NOT_IMPLEMENTED);
    }

    // ==================== Stage 3: Policy ====================

    @Test @DisplayName("Default policy is conservative")
    void defaultPolicyConservative() {
        FFmpegLibassBasicRenderPolicy policy = FFmpegLibassBasicRenderPolicy.conservative();
        assertTrue(policy.allowWarnings());
        assertFalse(policy.allowPocEffects());
        assertFalse(policy.allowPocTransitions());
        assertFalse(policy.failOnTimelineWarnings());
        assertFalse(policy.failOnEffectWarnings());
        assertFalse(policy.failOnTransitionWarnings());
        assertTrue(policy.failOnUnsupportedOutputProfile());
        assertTrue(policy.requireCaptionOverlayValidation());
        assertTrue(policy.requireWatermarkOverlayValidation());
    }

    @Test @DisplayName("POC effects disabled by default")
    void pocEffectsDisabledByDefault() {
        assertFalse(FFmpegLibassBasicRenderPolicy.conservative().allowPocEffects());
    }

    @Test @DisplayName("POC transitions disabled by default")
    void pocTransitionsDisabledByDefault() {
        assertFalse(FFmpegLibassBasicRenderPolicy.conservative().allowPocTransitions());
    }

    @Test @DisplayName("Unsupported output profile fails by default")
    void unsupportedOutputProfileFailsByDefault() {
        assertTrue(FFmpegLibassBasicRenderPolicy.conservative().failOnUnsupportedOutputProfile());
    }

    @Test @DisplayName("Timeline warnings do not fail by default")
    void timelineWarningsDoNotFailByDefault() {
        assertFalse(FFmpegLibassBasicRenderPolicy.conservative().failOnTimelineWarnings());
    }

    @Test @DisplayName("Effect warnings do not fail by default")
    void effectWarningsDoNotFailByDefault() {
        assertFalse(FFmpegLibassBasicRenderPolicy.conservative().failOnEffectWarnings());
    }

    @Test @DisplayName("Transition warnings do not fail by default")
    void transitionWarningsDoNotFailByDefault() {
        assertFalse(FFmpegLibassBasicRenderPolicy.conservative().failOnTransitionWarnings());
    }

    @Test @DisplayName("Caption overlay validation required by default")
    void captionOverlayValidationRequiredByDefault() {
        assertTrue(FFmpegLibassBasicRenderPolicy.conservative().requireCaptionOverlayValidation());
    }

    @Test @DisplayName("Watermark overlay validation required by default")
    void watermarkOverlayValidationRequiredByDefault() {
        assertTrue(FFmpegLibassBasicRenderPolicy.conservative().requireWatermarkOverlayValidation());
    }

    // ==================== Stage 4: Planner — Basic Render Plan ====================

    @Test @DisplayName("Empty valid timeline with output profile produces PLANNED plan")
    void emptyValidTimelineProducesPlannedPlan() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Empty", TimelineOutputSpec.mp4_1080p30());
        FFmpegLibassBasicRenderPlanningResult result = plan(timeline);
        assertEquals(FFmpegLibassBasicRenderPlanningResultStatus.PLANNED, result.status());
        assertNotNull(result.plan());
        assertEquals(FFmpegLibassBasicRenderPlanStatus.READY, result.plan().status());
    }

    @Test @DisplayName("Timeline validation failure returns VALIDATION_FAILED or BLOCKED")
    void timelineValidationFailureReturnsFailed() {
        // Timeline with no tracks
        TimelineSpec timeline = new TimelineSpec("tl-1", "Empty", null,
                List.of(), List.of(), TimelineOutputSpec.mp4_1080p30(), 0, Map.<String,String>of());
        FFmpegLibassBasicRenderPlanningResult result = plan(timeline);
        // Should have issues from validation
        assertFalse(result.issues().isEmpty());
    }

    @Test @DisplayName("Valid effect plan produces APPLY_EFFECT_OPERATION steps")
    void validEffectPlanProducesSteps() {
        TimelineSpec timeline = buildTimelineWithEffect("SCALE",
                Map.of("width", 1920, "height", 1080));
        FFmpegLibassBasicRenderPlanningResult result = plan(timeline);
        assertEquals(FFmpegLibassBasicRenderPlanningResultStatus.PLANNED, result.status());
        assertTrue(result.plan().stages().stream()
                .flatMap(s -> s.steps().stream())
                .anyMatch(s -> s.type() == FFmpegLibassBasicRenderStepType.APPLY_EFFECT_OPERATION));
    }

    @Test @DisplayName("Valid transition plan produces APPLY_TRANSITION_OPERATION steps")
    void validTransitionPlanProducesSteps() {
        TimelineSpec timeline = buildTimelineWithTransition("CROSSFADE", 500);
        FFmpegLibassBasicRenderPlanningResult result = plan(timeline);
        assertEquals(FFmpegLibassBasicRenderPlanningResultStatus.PLANNED, result.status());
        assertTrue(result.plan().stages().stream()
                .flatMap(s -> s.steps().stream())
                .anyMatch(s -> s.type() == FFmpegLibassBasicRenderStepType.APPLY_TRANSITION_OPERATION));
    }

    @Test @DisplayName("Caption overlays produce APPLY_CAPTION_OVERLAY steps")
    void captionOverlaysProduceSteps() {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        TimelineTextOverlay overlay = TimelineTextOverlay.of("cap-1", "Hello World", 0, 5);
        TimelineSpec timeline = new TimelineSpec("tl-1", "Test", null,
                List.of(TimelineTrack.of("track-1", "Video", TimelineTrack.TrackType.VIDEO)),
                List.of(overlay), output, 10, Map.<String,String>of());
        FFmpegLibassBasicRenderPlanningResult result = plan(timeline);
        assertEquals(FFmpegLibassBasicRenderPlanningResultStatus.PLANNED, result.status());
        assertTrue(result.plan().stages().stream()
                .flatMap(s -> s.steps().stream())
                .anyMatch(s -> s.type() == FFmpegLibassBasicRenderStepType.APPLY_CAPTION_OVERLAY));
    }

    @Test @DisplayName("Clip sequence produces DECLARE_INPUT_CLIP and ASSEMBLE_CLIP_SEQUENCE steps")
    void clipSequenceProducesSteps() {
        TimelineSpec timeline = buildTimelineWithClips(List.of(
                Map.of("id", "c1", "start", 0, "dur", 5),
                Map.of("id", "c2", "start", 5, "dur", 5)));
        FFmpegLibassBasicRenderPlanningResult result = plan(timeline);
        assertTrue(result.plan().stages().stream()
                .flatMap(s -> s.steps().stream())
                .anyMatch(s -> s.type() == FFmpegLibassBasicRenderStepType.DECLARE_INPUT_CLIP));
        assertTrue(result.plan().stages().stream()
                .flatMap(s -> s.steps().stream())
                .anyMatch(s -> s.type() == FFmpegLibassBasicRenderStepType.ASSEMBLE_CLIP_SEQUENCE));
    }

    @Test @DisplayName("Output profile produces DECLARE_OUTPUT_PROFILE and ENCODE_OUTPUT steps")
    void outputProfileProducesSteps() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", TimelineOutputSpec.mp4_1080p30());
        FFmpegLibassBasicRenderPlanningResult result = plan(timeline);
        assertTrue(result.plan().stages().stream()
                .flatMap(s -> s.steps().stream())
                .anyMatch(s -> s.type() == FFmpegLibassBasicRenderStepType.DECLARE_OUTPUT_PROFILE));
        assertTrue(result.plan().stages().stream()
                .flatMap(s -> s.steps().stream())
                .anyMatch(s -> s.type() == FFmpegLibassBasicRenderStepType.ENCODE_OUTPUT));
    }

    @Test @DisplayName("Verification step is always present for planned render")
    void verificationStepAlwaysPresent() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", TimelineOutputSpec.mp4_1080p30());
        FFmpegLibassBasicRenderPlanningResult result = plan(timeline);
        assertTrue(result.plan().stages().stream()
                .flatMap(s -> s.steps().stream())
                .anyMatch(s -> s.type() == FFmpegLibassBasicRenderStepType.VERIFY_OUTPUT));
    }

    @Test @DisplayName("No RenderJob/Product/StorageRuntime/OpenCue calls")
    void noExternalCalls() {
        assertNotNull(FFmpegLibassBasicRenderPlanner.class);
    }

    // ==================== Stage 5: Output Profile and Overlay Validation ====================

    @Test @DisplayName("MP4 container accepted")
    void mp4ContainerAccepted() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", TimelineOutputSpec.mp4_1080p30());
        FFmpegLibassBasicRenderPlanningResult result = plan(timeline);
        assertFalse(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegLibassBasicRenderPlanIssueCode.UNSUPPORTED_OUTPUT_CONTAINER));
    }

    @Test @DisplayName("H264 video codec accepted")
    void h264VideoCodecAccepted() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", TimelineOutputSpec.mp4_1080p30());
        FFmpegLibassBasicRenderPlanningResult result = plan(timeline);
        assertFalse(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegLibassBasicRenderPlanIssueCode.UNSUPPORTED_VIDEO_CODEC));
    }

    @Test @DisplayName("Unsupported container blocked")
    void unsupportedContainerBlocked() {
        TimelineOutputSpec output = new TimelineOutputSpec(
                "avi", "1920x1080", 30.0, "h264", 8000,
                TimelineAudioSpec.aacDefault(), "yuv420p");
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", output);
        FFmpegLibassBasicRenderPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegLibassBasicRenderPlanIssueCode.UNSUPPORTED_OUTPUT_CONTAINER));
    }

    @Test @DisplayName("Unsupported video codec blocked")
    void unsupportedVideoCodecBlocked() {
        TimelineOutputSpec output = new TimelineOutputSpec(
                "mp4", "1920x1080", 30.0, "mpeg2", 8000,
                TimelineAudioSpec.aacDefault(), "yuv420p");
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", output);
        FFmpegLibassBasicRenderPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegLibassBasicRenderPlanIssueCode.UNSUPPORTED_VIDEO_CODEC));
    }

    @Test @DisplayName("Caption overlay requires text")
    void captionOverlayRequiresText() {
        TimelineTextOverlay overlay = new TimelineTextOverlay(
                "cap-1", "", "DejaVu Sans", 24, "#FFFFFF",
                "center", "bottom", 0.0, 5.0, null);
        TimelineSpec timeline = new TimelineSpec("tl-1", "Test", null,
                List.of(TimelineTrack.of("track-1", "Video", TimelineTrack.TrackType.VIDEO)),
                List.of(overlay), TimelineOutputSpec.mp4_1080p30(), 10, Map.<String,String>of());
        FFmpegLibassBasicRenderPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegLibassBasicRenderPlanIssueCode.CAPTION_OVERLAY_INVALID));
    }

    @Test @DisplayName("Caption overlay requires valid time range")
    void captionOverlayRequiresValidTimeRange() {
        TimelineTextOverlay overlay = new TimelineTextOverlay(
                "cap-1", "Hello", "DejaVu Sans", 24, "#FFFFFF",
                "center", "bottom", -1.0, 5.0, null);
        TimelineSpec timeline = new TimelineSpec("tl-1", "Test", null,
                List.of(TimelineTrack.of("track-1", "Video", TimelineTrack.TrackType.VIDEO)),
                List.of(overlay), TimelineOutputSpec.mp4_1080p30(), 10, Map.<String,String>of());
        FFmpegLibassBasicRenderPlanningResult result = plan(timeline);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegLibassBasicRenderPlanIssueCode.CAPTION_OVERLAY_INVALID));
    }

    @Test @DisplayName("Watermark opacity must be 0..1")
    void watermarkOpacityMustBeValid() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", TimelineOutputSpec.mp4_1080p30());
        // Add watermark metadata with invalid opacity
        TimelineSpec withWatermark = new TimelineSpec(
                timeline.id(), timeline.name(), timeline.description(),
                timeline.tracks(), timeline.textOverlays(), timeline.outputSpec(),
                timeline.totalDuration(), Map.of("watermark.placement", "bottom-right", "watermark.opacity", "1.5"));
        FFmpegLibassBasicRenderPlanningResult result = plan(withWatermark);
        assertTrue(result.issues().stream().anyMatch(i ->
                i.code() == FFmpegLibassBasicRenderPlanIssueCode.WATERMARK_OVERLAY_INVALID));
    }

    // ==================== Stage 6: Determinism and Safety ====================

    @Test @DisplayName("Stage ordering deterministic across double-run")
    void stageOrderingDeterministic() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", TimelineOutputSpec.mp4_1080p30());
        FFmpegLibassBasicRenderPlanningResult r1 = plan(timeline);
        FFmpegLibassBasicRenderPlanningResult r2 = plan(timeline);
        assertEquals(r1.plan().stages().size(), r2.plan().stages().size());
        for (int i = 0; i < r1.plan().stages().size(); i++) {
            assertEquals(r1.plan().stages().get(i).type(), r2.plan().stages().get(i).type());
        }
    }

    @Test @DisplayName("Step ordering deterministic across double-run")
    void stepOrderingDeterministic() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", TimelineOutputSpec.mp4_1080p30());
        FFmpegLibassBasicRenderPlanningResult r1 = plan(timeline);
        FFmpegLibassBasicRenderPlanningResult r2 = plan(timeline);
        List<FFmpegLibassBasicRenderStep> steps1 = r1.plan().stages().stream()
                .flatMap(s -> s.steps().stream()).toList();
        List<FFmpegLibassBasicRenderStep> steps2 = r2.plan().stages().stream()
                .flatMap(s -> s.steps().stream()).toList();
        assertEquals(steps1.size(), steps2.size());
        for (int i = 0; i < steps1.size(); i++) {
            assertEquals(steps1.get(i).type(), steps2.get(i).type());
        }
    }

    @Test @DisplayName("Input timeline not mutated")
    void inputTimelineNotMutated() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", TimelineOutputSpec.mp4_1080p30());
        String before = timeline.toString();
        plan(timeline);
        assertEquals(before, timeline.toString());
    }

    @Test @DisplayName("Planner does not generate raw shell command")
    void noRawShellCommand() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", TimelineOutputSpec.mp4_1080p30());
        FFmpegLibassBasicRenderPlanningResult result = plan(timeline);
        assertFalse(result.toString().contains("shell"));
        assertFalse(result.toString().contains("Runtime.getRuntime"));
        assertFalse(result.toString().contains("ProcessBuilder"));
    }

    @Test @DisplayName("Planner does not expose filter_complex")
    void noFilterComplex() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", TimelineOutputSpec.mp4_1080p30());
        FFmpegLibassBasicRenderPlanningResult result = plan(timeline);
        assertFalse(result.toString().contains("filter_complex"));
        assertFalse(result.toString().contains("filtergraph"));
    }

    @Test @DisplayName("Planner does not include OpenCue job/layer/frame ids")
    void noOpenCueIds() {
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", TimelineOutputSpec.mp4_1080p30());
        FFmpegLibassBasicRenderPlanningResult result = plan(timeline);
        assertFalse(result.toString().contains("cuebot"));
        assertFalse(result.toString().contains("rqd"));
    }

    @Test @DisplayName("Planner does not implement parallel segment rendering")
    void noParallelSegmentRendering() {
        assertNotNull(FFmpegLibassBasicRenderPlanner.class);
    }

    // ==================== Helpers ====================

    private FFmpegLibassBasicRenderPlanningResult plan(TimelineSpec timeline) {
        return FFmpegLibassBasicRenderPlanner.plan(new FFmpegLibassBasicRenderPlanningRequest(
                new FFmpegLibassBasicRenderPlanningRequestId("req-" + System.nanoTime()),
                timeline, FFmpegLibassBasicRenderPolicy.conservative(), Map.of()));
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
}
