package com.example.platform.render.infrastructure.shotstack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShotstackTimelineMapperTest {

    private final ShotstackTimelineMapper mapper =
            new ShotstackTimelineMapper(new TimelineScriptParser());

    @Test
    void mapsVideoClipToShotstackPayload() {
        String script = """
                {"tracks":[{"type":"VIDEO","clips":[{
                  "media_reference":"file:///tmp/clip.mp4",
                  "clipDuration":12.5,
                  "timelineStart":0,
                  "assetInPoint":0,
                  "assetOutPoint":12.5
                }]}]}
                """;

        ObjectNode payload = mapper.toEditPayload(script, Map.of("resolution", "hd")).orElseThrow();
        assertEquals("mp4", payload.path("output").path("format").asText());
        assertEquals("hd", payload.path("output").path("resolution").asText());
        assertEquals("file:///tmp/clip.mp4",
                payload.path("timeline").path("tracks").get(0).path("clips").get(0)
                        .path("asset").path("src").asText());
        assertEquals(12.5, payload.path("timeline").path("tracks").get(0).path("clips").get(0)
                .path("length").asDouble(), 0.01);
    }

    @Test
    void emptyTimelineReturnsEmpty() {
        assertTrue(mapper.toEditPayload("{}", Map.of()).isEmpty());
    }
}
