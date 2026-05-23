package com.example.platform.render.domain.timeline;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TimelineScriptParserTest {

    private final TimelineScriptParser parser = new TimelineScriptParser();

    @Test
    void resolvesFileUri() {
        assertEquals("/tmp/video.mp4", parser.resolveLocalPath("file:///tmp/video.mp4", "/data"));
    }

    @Test
    void parsesOtioStyleTimeline() {
        String json = """
                {"tracks":[{"type":"VIDEO","children":[{"media_reference":"file:///a.mp4",
                "source_range":{"start_time":0,"duration":10}}]}]}
                """;
        assertTrue(parser.isTimelineJson(json));
        TimelineSpec spec = parser.parse(json).orElseThrow();
        assertFalse(parser.videoClipsInOrder(spec).isEmpty());
    }
}
