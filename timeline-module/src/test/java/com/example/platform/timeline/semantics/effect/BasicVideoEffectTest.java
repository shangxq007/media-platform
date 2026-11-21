package com.example.platform.timeline.semantics.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BasicVideoEffectTest {
    @Test
    void identityDoesNotChangeClipOutput() {
        assertEquals(0, BasicVideoEffect.IDENTITY.brightness());
        assertEquals(1, BasicVideoEffect.IDENTITY.contrast());
        assertEquals(1, BasicVideoEffect.IDENTITY.saturation());
        assertFalse(BasicVideoEffect.IDENTITY.grayscale());
        assertEquals(0, BasicVideoEffect.IDENTITY.fadeInSeconds());
        assertEquals(0, BasicVideoEffect.IDENTITY.fadeOutSeconds());
    }

    @Test
    void rangesFailClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> new BasicVideoEffect(1.1, 1, 1, false, 0, 0, true));
        assertThrows(IllegalArgumentException.class,
                () -> new BasicVideoEffect(0, 2.1, 1, false, 0, 0, true));
        assertThrows(IllegalArgumentException.class,
                () -> new BasicVideoEffect(0, 1, -0.1, false, 0, 0, true));
        assertThrows(IllegalArgumentException.class,
                () -> new BasicVideoEffect(0, 1, 1, false, -0.1, 0, true));
    }
}
