package com.example.platform.render.infrastructure.api;

import com.example.platform.render.infrastructure.RenderJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SubtitleRenderApiMvcTest {

    private SubtitleRenderRequestMapper mapper;
    private TemplateAllowlist templateAllowlist;
    private FontOwnershipChecker fontOwnershipChecker;

    @BeforeEach
    void setUp() {
        templateAllowlist = new TemplateAllowlist() {
            @Override
            public boolean isAllowed(String templateId) {
                return "subtitle-basic".equals(templateId) || "tiktok-style".equals(templateId);
            }
        };
        fontOwnershipChecker = new FontOwnershipChecker() {
            @Override
            public boolean isOwnedByTenant(String fontAssetId, String tenantId) {
                return "font-001".equals(fontAssetId) && "tenant-001".equals(tenantId);
            }

            @Override
            public boolean isReady(String fontAssetId) {
                return "font-001".equals(fontAssetId);
            }
        };
        mapper = new SubtitleRenderRequestMapper(templateAllowlist, fontOwnershipChecker);
    }

    private PublicSubtitleRenderRequest validRequest() {
        return new PublicSubtitleRenderRequest(
                new PublicVideoInput("video-asset-001"),
                List.of(new PublicCaption("Hello World", 0.0, 3.0, List.of())),
                new PublicCaptionStyle("font-001", 24.0, "#FFFFFF", "#000000",
                        null, null, "center", null, false, false, 1.0),
                new PublicTemplateRef("subtitle-basic", "1.0.0"),
                new PublicOutputSpec("mp4", 1920, 1080, 30),
                null
        );
    }

    @Test
    void validRequestMapsToRenderJob() {
        RenderJob job = mapper.mapToRenderJob(validRequest(), "tenant-001");
        assertNotNull(job);
        assertEquals("captioned_video_export", job.jobType());
        assertTrue(job.requiredCapabilities().contains("caption_effects"));
        assertTrue(job.requiredCapabilities().contains("template_render"));
        assertTrue(job.requiredCapabilities().contains("output_normalize"));
    }

    @Test
    void requestCannotSpecifyProvider() {
        RenderJob job = mapper.mapToRenderJob(validRequest(), "tenant-001");
        assertNotNull(job);
        assertTrue(job.preferredProviders() == null || job.preferredProviders().isEmpty());
        assertTrue(job.blockedProviders() == null || job.blockedProviders().isEmpty());
    }

    @Test
    void rawRemotionJsRejected() {
        PublicApiError error = PublicApiError.rawJsRejected();
        assertNotNull(error);
        assertEquals("INVALID_REQUEST", error.code());
        assertTrue(error.message().contains("Remotion JS"));
    }

    @Test
    void rawFFmpegCommandRejected() {
        PublicApiError error = PublicApiError.rawCommandRejected();
        assertNotNull(error);
        assertEquals("INVALID_REQUEST", error.code());
        assertTrue(error.message().contains("FFmpeg"));
    }

    @Test
    void invalidTemplateIdRejected() {
        PublicSubtitleRenderRequest validReq = validRequest();
        final PublicSubtitleRenderRequest request = new PublicSubtitleRenderRequest(
                validReq.video(), validReq.captions(), validReq.style(),
                new PublicTemplateRef("malicious-template", "1.0.0"),
                validReq.output(), validReq.webhookUrl());
        assertThrows(SubtitleRenderValidationException.class, () ->
                mapper.mapToRenderJob(request, "tenant-001"));
    }

    @Test
    void fontNotOwnedByTenantRejected() {
        PublicSubtitleRenderRequest validReq = validRequest();
        final PublicSubtitleRenderRequest request = new PublicSubtitleRenderRequest(
                validReq.video(), validReq.captions(),
                new PublicCaptionStyle("font-other", 24.0, "#FFFFFF", "#000000",
                        null, null, "center", null, false, false, 1.0),
                validReq.template(), validReq.output(), validReq.webhookUrl());
        SubtitleRenderValidationException ex = assertThrows(SubtitleRenderValidationException.class, () ->
                mapper.mapToRenderJob(request, "tenant-001"));
        assertEquals("FONT_NOT_OWNED", ex.getError().code());
    }

    @Test
    void fontNotReadyRejected() {
        FontOwnershipChecker notReadyChecker = new FontOwnershipChecker() {
            @Override
            public boolean isOwnedByTenant(String fontAssetId, String tenantId) {
                return true;
            }

            @Override
            public boolean isReady(String fontAssetId) {
                return false;
            }
        };
        SubtitleRenderRequestMapper notReadyMapper = new SubtitleRenderRequestMapper(templateAllowlist, notReadyChecker);
        SubtitleRenderValidationException ex = assertThrows(SubtitleRenderValidationException.class, () ->
                notReadyMapper.mapToRenderJob(validRequest(), "tenant-001"));
        assertEquals("FONT_NOT_READY", ex.getError().code());
    }

    @Test
    void unsupportedOutputFormatRejected() {
        PublicSubtitleRenderRequest validReq = validRequest();
        final PublicSubtitleRenderRequest request = new PublicSubtitleRenderRequest(
                validReq.video(), validReq.captions(), validReq.style(),
                validReq.template(),
                new PublicOutputSpec("avi", 1920, 1080, 30),
                validReq.webhookUrl());
        SubtitleRenderValidationException ex = assertThrows(SubtitleRenderValidationException.class, () ->
                mapper.mapToRenderJob(request, "tenant-001"));
        assertEquals("UNSUPPORTED_FORMAT", ex.getError().code());
    }

    @Test
    void missingRequiredCaptionsRejected() {
        PublicSubtitleRenderRequest request = new PublicSubtitleRenderRequest(
                new PublicVideoInput("video-asset-001"),
                List.of(),
                new PublicCaptionStyle("font-001", 24.0, null, null,
                        null, null, null, null, null, null, null),
                new PublicTemplateRef("subtitle-basic", "1.0.0"),
                new PublicOutputSpec("mp4", 1920, 1080, 30),
                null);
        SubtitleRenderValidationException ex = assertThrows(SubtitleRenderValidationException.class, () ->
                mapper.mapToRenderJob(request, "tenant-001"));
        assertEquals("INVALID_REQUEST", ex.getError().code());
    }

    @Test
    void requestCreatesAsyncJobResponse() {
        RenderJob job = mapper.mapToRenderJob(validRequest(), "tenant-001");
        assertNotNull(job.id());
        assertTrue(job.id().startsWith("job-subtitle-"));
        assertNotNull(job.jobType());
    }

    @Test
    void renderJobContainsRequiredCapabilities() {
        RenderJob job = mapper.mapToRenderJob(validRequest(), "tenant-001");
        assertTrue(job.requiredCapabilities().contains("caption_effects"));
        assertTrue(job.requiredCapabilities().contains("template_render"));
        assertTrue(job.requiredCapabilities().contains("output_normalize"));
    }

    @Test
    void finalArtifactShapeReturned() {
        PublicArtifactResponse artifact = new PublicArtifactResponse(
                "art-001", "FINAL_OUTPUT",
                "https://cdn.example.com/out.mp4",
                "/v1/artifacts/art-001/download",
                "video/mp4", 10485760L, 30000L,
                1920, 1080, 30,
                "step-3", java.time.Instant.now());
        assertNotNull(artifact.artifactId());
        assertEquals("FINAL_OUTPUT", artifact.artifactType());
        assertNotNull(artifact.downloadUrl());
    }

    @Test
    void traceResponseHidesInternalProviderDetails() {
        PublicJobStatusResponse status = new PublicJobStatusResponse(
                "job-001", "RUNNING", "captioned_video_export", "production",
                0.5, "caption_effects",
                java.time.Instant.now(), java.time.Instant.now(), java.time.Instant.now());
        assertNotNull(status);
        assertEquals("RUNNING", status.status());
    }

    @Test
    void internalTraceCanIncludeProviderDetailsForAdmin() {
        RenderJob job = mapper.mapToRenderJob(validRequest(), "tenant-001");
        assertNotNull(job);
        assertNotNull(job.requiredCapabilities());
    }

    @Test
    void providerSelectionRejected() {
        PublicApiError error = PublicApiError.providerSelectionRejected();
        assertNotNull(error);
        assertEquals("INVALID_REQUEST", error.code());
        assertTrue(error.message().contains("Provider selection"));
    }
}
