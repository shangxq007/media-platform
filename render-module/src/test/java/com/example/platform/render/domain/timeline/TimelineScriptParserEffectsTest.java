package com.example.platform.render.domain.timeline;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TimelineScriptParserEffectsTest {

    private final TimelineScriptParser parser = new TimelineScriptParser();

    @Test
    void shouldParseClipEffectsFromOtioChildren() {
        String json = """
                {
                  "tracks": [{
                    "id": "v1",
                    "type": "video",
                    "children": [{
                      "id": "c1",
                      "media_reference": "file:///tmp/a.mp4",
                      "source_range": { "start_time": 0, "duration": 5 },
                      "effects": [{
                        "effectKey": "video.blur",
                        "packId": "builtin-core",
                        "parameters": { "radius": 3.0 }
                      }]
                    }]
                  }]
                }
                """;

        TimelineSpec spec = parser.parse(json).orElseThrow();
        TimelineClip clip = spec.tracks().get(0).clips().get(0);
        assertThat(clip.effects()).hasSize(1);
        assertThat(clip.effects().get(0).effectKey()).isEqualTo("video.blur");
        assertThat(clip.effects().get(0).packId()).isEqualTo("builtin-core");
        assertThat(clip.effects().get(0).parameters().get("radius")).isEqualTo(3.0);
    }
}
