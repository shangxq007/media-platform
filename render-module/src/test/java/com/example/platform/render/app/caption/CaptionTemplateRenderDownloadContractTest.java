package com.example.platform.render.app.caption;

import com.example.platform.render.api.dto.*;
import com.example.platform.render.domain.caption.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Caption Template Render download/result contract.
 * Proves: response exposes outputProductId, no storage internals,
 * downloadAvailable semantics documented.
 */
class CaptionTemplateRenderDownloadContractTest {

    @Test
    @DisplayName("Success response exposes outputProductId")
    void successResponseExposesOutputProductId() {
        CaptionTemplateRenderApiResponse response = CaptionTemplateRenderApiResponse.success(
                "rj-1", "prod-out-1", new CaptionOutputProfileDto(1920, 1080, 30.0, "mp4"));

        assertNotNull(response.outputProductId());
        assertEquals("prod-out-1", response.outputProductId());
    }

    @Test
    @DisplayName("Success response has ready=true")
    void successResponseReady() {
        CaptionTemplateRenderApiResponse response = CaptionTemplateRenderApiResponse.success(
                "rj-1", "prod-out-1", new CaptionOutputProfileDto(1920, 1080, 30.0, "mp4"));

        assertTrue(response.ready());
        assertEquals("READY", response.status());
    }

    @Test
    @DisplayName("Response does not expose raw download URL")
    void responseNoRawDownloadUrl() {
        CaptionTemplateRenderApiResponse response = CaptionTemplateRenderApiResponse.success(
                "rj-1", "prod-out-1", new CaptionOutputProfileDto(1920, 1080, 30.0, "mp4"));

        String str = response.toString();
        assertFalse(str.contains("signedUrl"));
        assertFalse(str.contains("downloadUrl"));
        assertFalse(str.contains("presign"));
    }

    @Test
    @DisplayName("Response does not expose storage internals")
    void responseNoStorageInternals() {
        CaptionTemplateRenderApiResponse response = CaptionTemplateRenderApiResponse.success(
                "rj-1", "prod-out-1", new CaptionOutputProfileDto(1920, 1080, 30.0, "mp4"));

        String str = response.toString();
        assertFalse(str.contains("bucket"));
        assertFalse(str.contains("objectKey"));
        assertFalse(str.contains("rootPath"));
        assertFalse(str.contains("relativePath"));
        assertFalse(str.contains("materializedPath"));
        assertFalse(str.contains("storageReferenceId"));
    }

    @Test
    @DisplayName("Response does not expose provider internals")
    void responseNoProviderInternals() {
        CaptionTemplateRenderApiResponse response = CaptionTemplateRenderApiResponse.success(
                "rj-1", "prod-out-1", new CaptionOutputProfileDto(1920, 1080, 30.0, "mp4"));

        String str = response.toString();
        assertFalse(str.contains("providerName"));
        assertFalse(str.contains("backendName"));
        assertFalse(str.contains("executionEnvironment"));
    }

    @Test
    @DisplayName("Response does not expose graph/plan/correlation IDs")
    void responseNoInternalIds() {
        CaptionTemplateRenderApiResponse response = CaptionTemplateRenderApiResponse.success(
                "rj-1", "prod-out-1", new CaptionOutputProfileDto(1920, 1080, 30.0, "mp4"));

        String str = response.toString();
        assertFalse(str.contains("renderCorrelationId"));
        assertFalse(str.contains("renderExecutionPlanId"));
        assertFalse(str.contains("artifactGraphId"));
        assertFalse(str.contains("capabilityGraphId"));
        assertFalse(str.contains("providerBindingPlanId"));
    }

    @Test
    @DisplayName("Validation failure response has safe error structure")
    void validationFailureSafe() {
        CaptionTemplateRenderApiResponse response = CaptionTemplateRenderApiResponse.validationFailed(
                List.of(new CaptionTemplateValidationErrorDto(
                        "captionSegments[0].text", "TEXT_BLANK", "Text must not be blank")));

        assertFalse(response.ready());
        assertEquals("VALIDATION_FAILED", response.status());
        assertEquals(1, response.validationErrors().size());
        assertNull(response.outputProductId());
    }

    @Test
    @DisplayName("Failed response has safe structure")
    void failedResponseSafe() {
        CaptionTemplateRenderApiResponse response = CaptionTemplateRenderApiResponse.failed("Internal error");

        assertFalse(response.ready());
        assertEquals("FAILED", response.status());
        assertNull(response.outputProductId());
    }

    @Test
    @DisplayName("v0: outputProductId is the download mechanism (no separate URL)")
    void v0OutputProductIdIsDownloadMechanism() {
        // v0 contract: outputProductId returned, caller uses existing Product/Storage
        // mechanism for download. No separate download URL in response.
        CaptionTemplateRenderApiResponse response = CaptionTemplateRenderApiResponse.success(
                "rj-1", "prod-out-1", new CaptionOutputProfileDto(1920, 1080, 30.0, "mp4"));

        assertNotNull(response.outputProductId());
        // No downloadAvailable field in v0 — outputProductId is the contract
    }
}
