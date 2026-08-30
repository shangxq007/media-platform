package com.example.platform.render.app.autocaptions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.ai.api.video.SpeechToTextPort;
import com.example.platform.ai.infrastructure.video.UnavailableSpeechToTextProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AutoCaptionsServiceTest {

    private AutoCaptionsService service;

    @BeforeEach
    void setUp() {
        service = new AutoCaptionsService(successfulSpeechToText());
    }

    @Test
    void generateCaptionsProducesOverlays() {
        var request = new AutoCaptionsService.AutoCaptionsRequest(
                "tenant-1", "proj-1", "asset-1",
                "tenant/tenant-1/workspace/ws-1/project/proj-1/assets/asset-1/audio.wav",
                "en", 10000,
                new com.example.platform.fonttext.typography.FontFamilyName("Inter"), 24, "#FFFFFF", 0.5, 0.9);

        AutoCaptionsService.AutoCaptionsResult result = service.generateCaptions(request);

        assertNotNull(result);
        assertTrue(result.success());
        assertFalse(result.overlays().isEmpty());
        assertEquals("proj-1", result.projectId());

        var overlay = result.overlays().get(0);
        assertNotNull(overlay.id());
        assertTrue(overlay.id().startsWith("sub_"));
        assertEquals("Inter", overlay.fontFamily().value());
        assertEquals(24, overlay.fontSize());
        assertEquals("#FFFFFF", overlay.color());
        assertEquals("0.5", overlay.positionX());
        assertEquals("0.9", overlay.positionY());
    }

    @Test
    void missingProviderReturnsTypedUnavailableWithoutGeneratedContent() {
        service = new AutoCaptionsService(new UnavailableSpeechToTextProvider());
        var request = new AutoCaptionsService.AutoCaptionsRequest(
                "tenant-1", "proj-1", "asset-1",
                "audio.wav", "en", 10000,
                new com.example.platform.fonttext.typography.FontFamilyName("Inter"), 0, null, 0.5, 0.9);

        AutoCaptionsService.AutoCaptionsResult result = service.generateCaptions(request);

        assertFalse(result.success());
        assertEquals("AI-503-001", result.errorCode());
        assertEquals(0, result.segmentCount());
        assertTrue(result.overlays().isEmpty());
    }

    @Test
    void positionDefaultsToBottomCenter() {
        var request = new AutoCaptionsService.AutoCaptionsRequest(
                "tenant-1", "proj-1", "asset-1",
                "audio.wav", "en", 10000,
                new com.example.platform.fonttext.typography.FontFamilyName("Inter"), 0, null, 0, 0);

        AutoCaptionsService.AutoCaptionsResult result = service.generateCaptions(request);

        var overlay = result.overlays().get(0);
        assertEquals("center", overlay.positionX());
        assertEquals("bottom", overlay.positionY());
    }

    private static SpeechToTextPort successfulSpeechToText() {
        return request -> new SpeechToTextPort.SpeechToTextResult(
                request.language(), 5.0,
                java.util.List.of(new SpeechToTextPort.SubtitleSegment(
                        0, 0, 5000, "Test caption", request.language(), 1.0,
                        java.util.List.of())));
    }
}
