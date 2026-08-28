package com.example.platform.render.infrastructure.shotstack;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.domain.interchange.TimelineScriptParser;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShotstackRenderProviderTest {

    private ShotstackApiClient apiClient;
    private ShotstackRenderProvider provider;

    @BeforeEach
    void setUp(@TempDir Path storageRoot) {
        apiClient = mock(ShotstackApiClient.class);
        ShotstackRenderProviderProperties props = new ShotstackRenderProviderProperties();
        props.setEnabled(true);
        props.setApiKey("test-key");
        props.setApiUrl("https://api.shotstack.io/edit/v1");

        TimelineScriptParser parser = new TimelineScriptParser();
        provider = new ShotstackRenderProvider(
                new ShotstackTimelineMapper(parser),
                apiClient,
                props,
                parser);
        org.springframework.test.util.ReflectionTestUtils.setField(provider, "storageRoot", storageRoot.toString());
    }

    @Test
    void renderDownloadsCloudOutput(@TempDir Path storageRoot) throws Exception {
        String script = """
                {"tracks":[{"type":"VIDEO","clips":[{
                  "assetRef": {"storageUri": "file:///tmp/clip.mp4"},
                  "clipDuration":10,
                  "timelineStart":0,
                  "assetInPoint":0,
                  "assetOutPoint":10
                }]}]}
                """;

        when(apiClient.submitRender(any(ObjectNode.class))).thenReturn("render-abc");
        when(apiClient.pollUntilDone("render-abc"))
                .thenReturn(new ShotstackApiClient.ShotstackRenderStatus(true, "https://cdn.example/out.mp4", null));
        doAnswer(inv -> {
            Files.write(inv.getArgument(1), new byte[] {0, 0, 0, 8});
            return null;
        }).when(apiClient).downloadTo(anyString(), any(Path.class));

        var result = provider.render("job-shotstack-1", script, "shotstack_social_1080p");

        assertNotNull(result.artifactId());
        assertTrue(result.storageUri().contains("job-shotstack-1"));
        assertEquals("mp4", result.format());
        assertEquals(30L, result.duration());
        verify(apiClient).downloadTo(eq("https://cdn.example/out.mp4"), any(Path.class));
    }

    @Test
    void validateEnvironmentFailsWithoutApiKey() {
        ShotstackRenderProviderProperties empty = new ShotstackRenderProviderProperties();
        empty.setApiKey("");
        ShotstackRenderProvider p = new ShotstackRenderProvider(
                new ShotstackTimelineMapper(new TimelineScriptParser()),
                apiClient,
                empty,
                new TimelineScriptParser());
        assertFalse(p.validateEnvironment().valid());
    }
}
