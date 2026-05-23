package com.example.platform.render.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.infrastructure.ColorProbeMetadata;
import org.junit.jupiter.api.Test;

class TimelineColorMetadataServiceTest {

    private final TimelineColorMetadataService service = new TimelineColorMetadataService();

    @Test
    void mergesColorIntoTimelineMetadata() {
        String json = """
                {"id":"tl1","metadata":{},"project":{"width":1920,"height":1080}}
                """;
        ColorProbeMetadata color = new ColorProbeMetadata(
                "bt709", "bt709", "bt709", "tv", "yuv420p", false);
        String merged = service.mergeProbeMetadata(json, color);
        assertTrue(merged.contains("platform.color.space"));
        assertTrue(merged.contains("bt709"));
    }
}
