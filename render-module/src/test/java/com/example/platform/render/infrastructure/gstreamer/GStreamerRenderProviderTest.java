package com.example.platform.render.infrastructure.gstreamer;

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

class GStreamerRenderProviderTest {

    private GStreamerRenderProvider provider;
    private ProcessToolRunner mockToolRunner;
    private GStreamerCommandFactory commandFactory;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        mockToolRunner = mock(ProcessToolRunner.class);
        commandFactory = new GStreamerCommandFactory();
        provider = new GStreamerRenderProvider(mockToolRunner, commandFactory);
        provider.setStorageRoot(tempDir.toString());
        // Default mock behavior for successful execution
        Instant now = Instant.now();
        when(mockToolRunner.execute(any(ToolExecutionRequest.class)))
                .thenReturn(ToolExecutionResult.success(0, "GStreamer 1.24", "", now, now.plusMillis(100)));
    }

    @Test
    void getSupportedProfilesReturnsGStreamerProfiles() {
        List<String> profiles = provider.getSupportedProfiles();
        assertNotNull(profiles);
        assertTrue(profiles.contains("gstreamer_1080p"));
        assertTrue(profiles.contains("gstreamer_720p"));
    }

    @Test
    void supportsStreamingCapabilities() {
        assertTrue(provider.supports("pipeline"));
        assertTrue(provider.supports("real-time"));
        assertTrue(provider.supports("streaming"));
        assertTrue(provider.supports("multi-track"));
        assertTrue(provider.supports("compositing"));
        assertTrue(provider.supports("subtitle-overlay"));
        assertTrue(provider.supports("filter-graph"));
    }

    @Test
    void environmentValidationReturnsResult() {
        RenderProvider.EnvironmentValidationResult result = provider.validateEnvironment();
        assertNotNull(result);
    }

    @Test
    void renderWithEmptyTimelineReturnsResult(@TempDir Path tempDir) {
        RenderProvider.RenderResult result = provider.render(
                "job-gst-1", "{\"tracks\":[]}", "default_1080p");

        assertNotNull(result);
        assertNotNull(result.artifactId());
        assertEquals("mp4", result.format());
        assertTrue(result.storageUri().contains("artifacts/job-gst-1"));
    }

    @Test
    void renderWith720pProfileReturns720p(@TempDir Path tempDir) {
        RenderProvider.RenderResult result = provider.render(
                "job-gst-2", "{\"tracks\":[]}", "default_720p");

        assertNotNull(result);
        assertEquals("1280x720", result.resolution());
    }

    @Test
    void renderFailureThrowsPlatformException(@TempDir Path tempDir) {
        Instant now = Instant.now();
        when(mockToolRunner.execute(any(ToolExecutionRequest.class)))
                .thenReturn(ToolExecutionResult.failed(1, "", "gst error", now, now.plusMillis(100)));

        assertThrows(com.example.platform.shared.web.PlatformException.class, () -> {
            provider.render("job-gst-3", "{\"tracks\":[]}", "default_1080p");
        });
    }

    @Test
    void commandFactoryBuildsTestSourcePipeline() {
        List<String> args = commandFactory.buildTestSourcePipeline("/tmp/output.mp4",
                com.example.platform.render.infrastructure.RenderPreset.PREVIEW_720P);

        assertNotNull(args);
        assertFalse(args.isEmpty());
        assertTrue(args.contains("videotestsrc"));
        assertTrue(args.contains("filesink"));
        assertTrue(args.contains("location=/tmp/output.mp4"));
    }

    @Test
    void commandFactoryBuildsTranscodePipeline() {
        List<String> args = commandFactory.buildTranscodePipeline(
                "/tmp/input.mp4", "/tmp/output.mp4",
                com.example.platform.render.infrastructure.RenderPreset.DEFAULT);

        assertNotNull(args);
        assertTrue(args.contains("filesrc"));
        assertTrue(args.contains("location=/tmp/input.mp4"));
        assertTrue(args.contains("decodebin"));
        assertTrue(args.contains("x264enc"));
        assertTrue(args.contains("filesink"));
        assertTrue(args.contains("location=/tmp/output.mp4"));
    }

    @Test
    void commandFactoryBuildsSubtitleOverlayPipeline() {
        List<String> args = commandFactory.buildSubtitleOverlayPipeline(
                "/tmp/input.mp4", "/tmp/output.mp4", "Hello World",
                com.example.platform.render.infrastructure.RenderPreset.DEFAULT);

        assertNotNull(args);
        assertTrue(args.contains("textoverlay"));
        assertTrue(args.contains("text=\"Hello World\""));
        assertTrue(args.contains("valignment=bottom"));
    }

    @Test
    void pipelineDoesNotUseShellConcatenation() {
        List<String> args = commandFactory.buildTestSourcePipeline("/tmp/output.mp4",
                com.example.platform.render.infrastructure.RenderPreset.DEFAULT);

        for (String arg : args) {
            assertFalse(arg.contains(";"),
                    "Argument should not contain semicolons: " + arg);
            assertFalse(arg.contains("|"),
                    "Argument should not contain pipe: " + arg);
            assertFalse(arg.contains("&&"),
                    "Argument should not contain &&: " + arg);
        }
    }
}
