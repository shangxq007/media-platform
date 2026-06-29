package com.example.platform.render.domain.timeline.diff.application;

import com.example.platform.render.domain.timeline.diff.*;
import com.example.platform.render.domain.timeline.diff.calculation.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class TimelinePatchApplierTest {

    private TimelinePatchApplier applier;
    private CanonicalTimelineDiffCalculator diffCalculator;

    @BeforeEach
    void setUp() {
        applier = new TimelinePatchApplier();
        diffCalculator = new CanonicalTimelineDiffCalculator();
    }

    @Test @DisplayName("Null base fails")
    void nullBaseFails() {
        TimelinePatch patch = new TimelinePatch(new TimelinePatchId("p1"), "rev-1", List.of(), TimelineMergePolicy.FAIL_FAST, Map.of());
        assertEquals(TimelinePatchApplicationStatus.VALIDATION_FAILED, applier.apply(null, patch).status());
    }

    @Test @DisplayName("Null patch fails")
    void nullPatchFails() {
        assertEquals(TimelinePatchApplicationStatus.VALIDATION_FAILED, applier.apply(snap("rev-1"), null).status());
    }

    @Test @DisplayName("Base revision mismatch fails")
    void baseRevisionMismatch() {
        TimelinePatch patch = new TimelinePatch(new TimelinePatchId("p1"), "rev-WRONG",
                List.of(op(TimelineChangeType.METADATA_CHANGED, TimelineChangeScope.METADATA, "timeline.metadata.k", "old", "new")),
                TimelineMergePolicy.FAIL_FAST, Map.of());
        assertEquals(TimelinePatchApplicationStatus.VALIDATION_FAILED, applier.apply(snap("rev-1"), patch).status());
    }

    @Test @DisplayName("Empty operations = NO_OP")
    void emptyOperations() {
        TimelinePatch patch = new TimelinePatch(new TimelinePatchId("p1"), "rev-1", List.of(), TimelineMergePolicy.FAIL_FAST, Map.of());
        assertEquals(TimelinePatchApplicationStatus.NO_OP, applier.apply(snap("rev-1"), patch).status());
    }

    @Test @DisplayName("Invalid path fails")
    void invalidPath() {
        TimelinePatch patch = new TimelinePatch(new TimelinePatchId("p1"), "rev-1",
                List.of(op(TimelineChangeType.METADATA_CHANGED, TimelineChangeScope.METADATA, "badpath", "old", "new")),
                TimelineMergePolicy.FAIL_FAST, Map.of());
        assertEquals(TimelinePatchApplicationStatus.VALIDATION_FAILED, applier.apply(snap("rev-1"), patch).status());
    }

    @Test @DisplayName("Duration changed")
    void durationChanged() {
        TimelinePatchApplicationResult r = applier.apply(snap("rev-1"), patchOf(TimelineChangeType.TIMELINE_DURATION_CHANGED, "timeline.durationMs", "5000", "10000"));
        assertTrue(r.isApplied());
        assertEquals(10000, r.patchedSnapshot().durationMs());
    }

    @Test @DisplayName("Metadata changed")
    void metadataChanged() {
        TimelinePatchApplicationResult r = applier.apply(snap("rev-1"), patchOf(TimelineChangeType.METADATA_CHANGED, "timeline.metadata.title", "Old", "title=New"));
        assertTrue(r.isApplied());
        assertEquals("New", r.patchedSnapshot().safeMetadata().get("title"));
    }

    @Test @DisplayName("Output profile changed")
    void outputProfileChanged() {
        CanonicalTimelineOutputProfileSnapshot p = new CanonicalTimelineOutputProfileSnapshot("p1", "mp4", "16:9", 1920, 1080, Map.of());
        CanonicalTimelineSnapshot base = new CanonicalTimelineSnapshot(new CanonicalTimelineSnapshotId("s1"), "rev-1", 5000,
                List.of(), List.of(), List.of(), List.of(), List.of(), p, Map.of());
        TimelinePatchApplicationResult r = applier.apply(base, patchOf(TimelineChangeType.OUTPUT_PROFILE_CHANGED, "timeline.outputProfile", "1920x1080", "1280x720"));
        assertTrue(r.isApplied());
        assertEquals(1280, r.patchedSnapshot().outputProfile().width());
    }

    @Test @DisplayName("Track added")
    void trackAdded() {
        TimelinePatchApplicationResult r = applier.apply(snap("rev-1"), patchOf(TimelineChangeType.TRACK_ADDED, "timeline.tracks.track-new", null, "track-new"));
        assertTrue(r.isApplied());
        assertEquals(2, r.patchedSnapshot().tracks().size());
    }

    @Test @DisplayName("Track removed")
    void trackRemoved() {
        TimelinePatchApplicationResult r = applier.apply(snap("rev-1"), patchOf(TimelineChangeType.TRACK_REMOVED, "timeline.tracks.track-1", "track-1", null));
        assertTrue(r.isApplied());
        assertTrue(r.patchedSnapshot().tracks().isEmpty());
    }

    @Test @DisplayName("Clip moved")
    void clipMoved() {
        TimelinePatchApplicationResult r = applier.apply(snap("rev-1"), patchOf(TimelineChangeType.CLIP_MOVED, "timeline.tracks.track-1.clips.clip-1.startMs", "0", "2000"));
        assertTrue(r.isApplied());
    }

    @Test @DisplayName("Caption text changed")
    void captionChanged() {
        CanonicalTimelineCaptionSnapshot cap = new CanonicalTimelineCaptionSnapshot("cap-1", 0, 3000, "Hello", Map.of(), Map.of());
        CanonicalTimelineSnapshot base = new CanonicalTimelineSnapshot(new CanonicalTimelineSnapshotId("s1"), "rev-1", 5000,
                List.of(), List.of(cap), List.of(), List.of(), List.of(), null, Map.of());
        TimelinePatchApplicationResult r = applier.apply(base, patchOf(TimelineChangeType.CAPTION_SEGMENT_CHANGED, "timeline.captions.cap-1.text", "Hello", "World"));
        assertTrue(r.isApplied());
        assertEquals("World", r.patchedSnapshot().captions().get(0).text());
    }

    @Test @DisplayName("Missing caption fails")
    void missingCaption() {
        TimelinePatchApplicationResult r = applier.apply(snap("rev-1"), patchOf(TimelineChangeType.CAPTION_SEGMENT_CHANGED, "timeline.captions.missing.text", "old", "new"));
        assertEquals(TimelinePatchApplicationStatus.VALIDATION_FAILED, r.status());
    }

    @Test @DisplayName("Watermark opacity changed")
    void watermarkChanged() {
        CanonicalTimelineWatermarkSnapshot wm = new CanonicalTimelineWatermarkSnapshot("wm-1", "logo", "BOTTOM_RIGHT", 50, Map.of());
        CanonicalTimelineSnapshot base = new CanonicalTimelineSnapshot(new CanonicalTimelineSnapshotId("s1"), "rev-1", 5000,
                List.of(), List.of(), List.of(wm), List.of(), List.of(), null, Map.of());
        TimelinePatchApplicationResult r = applier.apply(base, patchOf(TimelineChangeType.WATERMARK_CHANGED, "timeline.watermarks.wm-1", "BOTTOM_RIGHT:50", "TOP_LEFT:80"));
        assertTrue(r.isApplied());
        assertEquals("TOP_LEFT", r.patchedSnapshot().watermarks().get(0).position());
        assertEquals(80, r.patchedSnapshot().watermarks().get(0).opacityPercent());
    }

    @Test @DisplayName("Template parameter changed")
    void templateParamChanged() {
        CanonicalTimelineTemplateApplicationSnapshot ta = new CanonicalTimelineTemplateApplicationSnapshot("app-1", "tpl-1", "1.0", Map.of("fontSize", "24"), Map.of());
        CanonicalTimelineSnapshot base = new CanonicalTimelineSnapshot(new CanonicalTimelineSnapshotId("s1"), "rev-1", 5000,
                List.of(), List.of(), List.of(), List.of(ta), List.of(), null, Map.of());
        TimelinePatchApplicationResult r = applier.apply(base, patchOf(TimelineChangeType.TEMPLATE_PARAMETER_CHANGED,
                "timeline.templateApplications.app-1.parameters.fontSize", "24", "fontSize=32"));
        assertTrue(r.isApplied());
        assertEquals("32", r.patchedSnapshot().templateApplications().get(0).parameters().get("fontSize"));
    }

    @Test @DisplayName("Round-trip: duration diff -> patch -> apply")
    void roundTripDuration() {
        CanonicalTimelineSnapshot before = snap("rev-1");
        CanonicalTimelineSnapshot after = new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("s2"), "rev-2", 10000,
                List.of(track("track-1", 0)), List.of(), List.of(), List.of(), List.of(), null, Map.of());

        CanonicalTimelineDiffCalculationResult diff = diffCalculator.calculate(before, after);
        assertTrue(diff.successful());
        assertFalse(diff.diff().isNoOp());

        TimelinePatch patch = new TimelinePatch(new TimelinePatchId("p1"), before.revisionId(),
                diff.diff().operations(), TimelineMergePolicy.FAIL_FAST, Map.of());
        TimelinePatchApplicationResult result = applier.apply(before, patch);
        assertTrue(result.isApplied(), "Patch should apply: " + result.issues());
        assertEquals(10000, result.patchedSnapshot().durationMs());
    }

    @Test @DisplayName("Round-trip: metadata diff -> patch -> apply")
    void roundTripMetadata() {
        CanonicalTimelineSnapshot before = new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("s1"), "rev-1", 5000,
                List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of("title", "Old"));
        CanonicalTimelineSnapshot after = new CanonicalTimelineSnapshot(
                new CanonicalTimelineSnapshotId("s2"), "rev-2", 5000,
                List.of(), List.of(), List.of(), List.of(), List.of(), null, Map.of("title", "New"));

        CanonicalTimelineDiffCalculationResult diff = diffCalculator.calculate(before, after);
        assertTrue(diff.successful());

        TimelinePatch patch = new TimelinePatch(new TimelinePatchId("p1"), before.revisionId(),
                diff.diff().operations(), TimelineMergePolicy.FAIL_FAST, Map.of());
        TimelinePatchApplicationResult result = applier.apply(before, patch);
        assertTrue(result.isApplied(), "Patch should apply: " + result.issues());
        assertEquals("New", result.patchedSnapshot().safeMetadata().get("title"));
    }

    @Test @DisplayName("No provider/storage fields in result")
    void noProviderStorage() {
        TimelinePatchApplicationResult r = applier.apply(snap("rev-1"), patchOf(TimelineChangeType.METADATA_CHANGED, "timeline.metadata.k", "v1", "k=v2"));
        String s = r.toString();
        assertFalse(s.contains("providerName"));
        assertFalse(s.contains("bucket"));
        assertFalse(s.contains("signedUrl"));
    }

    private CanonicalTimelineSnapshot snap(String revId) {
        return new CanonicalTimelineSnapshot(new CanonicalTimelineSnapshotId("snap-" + revId), revId, 5000,
                List.of(track("track-1", 0)), List.of(), List.of(), List.of(), List.of(), null, Map.of());
    }

    private CanonicalTimelineTrackSnapshot track(String id, int order) {
        return new CanonicalTimelineTrackSnapshot(id, order, "VIDEO",
                List.of(new CanonicalTimelineClipSnapshot("clip-1", "asset-1", 0, 5000, 0, 5000, Map.of())), Map.of());
    }

    private TimelineChangeOperation op(TimelineChangeType type, TimelineChangeScope scope, String path, String before, String after) {
        return new TimelineChangeOperation(new TimelineChangeOperationId("op-" + System.nanoTime()),
                type, scope, new TimelineChangePath(path),
                before != null ? TimelineChangePayload.ofString(before) : TimelineChangePayload.empty(),
                after != null ? TimelineChangePayload.ofString(after) : TimelineChangePayload.empty(), Map.of());
    }

    private TimelinePatch patchOf(TimelineChangeType type, String path, String before, String after) {
        TimelineChangeScope scope = switch (type) {
            case TIMELINE_DURATION_CHANGED -> TimelineChangeScope.TIMELINE;
            case TRACK_ADDED, TRACK_REMOVED, TRACK_REORDERED -> TimelineChangeScope.TRACK;
            case CLIP_ADDED, CLIP_REMOVED, CLIP_MOVED, CLIP_TRIMMED -> TimelineChangeScope.CLIP;
            case CAPTION_SEGMENT_CHANGED -> TimelineChangeScope.CAPTION;
            case TEXT_STYLE_CHANGED -> TimelineChangeScope.TEXT_OVERLAY;
            case WATERMARK_CHANGED -> TimelineChangeScope.WATERMARK;
            case TEMPLATE_PARAMETER_CHANGED, TEMPLATE_PROFILE_CHANGED -> TimelineChangeScope.TEMPLATE_APPLICATION;
            case OUTPUT_PROFILE_CHANGED -> TimelineChangeScope.OUTPUT_PROFILE;
            case METADATA_CHANGED -> TimelineChangeScope.METADATA;
            default -> TimelineChangeScope.TIMELINE;
        };
        return new TimelinePatch(new TimelinePatchId("patch-test"), "rev-1",
                List.of(op(type, scope, path, before, after)), TimelineMergePolicy.FAIL_FAST, Map.of());
    }
}
