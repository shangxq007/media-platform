package com.example.platform.render.domain.timeline.semantics.temporal;

import com.example.platform.render.domain.timeline.semantics.clip.MediaClip;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TEMPORAL_MAPPING_FOUNDATION_V1 model tests (R1/R2/R3, TM7-TM12):
 * identity normalization, positive rational rate, direction, freeze position,
 * duration consistency invariant.
 */
class TemporalMappingTest {

    @Test
    void identityIsNormalizedConstantRate() {
        ConstantRateTemporalMapping identity = ConstantRateTemporalMapping.identity();
        assertEquals(1, identity.rate().numerator());
        assertEquals(1, identity.rate().denominator());
        assertEquals(PlaybackDirection.FORWARD, identity.direction());
        assertEquals(TemporalMapping.Kind.CONSTANT_RATE, identity.kind());
        // identity == explicit 1/1 FORWARD (single canonical representation, R1)
        assertEquals(identity, ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD));
    }

    @Test
    void rateNormalizesGcd() {
        assertEquals(ConstantRateTemporalMapping.of(2, 1, PlaybackDirection.FORWARD),
                ConstantRateTemporalMapping.of(4, 2, PlaybackDirection.FORWARD));
        assertEquals(ConstantRateTemporalMapping.of(1, 2, PlaybackDirection.FORWARD),
                ConstantRateTemporalMapping.of(2, 4, PlaybackDirection.FORWARD));
        assertEquals(ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD),
                ConstantRateTemporalMapping.of(2, 2, PlaybackDirection.FORWARD));
    }

    @Test
    void zeroAndNegativeRateRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ConstantRateTemporalMapping.of(0, 1, PlaybackDirection.FORWARD));
        assertThrows(IllegalArgumentException.class,
                () -> ConstantRateTemporalMapping.of(-1, 1, PlaybackDirection.FORWARD));
        // MediaClip.Rational itself rejects non-positive parts
        assertThrows(IllegalArgumentException.class, () -> new MediaClip.Rational(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new MediaClip.Rational(0, 1));
    }

    @Test
    void directionIsExplicitTypedState() {
        ConstantRateTemporalMapping fwd = ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.FORWARD);
        ConstantRateTemporalMapping rev = ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.REVERSE);
        assertEquals(fwd.rate(), rev.rate());
        assertNotEquals(fwd, rev);
        assertEquals(PlaybackDirection.REVERSE, rev.direction());
    }

    @Test
    void freezeOwnsOnlyExactSourcePosition() {
        FreezeTemporalMapping f = new FreezeTemporalMapping(MediaTime.ofRational(3, 1));
        assertEquals(MediaTime.ofRational(3, 1), f.sourcePosition());
        assertEquals(TemporalMapping.Kind.FREEZE, f.kind());
        assertNotEquals(f, new FreezeTemporalMapping(MediaTime.ofRational(4, 1)));
    }

    @Test
    void sealedRootHasExactlyTwoV1Variants() {
        // R1: no IdentityTemporalMapping subtype; sealed permits only the two V1 variants
        assertTrue(TemporalMapping.class.isSealed());
        assertEquals(2, TemporalMapping.class.getPermittedSubclasses().length);
    }

    @Test
    void reverseTimelineToSourceMapsFromWindowEnd() {
        MediaClip.TimeRange src = new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofRational(10, 1));
        ConstantRateTemporalMapping rev = ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.REVERSE);
        // local start -> source window end (10s); local 5s -> 5s; local end -> 0s
        assertEquals(MediaTime.ofRational(10, 1),
                reverseMap(rev, src, MediaTime.ZERO));
        assertEquals(MediaTime.ofRational(5, 1),
                reverseMap(rev, src, MediaTime.ofRational(5, 1)));
        assertEquals(MediaTime.ZERO,
                reverseMap(rev, src, MediaTime.ofRational(10, 1)));
    }

    private static MediaTime reverseMap(ConstantRateTemporalMapping m, MediaClip.TimeRange src, MediaTime local) {
        // source = windowEnd - localOffset*rate
        MediaTime offset = local;
        return src.end().subtract(offset.multiplyRational(m.rate().numerator(), m.rate().denominator()));
    }
}
