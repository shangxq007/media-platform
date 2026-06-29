package com.example.platform.render.domain.scenario;

import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.domain.timeline.editing.TimelineEditOperation;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry of all internal scenario definitions.
 * Pure, immutable. Internal domain model.
 * Provides the 10 required scenarios and optional scenarios.
 */
public final class InternalScenarioRegistry {

    private InternalScenarioRegistry() {}

    /** All required scenario definitions. */
    public static List<InternalScenarioDefinition> allRequired() {
        List<InternalScenarioDefinition> all = new ArrayList<>();
        all.add(scenario001());
        all.add(scenario002());
        all.add(scenario003());
        all.add(scenario004());
        all.add(scenario005());
        all.add(scenario006());
        all.add(scenario007());
        all.add(scenario008());
        all.add(scenario009());
        all.add(scenario010());
        return Collections.unmodifiableList(all);
    }

    /** All required + optional scenario definitions. */
    public static List<InternalScenarioDefinition> all() {
        List<InternalScenarioDefinition> all = new ArrayList<>(allRequired());
        // Optional timeline git scenarios omitted for P2X.0
        return Collections.unmodifiableList(all);
    }

    /** Lookup by id. */
    public static Optional<InternalScenarioDefinition> findById(String id) {
        return all().stream().filter(d -> d.id().value().equals(id)).findFirst();
    }

    // ==================== scenario-001: Basic Timeline Create ====================

    static InternalScenarioDefinition scenario001() {
        TimelineSpec timeline = TimelineSpec.create("sc001-tl", "basic-timeline", TimelineOutputSpec.mp4_1080p30());
        return new InternalScenarioDefinition(
                new InternalScenarioId("scenario-001-basic-timeline-create"),
                new InternalScenarioName("Basic Timeline Create"),
                InternalScenarioCategory.TIMELINE_EDITING,
                "Validates that a basic timeline can be created with one video track and default output profile.",
                InternalScenarioStatus.ACTIVE,
                timeline,
                List.of(),
                new InternalScenarioExpectedOutcome(
                        InternalScenarioResultStatus.PASS,
                        List.of(),
                        Map.of("hasVideoTrack", true, "outputFormat", "mp4"),
                        Map.of()),
                Map.of("noFFmpegExecution", "true", "noOpenCue", "true"),
                Map.of());
    }

    // ==================== scenario-002: Caption Overlay Render Plan ====================

