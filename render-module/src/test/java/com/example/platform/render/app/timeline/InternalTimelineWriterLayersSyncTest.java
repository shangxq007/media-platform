package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.shared.test.FixturePath;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InternalTimelineWriterLayersSyncTest {

    private final InternalTimelineAdapter adapter = TimelineTestSupport.internalTimelineAdapter();
    private final InternalTimelineWriter writer = new InternalTimelineWriter(new TimelineExtensionsReader());

    @Test
    void roundTripSyncsSubtitleLayersWithTracks() throws Exception {
        Path path = FixturePath.docsFixture("media-rendering/examples/timeline-v1-full-sample.json");
        String sample = Files.readString(path);
        String v1 = writer.toJson(adapter.toSpec(sample).orElseThrow());
        assertTrue(v1.contains("layer_sub_zh"));
        assertTrue(v1.contains("\"subtitleTrackId\" : \"sub_zh\"")
                || v1.contains("\"subtitleTrackId\": \"sub_zh\""));
        assertTrue(v1.contains("layer_sticker_logo"));
    }
}
