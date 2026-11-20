package com.example.platform.timeline.semantics.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class VideoTransformTest {
    @Test
    void identityPreservesLegacyLayout() {
        assertEquals(0, VideoTransform.IDENTITY.positionX());
        assertEquals(1, VideoTransform.IDENTITY.scaleX());
        assertEquals(1, VideoTransform.IDENTITY.opacity());
    }

    @Test
    void invalidScaleCropAndOpacityFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> new VideoTransform(0, 0, 0, 1, 0, 0, 1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new VideoTransform(0, 0, 1, 1, 0, 0.5, 0.5, 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new VideoTransform(0, 0, 1, 1, 0, 0, 1, 0, 1, 1.1));
    }

    @Test
    void negativePositionAndNonRightAngleAreValid() {
        VideoTransform transform = new VideoTransform(-40, -20, 0.5, 0.5, 33, 0.2, 0.8, 0.1, 0.9, 0.4);
        assertEquals(-40, transform.positionX());
        assertEquals(33, transform.rotationDegrees());
    }
}
