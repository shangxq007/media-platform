package com.example.platform.render.infrastructure.timeline;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.TimelineScriptParser;
import org.junit.jupiter.api.Test;

class EditorTimelineConverterTest {

    private final EditorTimelineConverter converter = new EditorTimelineConverter();
    private final TimelineScriptParser parser = new TimelineScriptParser();

    @Test
    void convertsEditorTracksToOtioWithMediaReference() {
        String editor = """
                {
                  "tracks": [{
                    "id": "v1",
                    "name": "Video 1",
                    "type": "video",
                    "clips": [{
                      "id": "tc1",
                      "clipId": "c1",
                      "start": 0,
                      "duration": 5,
                      "clipStart": 0,
                      "clipEnd": 5
                    }]
                  }],
                  "clips": [{
                    "id": "c1",
                    "name": "Clip",
                    "sourceUrl": "/tmp/test.mp4"
                  }]
                }
                """;
        String otio = converter.toOtioJson(editor);
        assertTrue(parser.isTimelineJson(otio));
        assertTrue(parser.parse(otio).isPresent());
        assertTrue(otio.contains("media_reference"));
    }
}
