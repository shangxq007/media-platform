package com.example.platform.render.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaCVRenderProviderTest {

    private JavaCVRenderProvider provider;

    @BeforeEach
    void setUp() {
        JavaCVMediaProbeAdapter adapter = new JavaCVMediaProbeAdapter();
        MediaProbeService probeService = new MediaProbeService(adapter);
        JavaCVRenderService renderService = new JavaCVRenderService(probeService);
        JavaCVTranscodeService transcodeService = new JavaCVTranscodeService(probeService);
        provider = new JavaCVRenderProvider(renderService, transcodeService,
                new com.example.platform.render.domain.timeline.TimelineScriptParser());
    }

    @Test
    void getSupportedProfilesReturnsAllProfiles() {
        List<String> profiles = provider.getSupportedProfiles();
        assertNotNull(profiles);
        assertTrue(profiles.contains("default_1080p"));
        assertTrue(profiles.contains("default_720p"));
        assertTrue(profiles.contains("social_1080p"));
        assertTrue(profiles.contains("social_720p"));
        assertTrue(profiles.contains("mobile_480p"));
        assertTrue(profiles.contains("4k_2160p"));
    }

    @Test
    void supportsH264() {
        assertTrue(provider.supports("h264"));
    }

    @Test
    void supportsH265() {
        assertTrue(provider.supports("h265"));
    }

    @Test
    void supportsVp9() {
        assertTrue(provider.supports("vp9"));
    }

    @Test
    void supportsMp4() {
        assertTrue(provider.supports("mp4"));
    }

    @Test
    void supportsWatermark() {
        assertTrue(provider.supports("watermark"));
    }

    @Test
    void supportsSubtitleBurn() {
        assertTrue(provider.supports("subtitle-burn"));
    }

    @Test
    void supportsFade() {
        assertTrue(provider.supports("fade"));
    }

    @Test
    void supportsClip() {
        assertTrue(provider.supports("clip"));
    }

    @Test
    void supportsTranscode() {
        assertTrue(provider.supports("transcode"));
    }

    @Test
    void doesNotSupportHdr() {
        assertFalse(provider.supports("hdr"));
    }

    @Test
    void renderWithEmptyTimelineReturnsResult(@TempDir Path tempDir) {
        provider.setStorageRoot(tempDir.toString());

        String otioJson = "{\"tracks\":[]}";
        RenderProvider.RenderResult result = provider.render("job-1", otioJson, "default_1080p");

        assertNotNull(result);
        assertNotNull(result.artifactId());
        assertTrue(result.artifactId().startsWith("art_"));
        assertEquals("mp4", result.format());
        assertEquals("1920x1080", result.resolution());
        assertTrue(result.storageUri().contains("artifacts/job-1"));
    }

    @Test
    void renderWithAiScriptReturnsResult(@TempDir Path tempDir) {
        provider.setStorageRoot(tempDir.toString());

        RenderProvider.RenderResult result = provider.render("job-2", "Generate a 5-second video", "social_720p");

        assertNotNull(result);
        assertEquals("mp4", result.format());
        assertEquals("1280x720", result.resolution());
    }

    @Test
    void renderWith720pProfileReturns720pResolution(@TempDir Path tempDir) {
        provider.setStorageRoot(tempDir.toString());

        RenderProvider.RenderResult result = provider.render("job-3", "test", "default_720p");
        assertEquals("1280x720", result.resolution());
    }

    @Test
    void renderWith1080pProfileReturns1080pResolution(@TempDir Path tempDir) {
        provider.setStorageRoot(tempDir.toString());

        RenderProvider.RenderResult result = provider.render("job-4", "test", "default_1080p");
        assertEquals("1920x1080", result.resolution());
    }

    @Test
    void renderWithH265PresetReturnsCorrectResolution(@TempDir Path tempDir) {
        provider.setStorageRoot(tempDir.toString());

        RenderProvider.RenderResult result = provider.render("job-5", "test", "h265");
        assertNotNull(result);
        assertEquals("mp4", result.format());
        assertEquals("1920x1080", result.resolution());
    }

    @Test
    void renderWithVp9PresetReturnsCorrectResolution(@TempDir Path tempDir) {
        provider.setStorageRoot(tempDir.toString());

        RenderProvider.RenderResult result = provider.render("job-6", "test", "vp9");
        assertNotNull(result);
        assertEquals("mp4", result.format());
        assertEquals("1920x1080", result.resolution());
    }

    @Test
    void renderWithPreview720pPresetReturnsCorrectResolution(@TempDir Path tempDir) {
        provider.setStorageRoot(tempDir.toString());

        RenderProvider.RenderResult result = provider.render("job-7", "test", "preview_720p");
        assertNotNull(result);
        assertEquals("mp4", result.format());
        assertEquals("1280x720", result.resolution());
    }

    @Test
    void renderWithHq1080pPresetReturnsCorrectResolution(@TempDir Path tempDir) {
        provider.setStorageRoot(tempDir.toString());

        RenderProvider.RenderResult result = provider.render("job-8", "test", "hq_1080p");
        assertNotNull(result);
        assertEquals("mp4", result.format());
        assertEquals("1920x1080", result.resolution());
    }

    @Test
    void environmentValidationReturnsResult() {
        RenderProvider.EnvironmentValidationResult result = provider.validateEnvironment();
        assertNotNull(result);
    }

    @Test
    void renderWithSubtitleTracksGeneratesVideo(@TempDir Path tempDir) {
        provider.setStorageRoot(tempDir.toString());

        String otioJson = "{\"tracks\":[{\"type\":\"VIDEO\",\"children\":[]},{\"type\":\"SUBTITLE\",\"burnIn\":true,\"cues\":[{\"text\":\"Hello\",\"startTime\":0.0,\"endTime\":5.0}],\"fontId\":\"test-font\",\"fallbackFontIds\":[]}]}";
        RenderProvider.RenderResult result = provider.render("job-sub", otioJson, "default_1080p");

        assertNotNull(result);
        assertEquals("mp4", result.format());
    }

    @Test
    void renderWithInvalidJsonFallsBackToPlaceholder(@TempDir Path tempDir) {
        provider.setStorageRoot(tempDir.toString());

        RenderProvider.RenderResult result = provider.render("job-invalid", "not json at all", "default_1080p");
        assertNotNull(result);
        assertEquals("mp4", result.format());
    }
}
