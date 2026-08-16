package com.example.platform.timeline.semantics.temporal;

import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.render.testsupport.TestSourceBindings;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TEMPORAL_MAPPING_FOUNDATION_V1 guard + invariant tests (R3/R4):
 * constant-rate duration consistency fail-closed; audio non-identity fail-closed.
 */
class TemporalMappingGuardTest {

    private static MediaClip clip(MediaClip.TimeRange timeline, MediaClip.TimeRange source,
                                  TemporalMapping mapping) {
        return new MediaClip("c1", "track-1", timeline, source, mapping,
                TestSourceBindings.of("asset-1", "stream-1", "artifact-1", source));
    }

    @Test
    void consistentConstantRateAccepted() {
        // 4s source / 2s timeline / 2x = VALID
        clip(new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(2, 1)),
                new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(4, 1)),
                ConstantRateTemporalMapping.of(2, 1, PlaybackDirection.FORWARD));
        // 2s source / 4s timeline / 0.5x = VALID
        clip(new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(4, 1)),
                new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(2, 1)),
                ConstantRateTemporalMapping.of(1, 2, PlaybackDirection.FORWARD));
    }

    @Test
    void durationMismatchFailsClosed() {
        // 4s source / 3s timeline / 2x = INVALID (no tolerance, no repair)
        assertThrows(IllegalArgumentException.class, () ->
                clip(new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(3, 1)),
                        new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(4, 1)),
                        ConstantRateTemporalMapping.of(2, 1, PlaybackDirection.FORWARD)));
    }

    @Test
    void freezeInsideSourceRangeAccepted() {
        clip(new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(2, 1)),
                new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(4, 1)),
                new FreezeTemporalMapping(MediaTime.ofRational(3, 1)));
    }

    @Test
    void freezeOutsideSourceRangeRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                clip(new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(2, 1)),
                        new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(4, 1)),
                        new FreezeTemporalMapping(MediaTime.ofRational(5, 1))));
    }

    @Test
    void audioIdentityExecutable() {
        assertTrue(TemporalAudioExecutionGuard.isAudioExecutable(
                ConstantRateTemporalMapping.identity()));
        assertDoesNotThrow(() -> TemporalAudioExecutionGuard.requireAudioIdentity(
                ConstantRateTemporalMapping.identity()));
    }

    @Test
    void audioNonIdentityFailsClosed() {
        for (TemporalMapping m : new TemporalMapping[]{
                ConstantRateTemporalMapping.of(2, 1, PlaybackDirection.FORWARD),
                ConstantRateTemporalMapping.of(1, 2, PlaybackDirection.FORWARD),
                ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.REVERSE),
                new FreezeTemporalMapping(MediaTime.ofRational(1, 1))}) {
            assertFalse(TemporalAudioExecutionGuard.isAudioExecutable(m), "mapping must not be audio-executable: " + m);
            assertThrows(TemporalAudioExecutionGuard.AudioTemporalExecutionUnsupported.class,
                    () -> TemporalAudioExecutionGuard.requireAudioIdentity(m));
        }
    }
}
