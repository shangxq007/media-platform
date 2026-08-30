package com.example.platform.render.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.platform.ai.infrastructure.video.UnavailableSpeechToTextProvider;
import com.example.platform.render.app.autocaptions.AutoCaptionsService;
import com.example.platform.shared.web.TenantContext;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AutoCaptionsControllerContainmentTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void directInvocationReturnsTypedUnavailableAndNoGeneratedSuccess() {
        TenantContext.set("tenant-1");
        AutoCaptionsController controller = new AutoCaptionsController(
                new AutoCaptionsService(new UnavailableSpeechToTextProvider()));

        var response = controller.generateCaptions(
                new AutoCaptionsController.GenerateCaptionsRequest(
                        "project-1", "asset-1", "audio.wav", "en", 10000,
                        null, 24, "#FFFFFF", 0.5, 0.9));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertEquals("AI-503-001", body.get("errorCode"));
        assertFalse((Boolean) body.get("success"));
        assertFalse(body.containsKey("overlays"));
    }
}
