package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.interchange.OpenTimelineioAdapter;
import com.example.platform.render.domain.interchange.TimelineExtensionsReader;
import com.example.platform.render.domain.interchange.TimelineOutputSpec;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.domain.interchange.TimelineTextOverlay;
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
        var overlay = TimelineTextOverlay.of("cue1", "Hello",
                new com.example.platform.fonttext.typography.FontFamilyName("DejaVu Sans"), 1.0, 2.0);
        TimelineSpec spec = new TimelineSpec(
                base.id(), base.name(), base.description(), base.tracks(),
                List.of(overlay), base.outputSpec(), 3.0, Map.of("platform.import.source", "test"));
        String v1 = writer.toJson(spec);
        assertTrue(v1.contains("subtitleTracks"));
        assertTrue(v1.contains("styles"));
        assertTrue(v1.contains("layer_sub_imported"));
    }
}
