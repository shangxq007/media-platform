package com.example.platform.render.infrastructure.mlt;

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

class MltRenderProviderTest {

    private MltRenderProvider provider;
    private ProcessToolRunner mockToolRunner;
    private MltProjectXmlBuilder xmlBuilder;
    private MLTCommandFactory commandFactory;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        mockToolRunner = mock(ProcessToolRunner.class);
        xmlBuilder = new MltProjectXmlBuilder();
        commandFactory = new MLTCommandFactory();
        provider = new MltRenderProvider(mockToolRunner, xmlBuilder, commandFactory,
                new com.example.platform.render.domain.timeline.TimelineScriptParser());
        // Set storageRoot since @Value is not processed in unit tests
        provider.setStorageRoot(tempDir.toString());
        // Default mock behavior for successful execution
        Instant now = Instant.now();
        when(mockToolRunner.execute(any(ToolExecutionRequest.class)))
                .thenReturn(ToolExecutionResult.success(0, "", "", now, now.plusMillis(100)));
    }

    @Test
    void getSupportedProfilesReturnsMltProfiles() {
        List<String> profiles = provider.getSupportedProfiles();
        assertNotNull(profiles);
        assertTrue(profiles.contains("social_1080p"));
        assertTrue(profiles.contains("default_1080p"));
    }

    @Test
    void supportsTimelineCapabilities() {
        assertTrue(provider.supports("timeline"));
        assertTrue(provider.supports("multi-track"));
        assertTrue(provider.supports("transitions"));
        assertTrue(provider.supports("compositing"));
    }

    @Test
    void environmentValidationReturnsResult() {
        RenderProvider.EnvironmentValidationResult result = provider.validateEnvironment();
        assertNotNull(result);
    }

    @Test
    void renderWithEmptyTimelineReturnsResult(@TempDir Path tempDir) {
        Instant now = Instant.now();
        when(mockToolRunner.execute(any(ToolExecutionRequest.class)))
                .thenReturn(ToolExecutionResult.success(0, "melt 7.0", "", now, now.plusMillis(100)));

        RenderProvider.RenderResult result = provider.render(
                "job-mlt-1", "{\"tracks\":[]}", "default_1080p");

        assertNotNull(result);
        assertNotNull(result.artifactId());
        assertEquals("mp4", result.format());
        assertTrue(result.storageUri().contains("artifacts/job-mlt-1"));
    }

    @Test
    void renderWith720pProfileReturns720p(@TempDir Path tempDir) {
        Instant now = Instant.now();
        when(mockToolRunner.execute(any(ToolExecutionRequest.class)))
                .thenReturn(ToolExecutionResult.success(0, "melt 7.0", "", now, now.plusMillis(100)));

        RenderProvider.RenderResult result = provider.render(
                "job-mlt-2", "{\"tracks\":[]}", "default_720p");

        assertNotNull(result);
        assertEquals("1280x720", result.resolution());
    }

    @Test
    void renderFailureThrowsPlatformException(@TempDir Path tempDir) {
        Instant now = Instant.now();
        when(mockToolRunner.execute(any(ToolExecutionRequest.class)))
                .thenReturn(ToolExecutionResult.failed(1, "", "melt error", now, now.plusMillis(100)));

        assertThrows(com.example.platform.shared.web.PlatformException.class, () -> {
            provider.render("job-mlt-3", "{\"tracks\":[]}", "default_1080p");
        });
    }

    @Test
    void mltProjectXmlBuilderBuildsValidXml() {
        String xml = xmlBuilder.buildSkeleton(1920, 1080, 30);
        assertNotNull(xml);
        assertTrue(xml.contains("<?xml version"));
        assertTrue(xml.contains("mlt"));
        assertTrue(xml.contains("1920"));
        assertTrue(xml.contains("1080"));
    }

    @Test
    void meltCommandFactoryBuildsRenderCommand() {
        List<String> args = commandFactory.buildRenderCommand(
                "/tmp/project.xml", "/tmp/output.mp4", "atsc_1080p_30");
        assertNotNull(args);
        assertTrue(args.contains("/tmp/project.xml"));
        assertTrue(args.contains("-consumer"));
        assertTrue(args.contains("avformat:/tmp/output.mp4"));
    }

    @Test
    void meltCommandFactoryBuildsPreviewCommand() {
        List<String> args = commandFactory.buildPreviewCommand("/tmp/project.xml", "/tmp/preview.mp4");
        assertNotNull(args);
        assertTrue(args.contains("width=854"));
        assertTrue(args.contains("height=480"));
        assertTrue(args.contains("preset=ultrafast"));
    }
}
