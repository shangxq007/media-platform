package com.example.platform.render.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SimpleRenderPolicyEngineTest {

    private SimpleRenderPolicyEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SimpleRenderPolicyEngine();
    }

    @Test
    void socialProfileUsesFfmpegBackend() {
        RenderPolicyDecision decision = engine.decide("social_1080p");
        assertNotNull(decision);
        assertEquals("ffmpeg", decision.primaryBackend());
        assertEquals("NORMAL", decision.notificationPriority());
    }

    @Test
    void nonSocialProfileUsesMltBackend() {
        RenderPolicyDecision decision = engine.decide("cinema_4k");
        assertNotNull(decision);
        assertEquals("mlt", decision.primaryBackend());
        assertEquals("HIGH", decision.notificationPriority());
    }

    @Test
    void genericProfileUsesMltBackend() {
        RenderPolicyDecision decision = engine.decide("standard");
        assertNotNull(decision);
        assertEquals("mlt", decision.primaryBackend());
        assertEquals("HIGH", decision.notificationPriority());
    }

    @Test
    void socialProfileWithComplexNameUsesFfmpeg() {
        RenderPolicyDecision decision = engine.decide("social_vertical_short");
        assertEquals("ffmpeg", decision.primaryBackend());
        assertEquals("NORMAL", decision.notificationPriority());
    }
}