    static InternalScenarioDefinition scenario002() {
        TimelineAssetRef asset = TimelineAssetRef.of("asset-1", "internal://clip1.mp4");
        TimelineClip clip = TimelineClip.of("clip-1", asset, 0.0, 0.0, 10.0);
        TimelineTrack track = new TimelineTrack("track-1", "Main Video", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
        TimelineTextOverlay caption = TimelineTextOverlay.of("caption-1", "Hello World", 0.0, 5.0);
        TimelineSpec timeline = new TimelineSpec(
                "sc002-tl", "caption-timeline", "Caption overlay test",
                List.of(track), List.of(caption),
                TimelineOutputSpec.mp4_1080p30(), 10.0, Map.of());

        return new InternalScenarioDefinition(
                new InternalScenarioId("scenario-002-caption-overlay-render-plan"),
                new InternalScenarioName("Caption Overlay Render Plan"),
                InternalScenarioCategory.BASIC_RENDER_PLANNING,
                "Validates caption overlay planning produces expected render steps.",
                InternalScenarioStatus.ACTIVE,
                timeline,
                List.of(),
                new InternalScenarioExpectedOutcome(
                        InternalScenarioResultStatus.PASS,
                        List.of(),
                        Map.of("hasCaptionSteps", true, "stagesCountMin", 1),
                        Map.of()),
                Map.of("noFFmpegExecution", "true"),
                Map.of());
    }

    // ==================== scenario-003: Watermark Overlay Render Plan ====================

    static InternalScenarioDefinition scenario003() {
        TimelineAssetRef asset = TimelineAssetRef.of("asset-1", "internal://clip1.mp4");
        TimelineClip clip = TimelineClip.of("clip-1", asset, 0.0, 0.0, 10.0);
        TimelineTrack track = new TimelineTrack("track-1", "Main Video", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
        Map<String, String> metadata = Map.of(
                "watermark.placement", "bottom-right",
                "watermark.opacity", "0.5");
        TimelineSpec timeline = new TimelineSpec(
                "sc003-tl", "watermark-timeline", "Watermark overlay test",
                List.of(track), List.of(),
                TimelineOutputSpec.mp4_1080p30(), 10.0, metadata);

        return new InternalScenarioDefinition(
                new InternalScenarioId("scenario-003-watermark-overlay-render-plan"),
                new InternalScenarioName("Watermark Overlay Render Plan"),
                InternalScenarioCategory.BASIC_RENDER_PLANNING,
                "Validates watermark overlay planning from metadata produces expected render steps.",
                InternalScenarioStatus.ACTIVE,
                timeline,
                List.of(),
                new InternalScenarioExpectedOutcome(
                        InternalScenarioResultStatus.PASS,
                        List.of(),
                        Map.of("hasWatermarkSteps", true, "stagesCountMin", 1),
                        Map.of()),
                Map.of("noFFmpegExecution", "true"),
                Map.of());
    }

    // ==================== scenario-004: Effect Plan Scale/Crop/Opacity ====================

    static InternalScenarioDefinition scenario004() {
        TimelineAssetRef asset = TimelineAssetRef.of("asset-1", "internal://clip1.mp4");
        List<TimelineClipEffect> effects = List.of(
                TimelineClipEffect.ofKey("SCALE", Map.of("width", 1280, "height", 720)),
                TimelineClipEffect.ofKey("CROP", Map.of("x", 0, "y", 0, "width", 640, "height", 480)),
                TimelineClipEffect.ofKey("OPACITY", Map.of("opacity", 0.8)));
        TimelineClip clip = new TimelineClip("clip-1", asset, 0.0, 0.0, 10.0, 10.0, effects);
        TimelineTrack track = new TimelineTrack("track-1", "Main Video", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
        TimelineSpec timeline = new TimelineSpec(
                "sc004-tl", "effect-timeline", "Effect plan test",
                List.of(track), List.of(),
                TimelineOutputSpec.mp4_1080p30(), 10.0, Map.of());

        return new InternalScenarioDefinition(
                new InternalScenarioId("scenario-004-effect-plan-scale-crop-opacity"),
                new InternalScenarioName("Effect Plan Scale/Crop/Opacity"),
                InternalScenarioCategory.EFFECT_PLANNING,
                "Validates effect planning produces expected effect operations for SCALE, CROP, OPACITY.",
                InternalScenarioStatus.ACTIVE,
                timeline,
                List.of(),
                new InternalScenarioExpectedOutcome(
                        InternalScenarioResultStatus.PASS,
                        List.of(),
                        Map.of("effectOperationCount", 3, "hasScale", true, "hasCrop", true, "hasOpacity", true),
                        Map.of()),
                Map.of("noFFmpegExecution", "true"),
                Map.of());
    }

    // ==================== scenario-005: Transition Plan Cut/Crossfade ====================

    static InternalScenarioDefinition scenario005() {
        TimelineAssetRef asset1 = TimelineAssetRef.of("asset-1", "internal://clip1.mp4");
        TimelineAssetRef asset2 = TimelineAssetRef.of("asset-2", "internal://clip2.mp4");
        List<TimelineClipEffect> transitionEffect = List.of(
                TimelineClipEffect.ofKey("CROSSFADE", Map.of("durationMs", 1000)));
        TimelineClip clip1 = new TimelineClip("clip-1", asset1, 0.0, 0.0, 5.0, 5.0, transitionEffect);
        TimelineClip clip2 = TimelineClip.of("clip-2", asset2, 5.0, 0.0, 5.0);
        TimelineTrack track = new TimelineTrack("track-1", "Main Video", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip1, clip2), false, false);
        TimelineSpec timeline = new TimelineSpec(
                "sc005-tl", "transition-timeline", "Transition plan test",
                List.of(track), List.of(),
                TimelineOutputSpec.mp4_1080p30(), 10.0, Map.of());

        return new InternalScenarioDefinition(
                new InternalScenarioId("scenario-005-transition-plan-cut-crossfade"),
                new InternalScenarioName("Transition Plan Cut/Crossfade"),
                InternalScenarioCategory.TRANSITION_PLANNING,
                "Validates transition planning produces expected transition operations.",
                InternalScenarioStatus.ACTIVE,
                timeline,
                List.of(),
                new InternalScenarioExpectedOutcome(
                        InternalScenarioResultStatus.PASS,
                        List.of(),
                        Map.of("transitionOperationCountMin", 1, "hasCrossfade", true),
                        Map.of()),
                Map.of("noFFmpegExecution", "true"),
                Map.of());
    }

    // ==================== scenario-006: Basic Render Plan Composition ====================

    static InternalScenarioDefinition scenario006() {
        TimelineAssetRef asset = TimelineAssetRef.of("asset-1", "internal://clip1.mp4");
        List<TimelineClipEffect> effects = List.of(
                TimelineClipEffect.ofKey("OPACITY", Map.of("opacity", 0.9)));
        TimelineClip clip = new TimelineClip("clip-1", asset, 0.0, 0.0, 10.0, 10.0, effects);
        TimelineTrack track = new TimelineTrack("track-1", "Main Video", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
        TimelineTextOverlay caption = TimelineTextOverlay.of("caption-1", "Test", 0.0, 5.0);
        Map<String, String> metadata = Map.of("watermark.placement", "top-left");
        TimelineSpec timeline = new TimelineSpec(
                "sc006-tl", "full-render-timeline", "Full render plan composition test",
                List.of(track), List.of(caption),
                TimelineOutputSpec.mp4_1080p30(), 10.0, metadata);

        return new InternalScenarioDefinition(
                new InternalScenarioId("scenario-006-basic-render-plan-composition"),
                new InternalScenarioName("Basic Render Plan Composition"),
                InternalScenarioCategory.BASIC_RENDER_PLANNING,
                "Validates full render plan composition with effects, captions, watermarks, and output encoding.",
                InternalScenarioStatus.ACTIVE,
                timeline,
                List.of(),
                new InternalScenarioExpectedOutcome(
                        InternalScenarioResultStatus.PASS,
                        List.of(),
                        Map.of("stagesCountMin", 5, "hasEffectStage", true, "hasCaptionStage", true,
                                "hasWatermarkStage", true, "hasOutputEncoding", true),
                        Map.of()),
                Map.of("noFFmpegExecution", "true", "noOpenCue", "true"),
                Map.of());
    }

    // ==================== scenario-007: Invalid Effect Forbidden Filtergraph ====================

    static InternalScenarioDefinition scenario007() {
        TimelineAssetRef asset = TimelineAssetRef.of("asset-1", "internal://clip1.mp4");
        List<TimelineClipEffect> effects = List.of(
                TimelineClipEffect.ofKey("CUSTOM_FILTERGRAPH", Map.of("filter", "some_raw_filter")));
        TimelineClip clip = new TimelineClip("clip-1", asset, 0.0, 0.0, 10.0, 10.0, effects);
        TimelineTrack track = new TimelineTrack("track-1", "Main Video", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
        TimelineSpec timeline = new TimelineSpec(
                "sc007-tl", "forbidden-effect-timeline", "Forbidden filtergraph test",
                List.of(track), List.of(),
                TimelineOutputSpec.mp4_1080p30(), 10.0, Map.of());

        return new InternalScenarioDefinition(
                new InternalScenarioId("scenario-007-invalid-effect-forbidden-filtergraph"),
                new InternalScenarioName("Invalid Effect Forbidden Filtergraph"),
                InternalScenarioCategory.SAFETY_BOUNDARY,
                "Validates that arbitrary filtergraph effects are blocked by effect planning.",
                InternalScenarioStatus.ACTIVE,
                timeline,
                List.of(),
                new InternalScenarioExpectedOutcome(
                        InternalScenarioResultStatus.BLOCKED,
                        List.of(InternalScenarioIssueCode.FORBIDDEN_EFFECT_NOT_BLOCKED),
                        Map.of(),
                        Map.of()),
                Map.of("noFFmpegExecution", "true", "noRawFiltergraph", "true"),
                Map.of());
    }

    // ==================== scenario-008: Invalid Transition User-Defined Graph ====================

    static InternalScenarioDefinition scenario008() {
        TimelineAssetRef asset1 = TimelineAssetRef.of("asset-1", "internal://clip1.mp4");
        TimelineAssetRef asset2 = TimelineAssetRef.of("asset-2", "internal://clip2.mp4");
        List<TimelineClipEffect> transitionEffect = List.of(
                TimelineClipEffect.ofKey("CUSTOM_GRAPH_TRANSITION", Map.of("graph", "user_defined")));
        TimelineClip clip1 = new TimelineClip("clip-1", asset1, 0.0, 0.0, 5.0, 5.0, transitionEffect);
        TimelineClip clip2 = TimelineClip.of("clip-2", asset2, 5.0, 0.0, 5.0);
        TimelineTrack track = new TimelineTrack("track-1", "Main Video", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip1, clip2), false, false);
        TimelineSpec timeline = new TimelineSpec(
                "sc008-tl", "forbidden-transition-timeline", "User-defined graph test",
                List.of(track), List.of(),
                TimelineOutputSpec.mp4_1080p30(), 10.0, Map.of());

        return new InternalScenarioDefinition(
                new InternalScenarioId("scenario-008-invalid-transition-user-defined-graph"),
                new InternalScenarioName("Invalid Transition User-Defined Graph"),
                InternalScenarioCategory.SAFETY_BOUNDARY,
                "Validates that user-defined transition graphs are blocked by transition planning.",
                InternalScenarioStatus.ACTIVE,
                timeline,
                List.of(),
                new InternalScenarioExpectedOutcome(
                        InternalScenarioResultStatus.BLOCKED,
                        List.of(InternalScenarioIssueCode.FORBIDDEN_TRANSITION_NOT_BLOCKED),
                        Map.of(),
                        Map.of()),
                Map.of("noFFmpegExecution", "true", "noUserDefinedGraph", "true"),
                Map.of());
    }

    // ==================== scenario-009: Output Profile Validation ====================

    static InternalScenarioDefinition scenario009() {
        TimelineAssetRef asset = TimelineAssetRef.of("asset-1", "internal://clip1.mp4");
        TimelineClip clip = TimelineClip.of("clip-1", asset, 0.0, 0.0, 10.0);
        TimelineTrack track = new TimelineTrack("track-1", "Main Video", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip), false, false);
        // Use an unsupported container to trigger output profile validation failure
        TimelineOutputSpec badOutput = new TimelineOutputSpec(
                "avi", "1920x1080", 30.0, "h264", 8000, TimelineAudioSpec.aacDefault(), "yuv420p");
        TimelineSpec timeline = new TimelineSpec(
                "sc009-tl", "bad-output-timeline", "Invalid output profile test",
                List.of(track), List.of(),
                badOutput, 10.0, Map.of());

        return new InternalScenarioDefinition(
                new InternalScenarioId("scenario-009-output-profile-validation"),
                new InternalScenarioName("Output Profile Validation"),
                InternalScenarioCategory.OUTPUT_PROFILE,
                "Validates that unsupported output container is blocked by render planning.",
                InternalScenarioStatus.ACTIVE,
                timeline,
                List.of(),
                new InternalScenarioExpectedOutcome(
                        InternalScenarioResultStatus.BLOCKED,
                        List.of(InternalScenarioIssueCode.OUTPUT_PROFILE_INVALID),
                        Map.of(),
                        Map.of()),
                Map.of("noFFmpegExecution", "true"),
                Map.of());
    }

    // ==================== scenario-010: Full Basic Planning Flow ====================

    static InternalScenarioDefinition scenario010() {
        TimelineAssetRef asset1 = TimelineAssetRef.of("asset-1", "internal://clip1.mp4");
        TimelineAssetRef asset2 = TimelineAssetRef.of("asset-2", "internal://clip2.mp4");
        List<TimelineClipEffect> effects1 = List.of(
                TimelineClipEffect.ofKey("SCALE", Map.of("width", 1280, "height", 720)),
                TimelineClipEffect.ofKey("OPACITY", Map.of("opacity", 0.95)));
        List<TimelineClipEffect> transitionEffect = List.of(
                TimelineClipEffect.ofKey("CROSSFADE", Map.of("durationMs", 1000)));
        TimelineClip clip1 = new TimelineClip("clip-1", asset1, 0.0, 0.0, 5.0, 5.0, transitionEffect);
        TimelineClip clip2 = new TimelineClip("clip-2", asset2, 5.0, 0.0, 5.0, 5.0, effects1);
        TimelineTrack track = new TimelineTrack("track-1", "Main Video", TimelineTrack.TrackType.VIDEO, 0,
                List.of(clip1, clip2), false, false);
        TimelineTextOverlay caption = TimelineTextOverlay.of("caption-1", "Full Flow", 0.0, 10.0);
        Map<String, String> metadata = Map.of(
                "watermark.placement", "bottom-right",
                "watermark.opacity", "0.3");
        TimelineSpec timeline = new TimelineSpec(
                "sc010-tl", "full-flow-timeline", "Full basic planning flow test",
                List.of(track), List.of(caption),
                TimelineOutputSpec.mp4_1080p30(), 10.0, metadata);

        return new InternalScenarioDefinition(
                new InternalScenarioId("scenario-010-full-basic-planning-flow"),
                new InternalScenarioName("Full Basic Planning Flow"),
                InternalScenarioCategory.REGRESSION,
                "Validates the full planning flow: timeline editing → effect planning → transition planning → render plan.",
                InternalScenarioStatus.ACTIVE,
                timeline,
                List.of(),
                new InternalScenarioExpectedOutcome(
                        InternalScenarioResultStatus.PASS,
                        List.of(),
                        Map.of("stagesCountMin", 8, "hasEffectOperations", true,
                                "hasTransitions", true, "hasCaptionSteps", true,
                                "hasWatermarkSteps", true, "hasOutputEncoding", true),
                        Map.of()),
                Map.of("noFFmpegExecution", "true", "noOpenCue", "true", "noProductCreation", "true"),
                Map.of());
    }
}
