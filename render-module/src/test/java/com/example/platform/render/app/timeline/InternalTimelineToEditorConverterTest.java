package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.interchange.TimelineExtensionsReader;
import com.example.platform.timeline.app.TimelineImportService;
import com.example.platform.render.app.timeline.TimelineSpecImportAdapter;
import com.example.platform.render.domain.interchange.TimelineOutputSpec;
import com.example.platform.render.domain.interchange.TimelineScriptParser;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.shared.test.FixturePath;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InternalTimelineToEditorConverterTest {

    private final InternalTimelineToEditorConverter converter = new InternalTimelineToEditorConverter();
    private final TimelineSpecImportAdapter importAdapter = new TimelineSpecImportAdapter(new TimelineExtensionsReader());
    private final TimelineImportService importService = new TimelineImportService();
    private final TimelineConversionService conversionService = new TimelineConversionService(
            new TimelineSpecResolver(TimelineTestSupport.internalTimelineAdapter(), new TimelineScriptParser()),
            importAdapter,
            importService);

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
        String internal = importService.importTimeline(importAdapter.toRequest(spec));
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
