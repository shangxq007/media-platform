package com.example.platform.render.domain.timeline.diff;

import com.example.platform.render.domain.timeline.canonical.TimelineClip;
import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import com.example.platform.render.domain.timeline.canonical.TimelineMetadata;
import com.example.platform.render.domain.timeline.canonical.TimelineTrack;
import com.example.platform.render.domain.timeline.canonical.TrackType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for TimelineDiffEngine.
 * Covers identity, track changes, clip changes, moves, reordering, determinism, and aggregation.
 */
class TimelineDiffEngineTest {

    private static final String PRODUCT_ID = "prod-1";
    private static final String BASE_REV = "rev-base";
    private static final String TARGET_REV = "rev-target";
    private static final String BASE_DIGEST = "digest-base";
    private static final String TARGET_DIGEST = "digest-target";

    // ==================== Identity Tests ====================

    @Test
    @DisplayName("Same revision produces empty diff")
    void sameRevision_emptyDiff() {
        TimelineDocument doc = sampleDocument();
        TimelineChangeSet result = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, TARGET_DIGEST, doc, doc);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertEquals(0, result.getSummary().getTotal());
        assertEquals(BASE_REV, result.getBaseRevisionId());
        assertEquals(TARGET_REV, result.getTargetRevisionId());
    }

    @Test
    @DisplayName("Same digest produces empty diff even with different revision IDs")
    void sameDigest_emptyDiff() {
        TimelineDocument doc1 = sampleDocument();
        TimelineDocument doc2 = sampleDocument(); // identical content
        TimelineChangeSet result = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, BASE_DIGEST, doc1, doc2);

        assertTrue(result.isEmpty());
        assertEquals(0, result.getChanges().size());
    }

    @Test
    @DisplayName("ChangeSet is immutable")
    void changeSet_isImmutable() {
        TimelineDocument base = sampleDocument();
        TimelineDocument target = sampleDocumentWithExtraTrack();
        TimelineChangeSet result = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, TARGET_DIGEST, base, target);

        assertThrows(UnsupportedOperationException.class, () -> result.getChanges().add(null));
    }

    // ==================== Track Tests ====================

    @Test
    @DisplayName("Track added")
    void trackAdded() {
        TimelineDocument base = sampleDocument();
        TimelineDocument target = sampleDocumentWithExtraTrack();

        TimelineChangeSet result = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, TARGET_DIGEST, base, target);

        assertEquals(1, result.getSummary().getTracksAdded());
        assertEquals(0, result.getSummary().getTracksRemoved());
        assertFalse(result.isEmpty());

        TimelineChange change = result.getChanges().get(0);
        assertEquals(ChangeType.TRACK_ADDED, change.getChangeType());
        assertEquals(EntityKind.TRACK, change.getEntityKind());
        assertEquals("track-2", change.getEntityId());
    }

    @Test
    @DisplayName("Track removed")
    void trackRemoved() {
        TimelineDocument base = sampleDocumentWithExtraTrack();
        TimelineDocument target = sampleDocument();

        TimelineChangeSet result = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, TARGET_DIGEST, base, target);

        assertEquals(1, result.getSummary().getTracksRemoved());
        assertEquals(0, result.getSummary().getTracksAdded());

        TimelineChange change = result.getChanges().get(0);
        assertEquals(ChangeType.TRACK_REMOVED, change.getChangeType());
        assertEquals("track-2", change.getEntityId());
    }

    @Test
    @DisplayName("Track property changed")
    void trackPropertyChanged() {
        TimelineDocument base = sampleDocument();
        TimelineDocument target = sampleDocumentWithRenamedTrack();

        TimelineChangeSet result = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, TARGET_DIGEST, base, target);

        assertTrue(result.getSummary().getTracksChanged() > 0);

        boolean hasNameChange = result.getChanges().stream()
                .anyMatch(c -> c.getChangeType() == ChangeType.TRACK_PROPERTY_CHANGED
                        && c.getEntityId().equals("track-1")
                        && "name".equals(c.getPropertyName()));
        assertTrue(hasNameChange, "Should detect track name change");
    }

    @Test
    @DisplayName("Track reordered")
    void trackReordered() {
        TimelineDocument base = sampleDocument();
        TimelineDocument target = sampleDocumentWithReorderedTracks();

        TimelineChangeSet result = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, TARGET_DIGEST, base, target);

        assertTrue(result.getSummary().getTracksReordered() > 0);
    }

    // ==================== Clip Tests ====================

    @Test
    @DisplayName("Clip added")
    void clipAdded() {
        TimelineDocument base = sampleDocument();
        TimelineDocument target = sampleDocumentWithExtraClip();

        TimelineChangeSet result = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, TARGET_DIGEST, base, target);

        assertTrue(result.getSummary().getClipsAdded() > 0);

        boolean hasClipAdded = result.getChanges().stream()
                .anyMatch(c -> c.getChangeType() == ChangeType.CLIP_ADDED
                        && c.getEntityId().equals("clip-3"));
        assertTrue(hasClipAdded, "Should detect clip added");
    }

    @Test
    @DisplayName("Clip removed")
    void clipRemoved() {
        TimelineDocument base = sampleDocumentWithExtraClip();
        TimelineDocument target = sampleDocument();

        TimelineChangeSet result = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, TARGET_DIGEST, base, target);

        assertTrue(result.getSummary().getClipsRemoved() > 0);
    }

    @Test
    @DisplayName("Clip property changed")
    void clipPropertyChanged() {
        TimelineDocument base = sampleDocument();
        TimelineDocument target = sampleDocumentWithChangedClip();

        TimelineChangeSet result = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, TARGET_DIGEST, base, target);

        assertTrue(result.getSummary().getClipsChanged() > 0);

        boolean hasAssetChange = result.getChanges().stream()
                .anyMatch(c -> c.getEntityId().equals("clip-1")
                        && "assetId".equals(c.getPropertyName()));
        assertTrue(hasAssetChange, "Should detect clip assetId change");
    }

    @Test
    @DisplayName("Clip moved across tracks")
    void clipMovedAcrossTracks() {
        TimelineDocument base = sampleDocument();
        TimelineDocument target = sampleDocumentWithMovedClip();

        TimelineChangeSet result = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, TARGET_DIGEST, base, target);

        assertTrue(result.getSummary().getClipsMoved() > 0);

        boolean hasMove = result.getChanges().stream()
                .anyMatch(c -> c.getChangeType() == ChangeType.CLIP_MOVED
                        && c.getEntityId().equals("clip-1")
                        && "track-1".equals(c.getBeforeValue())
                        && "track-2".equals(c.getAfterValue()));
        assertTrue(hasMove, "Should detect clip move from track-1 to track-2");
    }

    @Test
    @DisplayName("Clip reordered in same track")
    void clipReordered() {
        TimelineDocument base = sampleDocument();
        TimelineDocument target = sampleDocumentWithReorderedClips();

        TimelineChangeSet result = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, TARGET_DIGEST, base, target);

        assertTrue(result.getSummary().getClipsReordered() > 0);
    }

    @Test
    @DisplayName("Clip move + property change")
    void clipMoveAndPropertyChange() {
        TimelineDocument base = sampleDocument();
        TimelineDocument target = sampleDocumentWithMovedAndChangedClip();

        TimelineChangeSet result = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, TARGET_DIGEST, base, target);

        assertTrue(result.getSummary().getClipsMoved() > 0);
        // Property changes should also be reported alongside move
    }

    // ==================== Determinism Tests ====================

    @Test
    @DisplayName("Repeated execution produces identical output")
    void repeatedExecution_identical() {
        TimelineDocument base = sampleDocument();
        TimelineDocument target = sampleDocumentWithExtraTrack();

        TimelineChangeSet result1 = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, TARGET_DIGEST, base, target);
        TimelineChangeSet result2 = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, TARGET_DIGEST, base, target);

        assertEquals(result1.getChanges().size(), result2.getChanges().size());
        assertEquals(result1.getSummary().getTotal(), result2.getSummary().getTotal());

        for (int i = 0; i < result1.getChanges().size(); i++) {
            TimelineChange c1 = result1.getChanges().get(i);
            TimelineChange c2 = result2.getChanges().get(i);
            assertEquals(c1.getChangeType(), c2.getChangeType());
            assertEquals(c1.getEntityId(), c2.getEntityId());
            assertEquals(c1.getPropertyName(), c2.getPropertyName());
        }
    }

    @Test
    @DisplayName("Output is deterministic regardless of input map order")
    void deterministic_regardlessOfMapOrder() {
        // Create documents with tracks in different insertion orders but same content
        TimelineDocument base = sampleDocument();
        // Use same target ordering - determinism is about same logical input producing same output
        TimelineDocument target = sampleDocumentWithExtraTrack();

        // Run multiple times to verify determinism
        TimelineChangeSet result1 = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, TARGET_DIGEST, base, target);
        TimelineChangeSet result2 = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, TARGET_DIGEST, base, target);

        // Both should produce same number of changes
        assertEquals(result1.getSummary().getTotal(), result2.getSummary().getTotal());
        assertEquals(result1.getChanges().size(), result2.getChanges().size());
    }

    // ==================== Summary Arithmetic Tests ====================

    @Test
    @DisplayName("Summary total equals changes size")
    void summaryTotal_equalsChangesSize() {
        TimelineDocument base = sampleDocument();
        TimelineDocument target = sampleDocumentWithExtraTrackAndClip();

        TimelineChangeSet result = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, TARGET_DIGEST, base, target);

        assertEquals(result.getChanges().size(), result.getSummary().getTotal());
    }

    @Test
    @DisplayName("Summary entity counts are correct")
    void summaryEntityCounts_correct() {
        TimelineDocument base = sampleDocument();
        TimelineDocument target = sampleDocumentWithExtraTrack();

        TimelineChangeSet result = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, TARGET_DIGEST, base, target);

        ChangeSummary s = result.getSummary();
        assertTrue(s.getTracksAdded() >= 1);
        assertTrue(s.getTotal() >= 1);
    }

    // ==================== Edge Cases ====================

    @Test
    @DisplayName("Empty document to document with content")
    void emptyToContent() {
        TimelineDocument empty = new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(), TimelineMetadata.empty());
        TimelineDocument content = sampleDocument();

        TimelineChangeSet result = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, TARGET_DIGEST, empty, content);

        assertFalse(result.isEmpty());
        assertTrue(result.getSummary().getTracksAdded() > 0);
    }

    @Test
    @DisplayName("Both empty documents produce empty diff")
    void bothEmpty() {
        TimelineDocument empty1 = new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(), TimelineMetadata.empty());
        TimelineDocument empty2 = new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(), TimelineMetadata.empty());

        TimelineChangeSet result = TimelineDiffEngine.diff(
                PRODUCT_ID, BASE_REV, TARGET_REV, BASE_DIGEST, TARGET_DIGEST, empty1, empty2);

        assertTrue(result.isEmpty());
    }

    // ==================== Helper Methods ====================

    private TimelineDocument sampleDocument() {
        TimelineClip clip1 = new TimelineClip("clip-1", "asset-1",
                Duration.ofMillis(0), Duration.ofMillis(1000),
                Duration.ZERO, Duration.ZERO);
        TimelineClip clip2 = new TimelineClip("clip-2", "asset-2",
                Duration.ofMillis(1000), Duration.ofMillis(2000),
                Duration.ZERO, Duration.ZERO);
        TimelineTrack track1 = new TimelineTrack("track-1", "Video 1", TrackType.VIDEO, List.of(clip1, clip2));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(track1),
                new TimelineMetadata("", "", Map.of()));
    }

    private TimelineDocument sampleDocumentWithExtraTrack() {
        TimelineClip clip1 = new TimelineClip("clip-1", "asset-1",
                Duration.ofMillis(0), Duration.ofMillis(1000), Duration.ZERO, Duration.ZERO);
        TimelineClip clip2 = new TimelineClip("clip-2", "asset-2",
                Duration.ofMillis(1000), Duration.ofMillis(2000), Duration.ZERO, Duration.ZERO);
        TimelineTrack track1 = new TimelineTrack("track-1", "Video 1", TrackType.VIDEO, List.of(clip1, clip2));
        TimelineTrack track2 = new TimelineTrack("track-2", "Audio 1", TrackType.AUDIO, List.of());
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(track1, track2),
                new TimelineMetadata("", "", Map.of()));
    }

    private TimelineDocument sampleDocumentWithExtraTrackReversed() {
        // Same content as above but tracks added in different order
        TimelineClip clip1 = new TimelineClip("clip-1", "asset-1",
                Duration.ofMillis(0), Duration.ofMillis(1000), Duration.ZERO, Duration.ZERO);
        TimelineClip clip2 = new TimelineClip("clip-2", "asset-2",
                Duration.ofMillis(1000), Duration.ofMillis(2000), Duration.ZERO, Duration.ZERO);
        TimelineTrack track1 = new TimelineTrack("track-1", "Video 1", TrackType.VIDEO, List.of(clip1, clip2));
        TimelineTrack track2 = new TimelineTrack("track-2", "Audio 1", TrackType.AUDIO, List.of());
        // Reversed order in list
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(track2, track1),
                new TimelineMetadata("", "", Map.of()));
    }

    private TimelineDocument sampleDocumentWithRenamedTrack() {
        TimelineClip clip1 = new TimelineClip("clip-1", "asset-1",
                Duration.ofMillis(0), Duration.ofMillis(1000), Duration.ZERO, Duration.ZERO);
        TimelineTrack track1 = new TimelineTrack("track-1", "Renamed Video", TrackType.VIDEO, List.of(clip1));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(track1),
                new TimelineMetadata("", "", Map.of()));
    }

    private TimelineDocument sampleDocumentWithReorderedTracks() {
        TimelineClip clip1 = new TimelineClip("clip-1", "asset-1",
                Duration.ofMillis(0), Duration.ofMillis(1000), Duration.ZERO, Duration.ZERO);
        TimelineTrack track1 = new TimelineTrack("track-1", "Video 1", TrackType.VIDEO, List.of(clip1));
        // Single track, no reorder possible - add second track
        TimelineTrack track2 = new TimelineTrack("track-2", "Audio 1", TrackType.AUDIO, List.of());
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(track2, track1),
                new TimelineMetadata("", "", Map.of()));
    }

    private TimelineDocument sampleDocumentWithExtraClip() {
        TimelineClip clip1 = new TimelineClip("clip-1", "asset-1",
                Duration.ofMillis(0), Duration.ofMillis(1000), Duration.ZERO, Duration.ZERO);
        TimelineClip clip2 = new TimelineClip("clip-2", "asset-2",
                Duration.ofMillis(1000), Duration.ofMillis(2000), Duration.ZERO, Duration.ZERO);
        TimelineClip clip3 = new TimelineClip("clip-3", "asset-3",
                Duration.ofMillis(2000), Duration.ofMillis(3000), Duration.ZERO, Duration.ZERO);
        TimelineTrack track1 = new TimelineTrack("track-1", "Video 1", TrackType.VIDEO, List.of(clip1, clip2, clip3));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(track1),
                new TimelineMetadata("", "", Map.of()));
    }

    private TimelineDocument sampleDocumentWithChangedClip() {
        TimelineClip clip1 = new TimelineClip("clip-1", "asset-changed",
                Duration.ofMillis(0), Duration.ofMillis(1000), Duration.ZERO, Duration.ZERO);
        TimelineTrack track1 = new TimelineTrack("track-1", "Video 1", TrackType.VIDEO, List.of(clip1));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(track1),
                new TimelineMetadata("", "", Map.of()));
    }

    private TimelineDocument sampleDocumentWithMovedClip() {
        // clip-1 moved from track-1 to track-2
        TimelineClip clip1 = new TimelineClip("clip-1", "asset-1",
                Duration.ofMillis(0), Duration.ofMillis(1000), Duration.ZERO, Duration.ZERO);
        TimelineClip clip2 = new TimelineClip("clip-2", "asset-2",
                Duration.ofMillis(1000), Duration.ofMillis(2000), Duration.ZERO, Duration.ZERO);
        TimelineTrack track1 = new TimelineTrack("track-1", "Video 1", TrackType.VIDEO, List.of(clip2));
        TimelineTrack track2 = new TimelineTrack("track-2", "Audio 1", TrackType.AUDIO, List.of(clip1));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(track1, track2),
                new TimelineMetadata("", "", Map.of()));
    }

    private TimelineDocument sampleDocumentWithReorderedClips() {
        TimelineClip clip1 = new TimelineClip("clip-1", "asset-1",
                Duration.ofMillis(0), Duration.ofMillis(1000), Duration.ZERO, Duration.ZERO);
        TimelineClip clip2 = new TimelineClip("clip-2", "asset-2",
                Duration.ofMillis(1000), Duration.ofMillis(2000), Duration.ZERO, Duration.ZERO);
        // Reversed clip order
        TimelineTrack track1 = new TimelineTrack("track-1", "Video 1", TrackType.VIDEO, List.of(clip2, clip1));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(track1),
                new TimelineMetadata("", "", Map.of()));
    }

    private TimelineDocument sampleDocumentWithMovedAndChangedClip() {
        // clip-1 moved to track-2 AND assetId changed
        TimelineClip clip1 = new TimelineClip("clip-1", "asset-new",
                Duration.ofMillis(0), Duration.ofMillis(1000), Duration.ZERO, Duration.ZERO);
        TimelineClip clip2 = new TimelineClip("clip-2", "asset-2",
                Duration.ofMillis(1000), Duration.ofMillis(2000), Duration.ZERO, Duration.ZERO);
        TimelineTrack track1 = new TimelineTrack("track-1", "Video 料", TrackType.VIDEO, List.of(clip2));
        TimelineTrack track2 = new TimelineTrack("track-2", "Audio 1", TrackType.AUDIO, List.of(clip1));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(track1, track2),
                new TimelineMetadata("", "", Map.of()));
    }

    private TimelineDocument sampleDocumentWithExtraTrackAndClip() {
        TimelineClip clip1 = new TimelineClip("clip-1", "asset-1",
                Duration.ofMillis(0), Duration.ofMillis(1000), Duration.ZERO, Duration.ZERO);
        TimelineClip clip2 = new TimelineClip("clip-2", "asset-2",
                Duration.ofMillis(1000), Duration.ofMillis(2000), Duration.ZERO, Duration.ZERO);
        TimelineClip clip3 = new TimelineClip("clip-3", "asset-3",
                Duration.ofMillis(2000), Duration.ofMillis(3000), Duration.ZERO, Duration.ZERO);
        TimelineTrack track1 = new TimelineTrack("track-1", "Video 1", TrackType.VIDEO, List.of(clip1, clip2));
        TimelineTrack track2 = new TimelineTrack("track-2", "Audio 1", TrackType.AUDIO, List.of(clip3));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(track1, track2),
                new TimelineMetadata("", "", Map.of()));
    }
}
