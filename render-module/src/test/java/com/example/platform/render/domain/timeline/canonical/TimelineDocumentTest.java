package com.example.platform.render.domain.timeline.canonical;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TimelineDocumentTest {

    @Test
    void stableTrackIds_preservedOnSerialization() {
        var clip1 = new TimelineClip("clip-1", "asset-1",
                Duration.ofSeconds(0), Duration.ofSeconds(10),
                Duration.ZERO, Duration.ZERO);
        var clip2 = new TimelineClip("clip-2", "asset-2",
                Duration.ofSeconds(10), Duration.ofSeconds(20),
                Duration.ZERO, Duration.ZERO);
        var track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(clip1, clip2));
        var doc = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), new TimelineMetadata("Test", "", Map.of()));

        assertEquals("track-1", doc.getTracks().get(0).trackId());
        assertEquals("clip-1", doc.getTracks().get(0).clips().get(0).getClipId());
        assertEquals("clip-2", doc.getTracks().get(0).clips().get(1).getClipId());
    }

    @Test
    void nullTrackId_throwsException() {
        var clip = new TimelineClip("clip-1", "asset-1",
                Duration.ZERO, Duration.ofSeconds(10),
                Duration.ZERO, Duration.ZERO);

        assertThrows(IllegalArgumentException.class, () ->
                new TimelineTrack(null, "Main", TrackType.VIDEO, List.of(clip)));
    }

    @Test
    void nullClipId_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineClip(null, "asset-1",
                        Duration.ZERO, Duration.ofSeconds(10),
                        Duration.ZERO, Duration.ZERO));
    }

    @Test
    void unsupportedSchemaVersion_throwsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new TimelineDocument("unsupported-version",
                        List.of(), TimelineMetadata.empty()));
    }

    @Test
    void currentSchemaVersion_accepted() {
        assertDoesNotThrow(() ->
                new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                        List.of(), TimelineMetadata.empty()));
    }

    @Test
    void emptyTracks_allowed() {
        var doc = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(), TimelineMetadata.empty());
        assertNotNull(doc.getTracks());
        assertTrue(doc.getTracks().isEmpty());
    }

    @Test
    void metadata_defaultsToEmpty() {
        var doc = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(), null);
        assertNotNull(doc.getMetadata());
        assertEquals("", doc.getMetadata().title());
    }

    @Test
    void tracks_areDefensivelyCopied() {
        var clips = new java.util.ArrayList<TimelineClip>();
        clips.add(new TimelineClip("clip-1", "asset-1",
                Duration.ZERO, Duration.ofSeconds(10),
                Duration.ZERO, Duration.ZERO));
        var track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, clips);

        // Original list modification should not affect track
        clips.add(new TimelineClip("clip-2", "asset-2",
                Duration.ZERO, Duration.ofSeconds(20),
                Duration.ZERO, Duration.ZERO));

        assertEquals(1, track.clips().size());
    }
}
