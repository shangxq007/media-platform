package com.example.platform.render.domain.timeline.semantics.clip;

import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MediaClipTest {

    @Test
    @DisplayName("Basic clip construction with valid ranges")
    void basicClip() {
        MediaTime t0 = MediaTime.ZERO;
        MediaTime t1 = MediaTime.ofRational(5, 1);
        MediaTime s0 = MediaTime.ZERO;
        MediaTime s1 = MediaTime.ofRational(10, 1);

        MediaClip clip = new MediaClip(
            "clip-1", "track-1",
            new MediaClip.TimeRange(t0, t1),
            new MediaClip.TimeRange(s0, s1),
            new MediaClip.Rational(2, 1),
            "asset-ref"
        );

        assertEquals("clip-1", clip.clipId());
        assertEquals(MediaTime.ofRational(5, 1), clip.timelineDuration());
        assertEquals(MediaTime.ofRational(10, 1), clip.sourceDuration());
    }

    @Test
    @DisplayName("Fixed-rate duration: sourceDuration / rate = timelineDuration")
    void fixedRateDuration() {
        // Source: 10s, rate: 2/1 -> timeline: 5s
        MediaClip clip = new MediaClip(
            "clip-1", "track-1",
            new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(5, 1)),
            new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(10, 1)),
            new MediaClip.Rational(2, 1),
            "asset-ref"
        );
        assertTrue(clip.hasValidFixedRateDuration());
    }

    @Test
    @DisplayName("Invalid: timelineStart > timelineEnd")
    void invalidTimelineRange() {
        assertThrows(IllegalArgumentException.class, () ->
            new MediaClip("clip-1", "track-1",
                new MediaClip.TimeRange(MediaTime.ofRational(5, 1), MediaTime.ZERO),
                new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(10, 1)),
                new MediaClip.Rational(1, 1),
                null)
        );
    }

    @Test
    @DisplayName("Invalid: sourceStart > sourceEnd")
    void invalidSourceRange() {
        assertThrows(IllegalArgumentException.class, () ->
            new MediaClip("clip-1", "track-1",
                new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(5, 1)),
                new MediaClip.TimeRange(MediaTime.ofRational(10, 1), MediaTime.ZERO),
                new MediaClip.Rational(1, 1),
                null)
        );
    }

    @Test
    @DisplayName("Invalid: playbackRate <= 0")
    void invalidPlaybackRate() {
        assertThrows(IllegalArgumentException.class, () ->
            new MediaClip("clip-1", "track-1",
                new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(5, 1)),
                new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(10, 1)),
                new MediaClip.Rational(0, 1),
                null)
        );
    }

    @Test
    @DisplayName("Timeline to source time mapping with 2x rate")
    void timelineToSource() {
        MediaClip clip = new MediaClip(
            "clip-1", "track-1",
            new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(5, 1)),
            new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(10, 1)),
            new MediaClip.Rational(2, 1),
            null
        );
        // timeline 2s -> source 4s
        MediaTime sourceTime = clip.timelineToSourceTime(MediaTime.ofRational(2, 1));
        assertEquals(MediaTime.ofRational(4, 1), sourceTime);
    }

    @Test
    @DisplayName("TimeRange.contains works correctly")
    void timeRangeContains() {
        MediaClip.TimeRange range = new MediaClip.TimeRange(
            MediaTime.ZERO, MediaTime.ofRational(5, 1));
        assertTrue(range.contains(MediaTime.ofRational(2, 1)));
        assertTrue(range.contains(MediaTime.ZERO));
        assertTrue(range.contains(MediaTime.ofRational(5, 1)));
        assertFalse(range.contains(MediaTime.ofRational(6, 1)));
    }

    @Test
    @DisplayName("TimeRange.overlaps works correctly")
    void timeRangeOverlaps() {
        MediaClip.TimeRange a = new MediaClip.TimeRange(
            MediaTime.ZERO, MediaTime.ofRational(5, 1));
        MediaClip.TimeRange b = new MediaClip.TimeRange(
            MediaTime.ofRational(3, 1), MediaTime.ofRational(8, 1));
        MediaClip.TimeRange c = new MediaClip.TimeRange(
            MediaTime.ofRational(6, 1), MediaTime.ofRational(10, 1));

        assertTrue(a.overlaps(b));
        assertTrue(b.overlaps(a));
        assertFalse(a.overlaps(c));
    }

    @Test
    @DisplayName("TimeRange.duration calculation")
    void timeRangeDuration() {
        MediaClip.TimeRange range = new MediaClip.TimeRange(
            MediaTime.ZERO, MediaTime.ofRational(5, 1));
        assertEquals(MediaTime.ofRational(5, 1), range.duration());
    }
}
