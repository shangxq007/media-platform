package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.shared.test.FixturePath;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InternalTimelineToEditorConverterTest {

    private final InternalTimelineToEditorConverter converter = new InternalTimelineToEditorConverter();
    private final InternalTimelineWriter writer =
            new InternalTimelineWriter(new TimelineExtensionsReader());
    private final TimelineConversionService conversionService = new TimelineConversionService(
            new TimelineSpecResolver(TimelineTestSupport.internalTimelineAdapter(), new TimelineScriptParser()),
            writer);

    @Test
    void convertsInternalSampleToEditorV2() throws Exception {
        Path sample = FixturePath.docsFixture("media-rendering/examples/timeline-v1-full-sample.json")
                .normalize()
                .toAbsolutePath();
        String internal = Files.readString(sample);
        String editor = converter.toEditorJson(internal);

        assertTrue(editor.contains("\"schemaVersion\""));
        assertTrue(editor.contains("2.0.0"));
        assertTrue(editor.contains("\"tracks\""));
        assertTrue(editor.contains("\"clips\""));
        assertTrue(editor.contains("internal-to-editor-v2"));
    }

    @Test
    void roundTripEditorThroughInternalPreservesClipCounts() {
        TimelineSpec spec = TimelineSpec.create("tl-round", "Round", TimelineOutputSpec.mp4_1080p30());
        String internal = writer.toJson(spec);
        String editor = converter.toEditorJson(internal);
        var preview = conversionService.preview(editor);
        assertEquals("editor-2.0.0", preview.sourceSchema());
        assertFalse(preview.internalTimelineJson().isBlank());
    }

    @Test
    void rejectsNonInternalPayload() {
        assertThrows(IllegalArgumentException.class, () -> converter.toEditorJson("{\"tracks\":[]}"));
    }
}
