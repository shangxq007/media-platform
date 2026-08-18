package com.example.platform.timeline.canonicalmodel;

import com.example.platform.shared.time.MediaTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimelineCanonicalNormalizerTest {

    @Test
    void normalizationOrdersTracksAndClipsDeterministically() {
        TimelineCandidate candidate = new TimelineCandidate("timeline-1", "project-1",
                TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1,
                List.of(track("video-b", TimelineCandidate.TrackType.VIDEO, 1, clip("clip-b", 5)),
                        track("audio-a", TimelineCandidate.TrackType.AUDIO, 1, clip("clip-audio", 2)),
                        track("video-a", TimelineCandidate.TrackType.VIDEO, 1, clip("clip-a", 0))), List.of(), List.of(), List.of(), com.example.platform.audio.domain.mix.AudioMix.empty(), java.util.List.of());

        TimelineCanonicalModel model = TimelineCanonicalNormalizer.normalize(candidate).orElseThrow();

        assertEquals(List.of("video-a", "video-b", "audio-a"),
                model.tracks().stream().map(TimelineTrackCanonical::trackId).toList());
        assertEquals(1.0d, model.tracks().get(2).audioGain());
        assertEquals(MediaTime.ofTicks(10, 1), model.duration());
    }

    @Test
    void normalizationIsDeterministicAndIdempotentForSameInput() {
        TimelineCandidate candidate = new TimelineCandidate("timeline-1", "project-1",
                TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1,
                List.of(track("video", TimelineCandidate.TrackType.VIDEO, 0,
                        clip("clip-b", 5), clip("clip-a", 0))), List.of(), List.of(), List.of(), com.example.platform.audio.domain.mix.AudioMix.empty(), java.util.List.of());

        TimelineCanonicalModel first = TimelineCanonicalNormalizer.normalize(candidate).orElseThrow();
        TimelineCanonicalModel second = TimelineCanonicalNormalizer.normalize(candidate).orElseThrow();
        TimelineCanonicalModel idempotent = TimelineCanonicalNormalizer.normalize(TimelineCandidate.fromCanonical(first)).orElseThrow();

        assertEquals(first, second);
        assertEquals(first, idempotent);
        assertEquals(List.of("clip-a", "clip-b"), first.tracks().getFirst().clips().stream()
                .map(TimelineClipCanonical::clipId).toList());
    }

    @Test
    void canonicalOutputCollectionsAreImmutable() {
        TimelineCanonicalModel model = TimelineCanonicalNormalizer.normalize(new TimelineCandidate("timeline-1", "project-1",
                TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1,
                List.of(track("video", TimelineCandidate.TrackType.VIDEO, 0, clip("clip", 0))), List.of(), List.of(), List.of(), com.example.platform.audio.domain.mix.AudioMix.empty(), java.util.List.of())).orElseThrow();

        assertThrows(UnsupportedOperationException.class, () -> model.tracks().clear());
        assertThrows(UnsupportedOperationException.class, () -> model.tracks().getFirst().clips().clear());
    }

    private static TimelineCandidate.Track track(String id, TimelineCandidate.TrackType type, int zOrder,
            TimelineCandidate.Clip... clips) {
        return TimelineCandidate.track(id, type, zOrder, null, List.of(clips));
    }

    private static TimelineCandidate.Clip clip(String id, long start) {
        return new TimelineCandidate.Clip(id, TimelineSourceRef.of("source-" + id), MediaTime.ofTicks(start, 1), MediaTime.ZERO, MediaTime.ofTicks(5, 1), com.example.platform.shared.time.FrameRate.of(30, 1), java.util.List.of(), null, null, null, null, null, null, null);
    }
}
