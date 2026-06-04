package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.shared.test.FixturePath;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InternalTimelineWriterTemplateTest {

    private final InternalTimelineAdapter adapter = TimelineTestSupport.internalTimelineAdapter();
    private final InternalTimelineWriter writer = new InternalTimelineWriter(new TimelineExtensionsReader());

    @Test
    void roundTripPreservesExternalTemplatesAndNodes() throws Exception {
        Path path = FixturePath.docsFixture("media-rendering/examples/timeline-v1-full-sample.json");
        String sample = Files.readString(path);
        var spec = adapter.toSpec(sample).orElseThrow();
        String v1 = writer.toJson(spec);

        assertTrue(v1.contains("tpl_remotion_title"));
        assertTrue(v1.contains("tpl_blender_logo"));
        assertTrue(v1.contains("tpl_natron_glow"));
        assertTrue(v1.contains("xr_blender_intro"));
        assertTrue(v1.contains("compositionId"));
        assertTrue(v1.contains("layer_sub_zh") || v1.contains("layer_sticker_logo"));
        assertTrue(v1.contains("style_ass_main"));
    }
}
