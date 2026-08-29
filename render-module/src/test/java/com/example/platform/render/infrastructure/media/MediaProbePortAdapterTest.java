package com.example.platform.render.infrastructure.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MediaProbePortAdapterTest {

    @Test
    void failsClosedWithoutRenderOwnedMediaProbeExecution() {
        var observation = new MediaProbePortAdapter().probe("file:///tmp/media.mp4");

        assertEquals("provider-plugin-unavailable", observation.provider());
        assertFalse(observation.valid());
        assertTrue(observation.normalizeRequired());
        assertEquals(MediaProbePortAdapter.ERROR, observation.error());
    }
}
