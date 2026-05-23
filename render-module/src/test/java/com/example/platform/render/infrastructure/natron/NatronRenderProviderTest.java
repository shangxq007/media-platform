package com.example.platform.render.infrastructure.natron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

class NatronRenderProviderTest {

    private NatronRenderProvider provider;
    private ProcessToolRunner toolRunner;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        toolRunner = mock(ProcessToolRunner.class);
        NatronRenderProviderProperties properties = new NatronRenderProviderProperties();
        properties.setPocEffectKey("video.natron_vignette");
        properties.setFallbackToFfmpeg(true);

        NatronRenderDurationResolver durationResolver = mock(NatronRenderDurationResolver.class);
        when(durationResolver.resolveDurationSeconds(any(), any())).thenReturn(12L);

        provider = new NatronRenderProvider(
                toolRunner,
                new NatronPocJobExtractor(new TimelineScriptParser()),
                new NatronPocCommandBuilder(),
                new NatronBatchScriptGenerator(),
                durationResolver,
                properties);
        provider.setStorageRoot(tempDir.toString());

        Path input = tempDir.resolve("in.mp4");
        Files.writeString(input, "x");
        Path output = tempDir.resolve("artifacts/job-natron/output.mp4");
        Files.createDirectories(output.getParent());
        Files.writeString(output, "out");

        when(toolRunner.execute(any(ToolExecutionRequest.class)))
                .thenReturn(ToolExecutionResult.success(0, "", "", Instant.now(), Instant.now()));
    }

    @Test
    void supportedProfilesIncludeNatronPoc() {
        List<String> profiles = provider.getSupportedProfiles();
        assertTrue(profiles.contains("natron_poc_1080p"));
    }

    @Test
    void renderProducesArtifact(@TempDir Path tempDir) throws Exception {
        Path input = tempDir.resolve("clip.mp4");
        Files.writeString(input, "fake");

        String script = """
                {"tracks":[{"type":"VIDEO","clips":[{
                  "media_reference":"file://%s",
                  "effects":[{"effectKey":"video.natron_vignette","parameters":{"intensity":0.5}}]
                }]}]}
                """.formatted(input.toString().replace("\\", "/"));

        RenderProvider.RenderResult result = provider.render("job-natron", script, "natron_poc_1080p");

        assertEquals("mp4", result.format());
        assertTrue(result.storageUri().contains("artifacts/job-natron/output.mp4"));
    }
}
