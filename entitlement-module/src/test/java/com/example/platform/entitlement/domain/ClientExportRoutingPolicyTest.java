package com.example.platform.entitlement.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientExportRoutingPolicyTest {

    @Test
    void freeSimpleTimelineRecommendsClient() {
        var d = ClientExportRoutingPolicy.resolve(
                "FREE", "free_720p_watermarked", 120, List.of(), true);
        assertEquals(ClientExportRoutingPolicy.LOCATION_CLIENT, d.recommendedRenderLocation());
        assertTrue(d.clientExportSupported());
    }

    @Test
    void natronEffectRequiresServer() {
        var d = ClientExportRoutingPolicy.resolve(
                "FREE", "free_720p_watermarked", 60, List.of("video.natron_vignette"), true);
        assertEquals(ClientExportRoutingPolicy.LOCATION_SERVER, d.recommendedRenderLocation());
        assertFalse(d.clientExportSupported());
        assertTrue(d.unsupportedReasons().stream().anyMatch(r -> r.startsWith("EFFECT_")));
    }

    @Test
    void proTierDefaultsToServer() {
        var d = ClientExportRoutingPolicy.resolve(
                "PRO", "default_1080p", 60, List.of(), true);
        assertEquals(ClientExportRoutingPolicy.LOCATION_SERVER, d.recommendedRenderLocation());
    }
}
