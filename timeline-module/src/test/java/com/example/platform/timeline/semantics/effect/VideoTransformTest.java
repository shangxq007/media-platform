package com.example.platform.timeline.semantics.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class VideoTransformTest {
    @Test
    void identityPreservesFullCanvasLayout() {
        assertEquals(0, VideoTransform.IDENTITY.positionXMicros());
        assertEquals(VideoTransform.UNIT, VideoTransform.IDENTITY.scaleXMicros());
        assertEquals(VideoTransform.UNIT, VideoTransform.IDENTITY.opacityMicros());
    }

    @Test
    void invalidScaleCropAndOpacityFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> new VideoTransform(0, 0, 0, 1_000_000, 0, 0, 1_000_000, 0, 1_000_000, 1_000_000));
        assertThrows(IllegalArgumentException.class,
                () -> new VideoTransform(0, 0, 1_000_000, 1_000_000, 0, 500_000, 500_000, 0, 1_000_000, 1_000_000));
        assertThrows(IllegalArgumentException.class,
                () -> new VideoTransform(0, 0, 1_000_000, 1_000_000, 0, 0, 1_000_000, 0, 1_000_000, 1_100_000));
    }

    @Test
    void negativePositionAndNonRightAngleAreValid() {
        VideoTransform transform = new VideoTransform(-125_000, -83_333, 500_000, 500_000, 33_000_000, 200_000, 800_000, 100_000, 900_000, 400_000);
        assertEquals(-125_000, transform.positionXMicros());
        assertEquals(33_000_000, transform.rotationMicroDegrees());
    }
}
