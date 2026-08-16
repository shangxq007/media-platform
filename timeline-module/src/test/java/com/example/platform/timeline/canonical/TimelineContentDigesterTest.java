package com.example.platform.timeline.canonical;

import com.example.platform.shared.time.MediaTime;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TimelineContentDigesterTest {

    private final TimelineContentDigester digester = new TimelineContentDigester();

    @Test
    void sameContent_sameDigest() {
        var doc1 = createSampleDocument();
        var doc2 = createSampleDocument();

        String digest1 = digester.digest(doc1);
        String digest2 = digester.digest(doc2);

        assertEquals(digest1, digest2, "Same content must produce same digest");
    }

    @Test
    void differentContent_differentDigest() {
        var doc1 = createSampleDocument();
        var doc2 = createSampleDocumentWithDifferentClip();

        String digest1 = digester.digest(doc1);
        String digest2 = digester.digest(doc2);

        assertNotEquals(digest1, digest2, "Different content must produce different digest");
    }

    @Test
    void digestAlgorithm_isSHA256() {
        assertEquals("SHA-256", digester.getAlgorithm());
    }

    @Test
    void serializationRules_documented() {
        assertNotNull(digester.getSerializationRules());
        assertTrue(digester.getSerializationRules().contains("SHA-256"));
    }

    private TimelineDocument createSampleDocument() {
        var clip = new TimelineClip("clip-1", "asset-1", null, null, null,
                MediaTime.ofRational(0, 1), MediaTime.ofRational(10, 1),
                MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(clip));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), new TimelineMetadata("Test", "", Map.of()));
    }

    private TimelineDocument createSampleDocumentWithDifferentClip() {
        var clip = new TimelineClip("clip-2", "asset-2", null, null, null,
                MediaTime.ofRational(5, 1), MediaTime.ofRational(15, 1),
                MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(clip));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), new TimelineMetadata("Test", "", Map.of()));
    }
}