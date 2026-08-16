package com.example.platform.render.domain.timeline.diff.application;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.render.domain.timeline.canonical.TextElement;
import com.example.platform.render.domain.timeline.canonical.TestTextElements;
import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import com.example.platform.render.domain.timeline.canonical.TimelineMetadata;
import com.example.platform.render.domain.timeline.diff.TimelineChangeOperation;
import com.example.platform.render.domain.timeline.diff.TimelineChangeOperationId;
import com.example.platform.render.domain.timeline.diff.TimelineChangePath;
import com.example.platform.render.domain.timeline.diff.TimelineChangePayload;
import com.example.platform.render.domain.timeline.diff.TimelineChangeScope;
import com.example.platform.render.domain.timeline.diff.TimelineChangeType;
import com.example.platform.render.domain.timeline.diff.TimelineMergePolicy;
import com.example.platform.render.domain.timeline.diff.TimelinePatch;
import com.example.platform.render.domain.timeline.diff.TimelinePatchId;
import com.example.platform.render.domain.timeline.diff.calculation.CanonicalTimelineSnapshot;
import com.example.platform.render.domain.timeline.diff.calculation.TimelineSnapshotConverter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROADMAP_19 CORR-1 (TIMELINE_PATCH_PRESERVES_UNTOUCHED_CANONICAL_FIELDS_V1):
 * every non-text TimelinePatch operation must preserve TextElement state
 * exactly. ZERO silent textElements = List.of() during reconstruction.
 */
class TimelinePatchTextPreservationTest {

    private final TimelinePatchApplier applier = new TimelinePatchApplier();

    private TimelineDocument baseDocumentWithText() {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(), TimelineMetadata.empty(), AudioMix.EMPTY, List.of(),
                List.of(TestTextElements.textElement("elem-1")));
    }

    private CanonicalTimelineSnapshot baseSnapshot() {
        return TimelineSnapshotConverter.toSnapshot(baseDocumentWithText(), "rev-1");
    }

    private TimelineChangeOperation change(TimelineChangeType type, String path, String before, String after) {
        return new TimelineChangeOperation(
                new TimelineChangeOperationId("op-1"), type, TimelineChangeScope.TIMELINE,
                new TimelineChangePath(path),
                before != null ? TimelineChangePayload.ofString(before) : TimelineChangePayload.empty(),
                after != null ? TimelineChangePayload.ofString(after) : TimelineChangePayload.empty(),
                Map.of());
    }

    private TimelinePatch patch(TimelineChangeOperation... ops) {
        return new TimelinePatch(
                new TimelinePatchId("patch-1"), "rev-1", List.of(ops),
                TimelineMergePolicy.MERGE_IF_COMPATIBLE, Map.of());
    }

    private void assertTextPreserved(CanonicalTimelineSnapshot after, String label) {
        assertEquals(1, after.textElements().size(), label + ": TextElement must be preserved");
        assertEquals("elem-1", after.textElements().get(0).id().value(), label + ": identity stable");
    }

    @Test
    void durationPatchPreservesTextElements() {
        var op = change(TimelineChangeType.TIMELINE_DURATION_CHANGED, "timeline.duration", "0", "10");
        var result = applier.apply(baseSnapshot(), patch(op));
        assertTrue(result.status() == TimelinePatchApplicationStatus.APPLIED
                || result.status() == TimelinePatchApplicationStatus.NO_OP);
        assertTextPreserved(result.patchedSnapshot(), "duration patch");
    }

    @Test
    void metadataPatchPreservesTextElements() {
        var op = change(TimelineChangeType.METADATA_CHANGED, "timeline.metadata.k", null, "v");
        var result = applier.apply(baseSnapshot(), patch(op));
        assertTextPreserved(result.patchedSnapshot(), "metadata patch");
    }

    @Test
    void trackPatchPreservesTextElements() {
        var op = change(TimelineChangeType.TRACK_ADDED, "timeline.tracks.track-2", null, "VIDEO");
        var result = applier.apply(baseSnapshot(), patch(op));
        assertTextPreserved(result.patchedSnapshot(), "track patch");
    }

    @Test
    void outputProfilePatchPreservesTextElements() {
        var op = change(TimelineChangeType.OUTPUT_PROFILE_CHANGED, "timeline.outputProfile.width", "640", "1280");
        var result = applier.apply(baseSnapshot(), patch(op));
        assertTextPreserved(result.patchedSnapshot(), "output profile patch");
    }

    @Test
    void finalApplyRebuildPreservesTextElements() {
        // NON-EMPTY patch -> apply() must reach the final rebuild line
        // `CanonicalTimelineSnapshot patched = new CanonicalTimelineSnapshot(...)`
        // and carry textElements through reconstruction. An empty patch would
        // short-circuit to NO_OP and never exercise the rebuild path.
        var op = change(TimelineChangeType.TIMELINE_DURATION_CHANGED, "timeline.duration", "0", "10");
        var result = applier.apply(baseSnapshot(), patch(op));
        assertEquals(TimelinePatchApplicationStatus.APPLIED, result.status(),
                "final rebuild must apply a non-empty patch");
        assertTextPreserved(result.patchedSnapshot(), "final rebuild (non-empty patch)");
    }

    @Test
    void contentSemanticsUnchangedByUnrelatedPatch() {
        var op = change(TimelineChangeType.METADATA_CHANGED, "timeline.metadata.k", null, "v");
        var result = applier.apply(baseSnapshot(), patch(op));
        TextElement before = baseSnapshot().textElements().get(0);
        TextElement after = result.patchedSnapshot().textElements().get(0);
        assertEquals(before, after, "unrelated patch must not alter TextElement semantics");
    }
}
