package com.example.platform.render.domain.timeline;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class OpenTimelineioAdapterTest {

    @Test
    void shouldRoundTripTimelineSpec() {
        TimelineOutputSpec output = TimelineOutputSpec.mp4_1080p30();
        TimelineSpec timeline = TimelineSpec.create("tl-1", "Test", output);

        String otioJson = OpenTimelineioAdapter.toOtioJson(timeline);
        assertNotNull(otioJson);
        assertTrue(otioJson.contains("tracks"));
        assertTrue(otioJson.contains("OTIO_SCHEMA"));

        TimelineSpec imported = OpenTimelineioAdapter.fromOtioJson(otioJson);
        assertEquals("tl-1", imported.id());
        assertNotNull(imported.tracks());
        assertFalse(imported.tracks().isEmpty());
        assertEquals("tl-1-v1", imported.tracks().get(0).id());
    }

    @Test
    void shouldImportOtioStyleJson() {
        String otioJson = """
                {
                  "id": "tl-otio",
                  "tracks": [{
                    "id": "v1",
                    "type": "VIDEO",
                    "children": [{
                      "id": "c1",
                      "media_reference": "file:///tmp/input.mp4",
                      "source_range": { "start_time": 1.0, "duration": 5.0 }
                    }]
                  }],
                  "format": "mp4"
                }
                """;
        TimelineSpec spec = OpenTimelineioAdapter.fromOtioJson(otioJson);
        assertEquals("tl-otio", spec.id());
        assertEquals(1, spec.tracks().size());
        assertEquals(1, spec.tracks().get(0).clips().size());
    }
}
