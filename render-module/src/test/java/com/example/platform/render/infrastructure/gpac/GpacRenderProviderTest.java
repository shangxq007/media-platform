package com.example.platform.render.infrastructure.gpac;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.render.infrastructure.RenderProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GpacRenderProviderTest {

    private GPACRenderProvider provider;
    private ProcessToolRunner mockToolRunner;
    private Mp4BoxCommandFactory commandFactory;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        mockToolRunner = mock(ProcessToolRunner.class);
        commandFactory = new Mp4BoxCommandFactory();
        provider = new GPACRenderProvider(mockToolRunner, commandFactory);
        provider.setStorageRoot(tempDir.toString());
    }

    @Test
    void getSupportedProfilesReturnsGpacProfiles() {
        List<String> profiles = provider.getSupportedProfiles();
        assertNotNull(profiles);
        assertTrue(profiles.contains("gpac_dash"));
        assertTrue(profiles.contains("gpac_hls"));
        assertTrue(profiles.contains("gpac_cmaf"));
    }

    @Test
    void supportsStreamingFormats() {
        assertTrue(provider.supports("mp4"));
        assertTrue(provider.supports("dash"));
        assertTrue(provider.supports("hls"));
        assertTrue(provider.supports("cmaf"));
        assertTrue(provider.supports("faststart"));
        assertTrue(provider.supports("multi-track"));
        assertTrue(provider.supports("subtitle-track"));
    }

    @Test
    void environmentValidationReturnsResult() {
        RenderProvider.EnvironmentValidationResult result = provider.validateEnvironment();
        assertNotNull(result);
    }

    @Test
    void renderWithMp4FaststartReturnsResult(@TempDir Path tempDir) {
        Instant now = Instant.now();
        when(mockToolRunner.execute(any(ToolExecutionRequest.class)))
                .thenReturn(ToolExecutionResult.success(0, "MP4Box GPAC v2.2", "", now, now.plusMillis(100)));

        RenderProvider.RenderResult result = provider.render(
                "job-gpac-1", "{\"tracks\":[]}", "default_1080p");

        assertNotNull(result);
        assertNotNull(result.artifactId());
        assertEquals("mp4", result.format());
        assertTrue(result.storageUri().contains("artifacts/job-gpac-1"));
    }

    @Test
    void renderWithDashFormatReturnsResult(@TempDir Path tempDir) {
        Instant now = Instant.now();
        when(mockToolRunner.execute(any(ToolExecutionRequest.class)))
                .thenReturn(ToolExecutionResult.success(0, "MP4Box GPAC v2.2", "", now, now.plusMillis(100)));

        String otioJson = "{\"tracks\":[],\"format\":\"dash\"}";
        RenderProvider.RenderResult result = provider.render(
                "job-gpac-2", otioJson, "default_1080p");

        assertNotNull(result);
        assertEquals("dash", result.format());
    }

    @Test
    void renderWithHlsFormatReturnsResult(@TempDir Path tempDir) {
        Instant now = Instant.now();
        when(mockToolRunner.execute(any(ToolExecutionRequest.class)))
                .thenReturn(ToolExecutionResult.success(0, "MP4Box GPAC v2.2", "", now, now.plusMillis(100)));

        String otioJson = "{\"tracks\":[],\"format\":\"hls\"}";
        RenderProvider.RenderResult result = provider.render(
                "job-gpac-3", otioJson, "default_720p");

        assertNotNull(result);
        assertEquals("hls", result.format());
    }

    @Test
    void renderWithCmafFormatReturnsResult(@TempDir Path tempDir) {
        Instant now = Instant.now();
        when(mockToolRunner.execute(any(ToolExecutionRequest.class)))
                .thenReturn(ToolExecutionResult.success(0, "MP4Box GPAC v2.2", "", now, now.plusMillis(100)));

        String otioJson = "{\"tracks\":[],\"format\":\"cmaf\"}";
        RenderProvider.RenderResult result = provider.render(
                "job-gpac-4", otioJson, "default_1080p");

        assertNotNull(result);
        assertEquals("cmaf", result.format());
    }

    @Test
    void mp4BoxCommandFactoryBuildsFaststartCommand() {
        List<String> args = commandFactory.buildFaststartCommand("input.mp4", "output.mp4");
        assertNotNull(args);
        assertTrue(args.contains("-add"));
        assertTrue(args.contains("input.mp4"));
        assertTrue(args.contains("-new"));
        assertTrue(args.contains("output.mp4"));
    }
}
