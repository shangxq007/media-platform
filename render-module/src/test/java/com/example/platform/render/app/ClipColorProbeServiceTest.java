package com.example.platform.render.app;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.infrastructure.ColorProbeMetadata;
import com.example.platform.render.infrastructure.MediaProbeResult;
import com.example.platform.render.infrastructure.MediaProbeService;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClipColorProbeServiceTest {

    @Test
    void enrichesClipAssetMetadata() {
        MediaProbeService probeService = mock(MediaProbeService.class);
        when(probeService.probeAbsolute(anyString(), anyString()))
                .thenReturn(new MediaProbeResult("j", true, "/tmp/a.mp4", 1000, 5000,
                        1920, 1080, "h264", "aac", 30, 0, 2, 44100, List.of(), "",
                        new ColorProbeMetadata("bt709", "bt709", "bt709", "tv", "yuv420p", false)));

        ClipColorProbeService service = new ClipColorProbeService(
                new TimelineScriptParser(),
                probeService,
                new TimelineColorMetadataService());

        String timeline = """
                {
                  "tracks":[{"type":"VIDEO","clips":[{
                    "media_reference":"file:///tmp/a.mp4",
                    "clipDuration":5,"timelineStart":0,"assetInPoint":0,"assetOutPoint":5
                  }]}]
                }
                """;
        ClipColorProbeService.ClipProbeResult result =
                service.probeAndEnrichTimeline(timeline, "probe");
        assertTrue(result.success());
        assertEquals(1, result.clipsProbed());
        assertTrue(result.timelineJson().contains("platform.color.space"));
    }
}
