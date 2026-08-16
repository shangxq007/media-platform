package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.fonttext.typography.FontFamilyName;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.timeline.app.TimelineImportRequest.ImportClip;
import com.example.platform.timeline.app.TimelineImportRequest.ImportClipEffect;
import com.example.platform.timeline.app.TimelineImportRequest.ImportExternalRenderNode;
import com.example.platform.timeline.app.TimelineImportRequest.ImportOutput;
import com.example.platform.timeline.app.TimelineImportRequest.ImportTextOverlay;
import com.example.platform.timeline.app.TimelineImportRequest.ImportTrack;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * GCR-1 CORRECTION V2: canonical construction authority tests (moved from the
 * render-side InternalTimelineWriter tests — construction now lives in
 * timeline-module). Proves the constructed document is internal-1.0, canonical,
 * and passes the E1b gate.
 */
class TimelineImportServiceTest {

    private final TimelineImportService service = new TimelineImportService();
    private final InternalTimelineValidationService validation = new InternalTimelineValidationService();

    private static TimelineImportRequest request(
            String id, List<ImportTrack> tracks, List<ImportTextOverlay> overlays, double durationSec) {
        return new TimelineImportRequest(
                id, id, 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                tracks, overlays, null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of("platform.import.source", "test"), durationSec);
    }

    @Test
    void buildsSchemaV1DocumentWithComposition() {
        TimelineImportRequest req = request("tl-import", List.of(
                new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080,
                                0.0, 2.0, 0.0, 2.0, List.of())))), List.of(), 2.0);
        String v1 = service.importTimeline(req);
        assertTrue(v1.contains("\"schemaVersion\" : \"1.0\"") || v1.contains("\"schemaVersion\": \"1.0\""));
        assertTrue(v1.contains("composition"));
        // The constructed canonical document must pass the canonical validation authority.
        assertTrue(validation.validate(v1).valid());
    }

    @Test
    void writesSubtitleStylesLayersAndClipEffects() {
        TimelineImportRequest req = new TimelineImportRequest(
                "tl-rich", "Rich", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080,
                                0.0, 2.0, 0.0, 2.0,
                                List.of(new ImportClipEffect("fx1", "blur", Map.of("radius", 3))))))),
                List.of(new ImportTextOverlay("cue1", "Hello", new FontFamilyName("DejaVu Sans"), 1.0, 2.0)),
                null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of("platform.import.source", "test"), 3.0);
        String v1 = service.importTimeline(req);
        assertTrue(v1.contains("subtitleTracks"));
        assertTrue(v1.contains("styles"));
        assertTrue(v1.contains("layer_sub_imported"));
        assertTrue(v1.contains("\"effectKey\""));
        assertTrue(validation.validate(v1).valid());
    }

    @Test
    void preservesExternalTemplatesAndNodes() {
        ObjectNode templates = InternalTimelineJson.mapper().createObjectNode();
        ObjectNode tpl = InternalTimelineJson.mapper().createObjectNode();
        tpl.put("id", "tpl_remotion_title");
        tpl.put("backend", "remotion");
        templates.set("tpl_remotion_title", tpl);
        TimelineImportRequest req = new TimelineImportRequest(
                "tl-tpl", "Templates", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(), List.of(), null, templates, null, null, false,
                List.of(new ImportExternalRenderNode(
                        "xr_remotion_title", "remotion", "tpl_remotion_title", null, null,
                        0.0, 2.0, Map.of("compositionId", "Comp"), null)),
                "AUTO", false, Map.of(), Map.of(), 2.0);
        String v1 = service.importTimeline(req);
        assertTrue(v1.contains("tpl_remotion_title"));
        assertTrue(v1.contains("xr_remotion_title"));
        assertTrue(v1.contains("compositionId"));
        assertTrue(validation.validate(v1).valid());
    }

    @Test
    void rejectsNonCanonicalConstructionThroughGate() {
        // A blank clip id must be rejected by the canonical gate at construction time.
        TimelineImportRequest req = request("tl-bad", List.of(
                new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("", "ast_1", null, 0, 0, 0.0, 2.0, 0.0, 2.0, List.of())))),
                List.of(), 2.0);
        assertThrows(TimelineCanonicalRejectionException.class, () -> service.importTimeline(req));
    }
}
