package com.example.platform.render.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OFXRenderProviderTest {

    private OFXRenderProvider provider;

    @BeforeEach
    void setUp() {
        provider = new OFXRenderProvider();
    }

    @Test
    void getSupportedProfilesIncludesOFX() {
        List<String> profiles = provider.getSupportedProfiles();
        assertTrue(profiles.contains("ofx_1080p"));
        assertTrue(profiles.contains("ofx_720p"));
        assertTrue(profiles.contains("default_1080p"));
        assertTrue(profiles.contains("social_720p"));
    }

    @Test
    void supportsBlurFilter() {
        assertTrue(provider.supports("blur"));
    }

    @Test
    void supportsVignette() {
        assertTrue(provider.supports("vignette"));
    }

    @Test
    void supportsChromatic() {
        assertTrue(provider.supports("chromatic"));
    }

    @Test
    void supportsDissolveTransition() {
        assertTrue(provider.supports("dissolve"));
    }

    @Test
    void supportsWipeTransition() {
        assertTrue(provider.supports("wipe"));
    }

    @Test
    void supportsTextBurn() {
        assertTrue(provider.supports("text-burn"));
    }

    @Test
    void supportsOverlay() {
        assertTrue(provider.supports("overlay"));
    }

    @Test
    void supportsPip() {
        assertTrue(provider.supports("pip"));
    }

    @Test
    void doesNotSupportH265() {
        assertFalse(provider.supports("h265"));
    }

    @Test
    void renderWithEmptyTimelineReturnsResult(@TempDir Path tempDir) {
        provider.setStorageRoot(tempDir.toString());

        String otioJson = "{\"tracks\":[]}";
        RenderProvider.RenderResult result = provider.render("job-1", otioJson, "ofx_1080p");

        assertNotNull(result);
        assertNotNull(result.artifactId());
        assertEquals("mp4", result.format());
        assertEquals("1920x1080", result.resolution());
    }

    @Test
    void renderWithEffectsTimeline(@TempDir Path tempDir) {
        provider.setStorageRoot(tempDir.toString());

        String otioJson = "{\"tracks\":[{\"name\":\"Video 1\",\"children\":[{\"name\":\"clip_1\",\"source_range\":{\"start_time\":0,\"duration\":5},\"effects\":[{\"type\":\"filter\",\"name\":\"blur\",\"params\":{\"radius\":3}},{\"type\":\"transition\",\"name\":\"dissolve\",\"duration\":0.5},{\"type\":\"text\",\"text\":\"Hello\",\"position\":\"bottom\"}]}]}]}";
        RenderProvider.RenderResult result = provider.render("job-2", otioJson, "ofx_720p");

        assertNotNull(result);
        assertEquals("1280x720", result.resolution());
    }

    @Test
    void renderWithAiScriptReturnsResult(@TempDir Path tempDir) {
        provider.setStorageRoot(tempDir.toString());

        RenderProvider.RenderResult result = provider.render("job-3", "Generate video with effects", "ofx_1080p");
        assertNotNull(result);
        assertEquals("mp4", result.format());
    }

    @Test
    void environmentValidationPasses() {
        RenderProvider.EnvironmentValidationResult result = provider.validateEnvironment();
        assertNotNull(result);
        assertTrue(result.valid());
    }
}
