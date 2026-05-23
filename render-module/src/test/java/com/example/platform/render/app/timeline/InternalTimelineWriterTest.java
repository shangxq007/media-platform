package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.OpenTimelineioAdapter;
import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineTextOverlay;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InternalTimelineWriterTest {

    private final InternalTimelineWriter writer =
            new InternalTimelineWriter(new TimelineExtensionsReader());
    private final InternalTimelineAdapter adapter = TimelineTestSupport.internalTimelineAdapter();

    @Test
    void otioImportRoundTripsToSchemaV1() {
        TimelineSpec spec = TimelineSpec.create("tl-otio", "OTIO", TimelineOutputSpec.mp4_1080p30());
        String otio = OpenTimelineioAdapter.toOtioJson(spec);
        var imported = OpenTimelineioAdapter.importWithReport(otio);
        String v1 = writer.toJson(imported.timeline(), imported.extensions());
        assertTrue(v1.contains("\"schemaVersion\" : \"1.0\"") || v1.contains("\"schemaVersion\": \"1.0\""));
        assertTrue(v1.contains("composition"));
        assertTrue(adapter.toSpec(v1).isPresent());
    }

    @Test
    void writesSubtitleStylesLayersAndClipEffects() {
        TimelineSpec base = TimelineSpec.create("tl-rich", "Rich", TimelineOutputSpec.mp4_1080p30());
        var overlay = TimelineTextOverlay.of("cue1", "Hello", 1.0, 2.0);
        TimelineSpec spec = new TimelineSpec(
                base.id(), base.name(), base.description(), base.tracks(),
                List.of(overlay), base.outputSpec(), 3.0, Map.of("platform.import.source", "test"));
        String v1 = writer.toJson(spec);
        assertTrue(v1.contains("subtitleTracks"));
        assertTrue(v1.contains("styles"));
        assertTrue(v1.contains("layer_sub_imported"));
    }
}
