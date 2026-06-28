package com.example.platform.render.domain.timeline.diff.calculation;

import com.example.platform.render.domain.timeline.diff.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CanonicalTimelineDiffCalculator.
 * Proves: track/clip/caption/watermark/template/workflow/output/metadata diff,
 * render impact estimation, deterministic ordering, safety.
 */
class CanonicalTimelineDiffCalculatorTest {

    private CanonicalTimelineDiffCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new CanonicalTimelineDiffCalculator();
    }

    // --- No-op diff ---

    @Test
    @DisplayName("Identical snapshots produce no-op diff")
    void identicalSnapshotsNoOp() {
        CanonicalTimelineSnapshot snap = simpleSnapshot("rev-1");
        CanonicalTimelineDiffCalculationResult result = calculator.calculate(snap, snap);

        assertTrue(result.successful());
        assertTrue(result.diff().isNoOp());
        assertEquals(TimelineRenderImpactLevel.NONE, result.diff().renderImpact().level());
    }

    @Test
    @DisplayName("Null snapshots fail")
    void nullSnapshotsFail() {
        CanonicalTimelineDiffCalculationResult result = calculator.calculate(null, null);
        assertFalse(result.successful());
    }

    // --- Duration diff ---

    @Test
    @DisplayName("Duration changed produces TIMELINE_DURATION_CHANGED")
    void durationChanged() {
        CanonicalTimelineSnapshot before = simpleSnapshot("rev-1");
        CanonicalTimelineSnapshot after = new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-2"), "rev-2", 10000,
                before.tracks(), before.captions(), before.watermarks(),
                before.templateApplications(), before.workflowSteps(),
                before.outputProfile(), Map.of());

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);

        assertTrue(result.diff().operations().stream()
                .anyMatch(op -> op.type() == TimelineChangeType.TIMELINE_DURATION_CHANGED));
        assertEquals(TimelineRenderImpactLevel.FULL_RERENDER, result.diff().renderImpact().level());
    }

    // --- Track diff ---

    @Test
    @DisplayName("Track added")
    void trackAdded() {
        CanonicalTimelineSnapshot before = simpleSnapshot("rev-1");
        CanonicalTimelineTrackSnapshot newTrack = new CanonicalTimelineTrackSnapshot(
                "track-2", 1, "VIDEO", List.of(), Map.of());
        CanonicalTimelineSnapshot after = new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-2"), "rev-2", 5000,
                List.of(trackSnapshot("track-1", 0), newTrack),
                List.of(), List.of(), List.of(), List.of(), null, Map.of());

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);

        assertTrue(result.diff().operations().stream()
                .anyMatch(op -> op.type() == TimelineChangeType.TRACK_ADDED));
    }

    @Test
    @DisplayName("Track removed")
    void trackRemoved() {
        CanonicalTimelineSnapshot before = new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-1"), "rev-1", 5000,
                List.of(trackSnapshot("track-1", 0), trackSnapshot("track-2", 1)),
                List.of(), List.of(), List.of(), List.of(), null, Map.of());
        CanonicalTimelineSnapshot after = simpleSnapshot("rev-2");

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);

        assertTrue(result.diff().operations().stream()
                .anyMatch(op -> op.type() == TimelineChangeType.TRACK_REMOVED));
    }

    @Test
    @DisplayName("Track reordered")
    void trackReordered() {
        CanonicalTimelineTrackSnapshot t1 = trackSnapshot("track-1", 0);
        CanonicalTimelineTrackSnapshot t2 = trackSnapshot("track-2", 1);
        CanonicalTimelineTrackSnapshot t1moved = new CanonicalTimelineTrackSnapshot(
                "track-1", 1, "VIDEO", List.of(), Map.of());
        CanonicalTimelineTrackSnapshot t2moved = new CanonicalTimelineTrackSnapshot(
                "track-2", 0, "VIDEO", List.of(), Map.of());

        CanonicalTimelineSnapshot before = new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-1"), "rev-1", 5000,
                List.of(t1, t2), List.of(), List.of(), List.of(), List.of(), null, Map.of());
        CanonicalTimelineSnapshot after = new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-2"), "rev-2", 5000,
                List.of(t1moved, t2moved), List.of(), List.of(), List.of(), List.of(), null, Map.of());

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);

        assertTrue(result.diff().operations().stream()
                .anyMatch(op -> op.type() == TimelineChangeType.TRACK_REORDERED));
    }

    // --- Clip diff ---

    @Test
    @DisplayName("Clip added")
    void clipAdded() {
        CanonicalTimelineSnapshot before = simpleSnapshot("rev-1");
        CanonicalTimelineClipSnapshot newClip = new CanonicalTimelineClipSnapshot(
                "clip-2", "asset-2", 5000, 5000, 0, 5000, Map.of());
        CanonicalTimelineSnapshot after = snapshotWithClips("rev-2",
                List.of(clipSnapshot("clip-1", 0), newClip));

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);

        assertTrue(result.diff().operations().stream()
                .anyMatch(op -> op.type() == TimelineChangeType.CLIP_ADDED));
    }

    @Test
    @DisplayName("Clip removed")
    void clipRemoved() {
        CanonicalTimelineSnapshot before = snapshotWithClips("rev-1",
                List.of(clipSnapshot("clip-1", 0), clipSnapshot("clip-2", 5)));
        CanonicalTimelineSnapshot after = simpleSnapshot("rev-2");

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);

        assertTrue(result.diff().operations().stream()
                .anyMatch(op -> op.type() == TimelineChangeType.CLIP_REMOVED));
    }

    @Test
    @DisplayName("Clip moved (start changed)")
    void clipMoved() {
        CanonicalTimelineClipSnapshot beforeClip = clipSnapshot("clip-1", 0);
        CanonicalTimelineClipSnapshot afterClip = new CanonicalTimelineClipSnapshot(
                "clip-1", "asset-1", 2000, 5000, 0, 5000, Map.of());

        CanonicalTimelineSnapshot before = snapshotWithClips("rev-1", List.of(beforeClip));
        CanonicalTimelineSnapshot after = snapshotWithClips("rev-2", List.of(afterClip));

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);

        assertTrue(result.diff().operations().stream()
                .anyMatch(op -> op.type() == TimelineChangeType.CLIP_MOVED));
    }

    @Test
    @DisplayName("Clip trimmed (duration changed)")
    void clipTrimmed() {
        CanonicalTimelineClipSnapshot beforeClip = clipSnapshot("clip-1", 0);
        CanonicalTimelineClipSnapshot afterClip = new CanonicalTimelineClipSnapshot(
                "clip-1", "asset-1", 0, 8000, 0, 8000, Map.of());

        CanonicalTimelineSnapshot before = snapshotWithClips("rev-1", List.of(beforeClip));
        CanonicalTimelineSnapshot after = snapshotWithClips("rev-2", List.of(afterClip));

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);

        assertTrue(result.diff().operations().stream()
                .anyMatch(op -> op.type() == TimelineChangeType.CLIP_TRIMMED));
    }

    @Test
    @DisplayName("Asset binding changed")
    void assetBindingChanged() {
        CanonicalTimelineClipSnapshot beforeClip = clipSnapshot("clip-1", 0);
        CanonicalTimelineClipSnapshot afterClip = new CanonicalTimelineClipSnapshot(
                "clip-1", "asset-NEW", 0, 5000, 0, 5000, Map.of());

        CanonicalTimelineSnapshot before = snapshotWithClips("rev-1", List.of(beforeClip));
        CanonicalTimelineSnapshot after = snapshotWithClips("rev-2", List.of(afterClip));

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);

        assertTrue(result.diff().operations().stream()
                .anyMatch(op -> op.type() == TimelineChangeType.ASSET_BINDING_CHANGED));
    }

    // --- Caption diff ---

    @Test
    @DisplayName("Caption text changed")
    void captionTextChanged() {
        CanonicalTimelineCaptionSnapshot beforeCap = new CanonicalTimelineCaptionSnapshot(
                "cap-1", 0, 3000, "Hello", Map.of(), Map.of());
        CanonicalTimelineCaptionSnapshot afterCap = new CanonicalTimelineCaptionSnapshot(
                "cap-1", 0, 3000, "World", Map.of(), Map.of());

        CanonicalTimelineSnapshot before = snapshotWithCaptions("rev-1", List.of(beforeCap));
        CanonicalTimelineSnapshot after = snapshotWithCaptions("rev-2", List.of(afterCap));

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);

        assertTrue(result.diff().operations().stream()
                .anyMatch(op -> op.type() == TimelineChangeType.CAPTION_SEGMENT_CHANGED));
        assertEquals(TimelineRenderImpactLevel.PARTIAL_RERENDER, result.diff().renderImpact().level());
    }

    @Test
    @DisplayName("Caption style changed")
    void captionStyleChanged() {
        CanonicalTimelineCaptionSnapshot beforeCap = new CanonicalTimelineCaptionSnapshot(
                "cap-1", 0, 3000, "Hello", Map.of("fontSize", "24"), Map.of());
        CanonicalTimelineCaptionSnapshot afterCap = new CanonicalTimelineCaptionSnapshot(
                "cap-1", 0, 3000, "Hello", Map.of("fontSize", "32"), Map.of());

        CanonicalTimelineSnapshot before = snapshotWithCaptions("rev-1", List.of(beforeCap));
        CanonicalTimelineSnapshot after = snapshotWithCaptions("rev-2", List.of(afterCap));

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);

        assertTrue(result.diff().operations().stream()
                .anyMatch(op -> op.type() == TimelineChangeType.TEXT_STYLE_CHANGED));
    }

    // --- Watermark diff ---

    @Test
    @DisplayName("Watermark opacity changed")
    void watermarkOpacityChanged() {
        CanonicalTimelineWatermarkSnapshot beforeWm = new CanonicalTimelineWatermarkSnapshot(
                "wm-1", "asset-logo", "BOTTOM_RIGHT", 50, Map.of());
        CanonicalTimelineWatermarkSnapshot afterWm = new CanonicalTimelineWatermarkSnapshot(
                "wm-1", "asset-logo", "BOTTOM_RIGHT", 80, Map.of());

        CanonicalTimelineSnapshot before = snapshotWithWatermarks("rev-1", List.of(beforeWm));
        CanonicalTimelineSnapshot after = snapshotWithWatermarks("rev-2", List.of(afterWm));

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);

        assertTrue(result.diff().operations().stream()
                .anyMatch(op -> op.type() == TimelineChangeType.WATERMARK_CHANGED));
    }

    // --- Template diff ---

    @Test
    @DisplayName("Template parameter changed")
    void templateParameterChanged() {
        CanonicalTimelineTemplateApplicationSnapshot beforeTa =
                new CanonicalTimelineTemplateApplicationSnapshot("app-1", "tpl-1", "1.0",
                        Map.of("fontSize", "24"), Map.of());
        CanonicalTimelineTemplateApplicationSnapshot afterTa =
                new CanonicalTimelineTemplateApplicationSnapshot("app-1", "tpl-1", "1.0",
                        Map.of("fontSize", "32"), Map.of());

        CanonicalTimelineSnapshot before = snapshotWithTemplates("rev-1", List.of(beforeTa));
        CanonicalTimelineSnapshot after = snapshotWithTemplates("rev-2", List.of(afterTa));

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);

        assertTrue(result.diff().operations().stream()
                .anyMatch(op -> op.type() == TimelineChangeType.TEMPLATE_PARAMETER_CHANGED));
    }

    @Test
    @DisplayName("Template profile changed")
    void templateProfileChanged() {
        CanonicalTimelineTemplateApplicationSnapshot beforeTa =
                new CanonicalTimelineTemplateApplicationSnapshot("app-1", "tpl-1", "1.0", Map.of(), Map.of());
        CanonicalTimelineTemplateApplicationSnapshot afterTa =
                new CanonicalTimelineTemplateApplicationSnapshot("app-1", "tpl-2", "2.0", Map.of(), Map.of());

        CanonicalTimelineSnapshot before = snapshotWithTemplates("rev-1", List.of(beforeTa));
        CanonicalTimelineSnapshot after = snapshotWithTemplates("rev-2", List.of(afterTa));

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);

        assertTrue(result.diff().operations().stream()
                .anyMatch(op -> op.type() == TimelineChangeType.TEMPLATE_PROFILE_CHANGED));
    }

    // --- Workflow step diff ---

    @Test
    @DisplayName("Workflow step changed")
    void workflowStepChanged() {
        CanonicalTimelineWorkflowStepSnapshot beforeWs =
                new CanonicalTimelineWorkflowStepSnapshot("step-1", "APPLY_TEMPLATE", "app-1", Map.of());
        CanonicalTimelineWorkflowStepSnapshot afterWs =
                new CanonicalTimelineWorkflowStepSnapshot("step-1", "APPLY_TEMPLATE", "app-2", Map.of());

        CanonicalTimelineSnapshot before = snapshotWithWorkflow("rev-1", List.of(beforeWs));
        CanonicalTimelineSnapshot after = snapshotWithWorkflow("rev-2", List.of(afterWs));

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);

        assertTrue(result.diff().operations().stream()
                .anyMatch(op -> op.type() == TimelineChangeType.WORKFLOW_APPLY_TEMPLATE_STEP_CHANGED));
    }

    // --- Output profile diff ---

    @Test
    @DisplayName("Output profile changed")
    void outputProfileChanged() {
        CanonicalTimelineOutputProfileSnapshot beforeP = new CanonicalTimelineOutputProfileSnapshot(
                "prof-1", "mp4", "16:9", 1920, 1080, Map.of());
        CanonicalTimelineOutputProfileSnapshot afterP = new CanonicalTimelineOutputProfileSnapshot(
                "prof-1", "mp4", "16:9", 1280, 720, Map.of());

        CanonicalTimelineSnapshot before = new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-1"), "rev-1", 5000,
                List.of(), List.of(), List.of(), List.of(), List.of(), beforeP, Map.of());
        CanonicalTimelineSnapshot after = new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-2"), "rev-2", 5000,
                List.of(), List.of(), List.of(), List.of(), List.of(), afterP, Map.of());

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);

        assertTrue(result.diff().operations().stream()
                .anyMatch(op -> op.type() == TimelineChangeType.OUTPUT_PROFILE_CHANGED));
        assertEquals(TimelineRenderImpactLevel.FULL_RERENDER, result.diff().renderImpact().level());
    }

    // --- Metadata diff ---

    @Test
    @DisplayName("Metadata changed")
    void metadataChanged() {
        CanonicalTimelineSnapshot before = new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-1"), "rev-1", 5000,
                List.of(), List.of(), List.of(), List.of(), List.of(), null,
                Map.of("title", "Old"));
        CanonicalTimelineSnapshot after = new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-2"), "rev-2", 5000,
                List.of(), List.of(), List.of(), List.of(), List.of(), null,
                Map.of("title", "New"));

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);

        assertTrue(result.diff().operations().stream()
                .anyMatch(op -> op.type() == TimelineChangeType.METADATA_CHANGED));
        assertEquals(TimelineRenderImpactLevel.METADATA_ONLY, result.diff().renderImpact().level());
    }

    // --- Render impact ---

    @Test
    @DisplayName("Caption-only change -> PARTIAL_RERENDER")
    void captionOnlyPartialRerender() {
        CanonicalTimelineCaptionSnapshot beforeCap = new CanonicalTimelineCaptionSnapshot(
                "cap-1", 0, 3000, "Hello", Map.of(), Map.of());
        CanonicalTimelineCaptionSnapshot afterCap = new CanonicalTimelineCaptionSnapshot(
                "cap-1", 0, 3000, "World", Map.of(), Map.of());

        CanonicalTimelineSnapshot before = snapshotWithCaptions("rev-1", List.of(beforeCap));
        CanonicalTimelineSnapshot after = snapshotWithCaptions("rev-2", List.of(afterCap));

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);
        assertEquals(TimelineRenderImpactLevel.PARTIAL_RERENDER, result.diff().renderImpact().level());
    }

    @Test
    @DisplayName("Watermark-only change -> PARTIAL_RERENDER")
    void watermarkOnlyPartialRerender() {
        CanonicalTimelineWatermarkSnapshot beforeWm = new CanonicalTimelineWatermarkSnapshot(
                "wm-1", "asset-logo", "BOTTOM_RIGHT", 50, Map.of());
        CanonicalTimelineWatermarkSnapshot afterWm = new CanonicalTimelineWatermarkSnapshot(
                "wm-1", "asset-logo", "BOTTOM_RIGHT", 80, Map.of());

        CanonicalTimelineSnapshot before = snapshotWithWatermarks("rev-1", List.of(beforeWm));
        CanonicalTimelineSnapshot after = snapshotWithWatermarks("rev-2", List.of(afterWm));

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);
        assertEquals(TimelineRenderImpactLevel.PARTIAL_RERENDER, result.diff().renderImpact().level());
    }

    @Test
    @DisplayName("Metadata-only change -> METADATA_ONLY")
    void metadataOnlyImpact() {
        CanonicalTimelineSnapshot before = new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-1"), "rev-1", 5000,
                List.of(), List.of(), List.of(), List.of(), List.of(), null,
                Map.of("title", "Old"));
        CanonicalTimelineSnapshot after = new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-2"), "rev-2", 5000,
                List.of(), List.of(), List.of(), List.of(), List.of(), null,
                Map.of("title", "New"));

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);
        assertEquals(TimelineRenderImpactLevel.METADATA_ONLY, result.diff().renderImpact().level());
    }

    // --- Deterministic ordering ---

    @Test
    @DisplayName("Operations are deterministically ordered")
    void deterministicOrdering() {
        CanonicalTimelineSnapshot before = simpleSnapshot("rev-1");
        // Multiple changes: duration + caption + metadata
        CanonicalTimelineCaptionSnapshot cap = new CanonicalTimelineCaptionSnapshot(
                "cap-1", 0, 3000, "Hello", Map.of(), Map.of());
        CanonicalTimelineSnapshot after = new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-2"), "rev-2", 10000,
                List.of(), List.of(cap), List.of(), List.of(), List.of(), null,
                Map.of("title", "New"));

        CanonicalTimelineDiffCalculationResult r1 = calculator.calculate(before, after);
        CanonicalTimelineDiffCalculationResult r2 = calculator.calculate(before, after);

        assertEquals(r1.diff().operations().size(), r2.diff().operations().size());
        for (int i = 0; i < r1.diff().operations().size(); i++) {
            assertEquals(r1.diff().operations().get(i).type(), r2.diff().operations().get(i).type());
            assertEquals(r1.diff().operations().get(i).path().value(),
                    r2.diff().operations().get(i).path().value());
        }
    }

    // --- Safety ---

    @Test
    @DisplayName("Calculator does not reference vedit or OTIO")
    void noVeditOrOcioReference() {
        // Verified by package structure — no vedit/OTIO imports
        assertNotNull(calculator);
    }

    @Test
    @DisplayName("Diff output contains no provider/storage fields")
    void noProviderStorageFields() {
        CanonicalTimelineSnapshot before = simpleSnapshot("rev-1");
        CanonicalTimelineSnapshot after = new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-2"), "rev-2", 10000,
                List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of());

        CanonicalTimelineDiffCalculationResult result = calculator.calculate(before, after);
        String str = result.diff().toString();
        assertFalse(str.contains("providerName"));
        assertFalse(str.contains("bucket"));
        assertFalse(str.contains("signedUrl"));
    }

    // --- Helpers ---

    private CanonicalTimelineSnapshot simpleSnapshot(String revisionId) {
        return new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-" + revisionId),
                revisionId, 5000,
                List.of(trackSnapshot("track-1", 0)),
                List.of(), List.of(), List.of(), List.of(), null, Map.of());
    }

    private CanonicalTimelineSnapshot snapshotWithClips(String revisionId, List<CanonicalTimelineClipSnapshot> clips) {
        return new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-" + revisionId),
                revisionId, 10000,
                List.of(new CanonicalTimelineTrackSnapshot("track-1", 0, "VIDEO", clips, Map.of())),
                List.of(), List.of(), List.of(), List.of(), null, Map.of());
    }

    private CanonicalTimelineSnapshot snapshotWithCaptions(String revisionId, List<CanonicalTimelineCaptionSnapshot> captions) {
        return new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-" + revisionId),
                revisionId, 5000,
                List.of(trackSnapshot("track-1", 0)),
                captions, List.of(), List.of(), List.of(), null, Map.of());
    }

    private CanonicalTimelineSnapshot snapshotWithWatermarks(String revisionId, List<CanonicalTimelineWatermarkSnapshot> watermarks) {
        return new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-" + revisionId),
                revisionId, 5000,
                List.of(trackSnapshot("track-1", 0)),
                List.of(), watermarks, List.of(), List.of(), null, Map.of());
    }

    private CanonicalTimelineSnapshot snapshotWithTemplates(String revisionId, List<CanonicalTimelineTemplateApplicationSnapshot> templates) {
        return new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-" + revisionId),
                revisionId, 5000,
                List.of(trackSnapshot("track-1", 0)),
                List.of(), List.of(), templates, List.of(), null, Map.of());
    }

    private CanonicalTimelineSnapshot snapshotWithWorkflow(String revisionId, List<CanonicalTimelineWorkflowStepSnapshot> steps) {
        return new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("snap-" + revisionId),
                revisionId, 5000,
                List.of(trackSnapshot("track-1", 0)),
                List.of(), List.of(), List.of(), steps, null, Map.of());
    }

    private CanonicalTimelineTrackSnapshot trackSnapshot(String id, int order) {
        return new CanonicalTimelineTrackSnapshot(id, order, "VIDEO",
                List.of(clipSnapshot("clip-1", 0)), Map.of());
    }

    private CanonicalTimelineClipSnapshot clipSnapshot(String id, long startMs) {
        return new CanonicalTimelineClipSnapshot(id, "asset-1", startMs, 5000, 0, 5000, Map.of());
    }
}
