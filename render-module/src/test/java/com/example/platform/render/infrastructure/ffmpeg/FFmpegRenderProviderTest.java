package com.example.platform.render.infrastructure.ffmpeg;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.platform.extension.app.ProcessToolRunner;
import com.example.platform.extension.domain.ToolExecutionRequest;
import com.example.platform.extension.domain.ToolExecutionResult;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.infrastructure.RenderProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FFmpegRenderProviderTest {

    private FFmpegRenderProvider provider;
    private ProcessToolRunner mockToolRunner;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        mockToolRunner = mock(ProcessToolRunner.class);
        FFmpegCommandFactory factory = new FFmpegCommandFactory();
        provider = new FFmpegRenderProvider(mockToolRunner, factory, new TimelineScriptParser(), null);
        provider.setStorageRoot(tempDir.toString());

        Path input = tempDir.resolve("input.mp4");
        Files.writeString(input, "fake");
        Instant now = Instant.now();
        when(mockToolRunner.execute(any(ToolExecutionRequest.class)))
                .thenReturn(ToolExecutionResult.success(0, "ffmpeg", "", now, now.plusMillis(50)));
    }

    @Test
    void rendersTimelineWithLocalClip(@TempDir Path tempDir) throws Exception {
        Path input = tempDir.resolve("clip.mp4");
        Files.writeString(input, "video");

        String timeline = """
                {"tracks":[{"type":"VIDEO","children":[{"media_reference":"file://%s",
                "source_range":{"start_time":0,"duration":2}}]}]}
                """.formatted(input);

        RenderProvider.RenderResult result = provider.render("job-ff-1", timeline, "default_1080p");
        assertNotNull(result.artifactId());
        assertEquals("mp4", result.format());
        verify(mockToolRunner).execute(any(ToolExecutionRequest.class));
    }

    @Test
    void validateEnvironmentDelegatesToFfmpeg() {
        RenderProvider.EnvironmentValidationResult env = provider.validateEnvironment();
        assertNotNull(env);
    }

    @Test
    void getSupportedProfilesIncludesSocial() {
        List<String> profiles = provider.getSupportedProfiles();
        assertTrue(profiles.contains("social_1080p"));
    }
}
