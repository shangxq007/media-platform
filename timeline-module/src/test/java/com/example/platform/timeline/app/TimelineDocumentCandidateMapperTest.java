package com.example.platform.timeline.app;

import com.example.platform.timeline.app.TimelineCanonicalRejectionException;import com.example.platform.timeline.app.TimelineDocumentCandidateMapper;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalProfile;
import com.example.platform.shared.time.MediaTime;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused unit tests for the frozen TimelineDocument -> TimelineCandidate adapter
 * (NDSF-SCOPE-E1 F005-F010). Pure mapping; no database.
 */
class TimelineDocumentCandidateMapperTest {

    @Test
    void mapsValidDocument_withFrozenDefaults() {
        var clip = new TimelineClip("clip-1", "asset-1", null, null, null,
                MediaTime.ofRational(0, 1), MediaTime.ofRational(10, 1), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(clip));
        var doc = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), new TimelineMetadata("t", "", Map.of()));

        TimelineCandidate candidate = TimelineDocumentCandidateMapper.map("prod-1", doc);

        assertEquals("prod-1", candidate.timelineId(), "F009: timelineId derived from productId");
        assertEquals("prod-1", candidate.projectId());
        assertEquals(TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1, candidate.profile());
        assertEquals(1, candidate.tracks().size());
        TimelineCandidate.Track mapped = candidate.tracks().get(0);
        assertEquals("track-1", mapped.trackId());
        assertEquals(TimelineCandidate.TrackType.VIDEO, mapped.type());
        assertEquals(0, mapped.zOrder(), "F007: zOrder default 0");
        assertNull(mapped.audioGain(), "F007: audioGain default null");
        assertEquals(1, mapped.clips().size());
        TimelineCandidate.Clip mappedClip = mapped.clips().get(0);
        assertEquals("clip-1", mappedClip.clipId());
        assertEquals("asset-1", mappedClip.sourceRef().value());
        assertEquals(MediaTime.ZERO, mappedClip.timelineStart());
        assertEquals(MediaTime.ZERO, mappedClip.sourceStart());
        assertEquals(0, MediaTime.ofRational(10L, 1L).compareTo(mappedClip.duration()));
    }

    @Test
    void convertsDurationExactlyToRationalMediaTime() {
        var clip = new TimelineClip("clip-1", "asset-1", null, null, null,
                MediaTime.ofRational(1, 1), MediaTime.ofMicros((2500) * 1000L), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(clip));
        var doc = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), TimelineMetadata.empty());

        TimelineCandidate candidate = TimelineDocumentCandidateMapper.map("prod-1", doc);

        // 2.5s - 1.0s = 1.5s = 1_500_000_000ns / 1e9 -> canonical 3/2
        MediaTime expected = MediaTime.ofRational(1_500_000_000L, 1_000_000_000L);
        assertEquals(0, expected.compareTo(candidate.tracks().get(0).clips().get(0).duration()));
        assertEquals(0, MediaTime.ofRational(1_500_000_000L, 1_000_000_000L)
                .compareTo(MediaTime.ofRational(3L, 2L)), "canonical gcd form 3/2");
    }

    @Test
    void unmappableTrackType_rejectedWithFrozenCode() {
        var track = new TimelineTrack("track-1", "Fx", TrackType.EFFECT, List.of());
        var doc = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), TimelineMetadata.empty());

        TimelineCanonicalRejectionException ex =
                assertThrows(TimelineCanonicalRejectionException.class, () ->
                        TimelineDocumentCandidateMapper.map("prod-1", doc));

        assertTrue(ex.hasAdapterDiagnostics());
        assertEquals(TimelineCanonicalRejectionException.Code.TIMELINE_TRACK_TYPE_UNSUPPORTED,
                ex.adapterDiagnostics().get(0).code());
    }

    @Test
    void blankAssetId_rejectedWithFrozenCode() {
        var clip = new TimelineClip("clip-1", null, null, null, null,
                MediaTime.ZERO, MediaTime.ofRational(10, 1), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(clip));
        var doc = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), TimelineMetadata.empty());

        TimelineCanonicalRejectionException ex =
                assertThrows(TimelineCanonicalRejectionException.class, () ->
                        TimelineDocumentCandidateMapper.map("prod-1", doc));

        assertTrue(ex.hasAdapterDiagnostics());
        assertEquals(TimelineCanonicalRejectionException.Code.TIMELINE_SOURCE_REF_INVALID,
                ex.adapterDiagnostics().get(0).code());
    }

    @Test
    void negativeDuration_rejectedWithFrozenCode() {
        var clip = new TimelineClip("clip-1", "asset-1", null, null, null,
                MediaTime.ofRational(10, 1), MediaTime.ofRational(5, 1), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(clip));
        var doc = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), TimelineMetadata.empty());

        TimelineCanonicalRejectionException ex =
                assertThrows(TimelineCanonicalRejectionException.class, () ->
                        TimelineDocumentCandidateMapper.map("prod-1", doc));

        assertTrue(ex.hasAdapterDiagnostics());
        assertEquals(TimelineCanonicalRejectionException.Code.TIMELINE_TIMING_INVALID,
                ex.adapterDiagnostics().get(0).code());
    }

    @Test
    void audioTrack_mapsWithNullGainDefault() {
        var clip = new TimelineClip("clip-1", "asset-1", null, null, null,
                MediaTime.ZERO, MediaTime.ofRational(4, 1), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var track = new TimelineTrack("track-1", "Mix", TrackType.AUDIO, List.of(clip));
        var doc = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), TimelineMetadata.empty());

        TimelineCandidate candidate = TimelineDocumentCandidateMapper.map("prod-1", doc);

        assertEquals(TimelineCandidate.TrackType.AUDIO, candidate.tracks().get(0).type());
        assertNull(candidate.tracks().get(0).audioGain());
    }

    @Test
    void emptyTrackList_mapsToEmptyCandidate() {
        var doc = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(), TimelineMetadata.empty());

        TimelineCandidate candidate = TimelineDocumentCandidateMapper.map("prod-1", doc);

        assertTrue(candidate.tracks().isEmpty());
    }
}
