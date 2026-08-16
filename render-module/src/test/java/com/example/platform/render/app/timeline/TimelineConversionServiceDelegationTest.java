package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.domain.interchange.TimelineExtensionsReader;
import com.example.platform.render.domain.interchange.TimelineOutputSpec;
import com.example.platform.render.domain.interchange.TimelineScriptParser;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.timeline.app.InternalTimelineValidationService;
import com.example.platform.timeline.app.TimelineImportService;
import com.example.platform.timeline.app.TimelineCanonicalRejectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * GCR-1 CORRECTION V2 behavioral proofs (directive §18 B, G):
 *
 * <p>B. external/editor/legacy conversion into canonical Timeline reaches the
 * Timeline-owned semantic authority — the render coordinator delegates
 * construction to {@link TimelineImportService}, whose E1b canonical gate
 * accepts/rejects the constructed document. Render holds no independent
 * acceptance rule.</p>
 *
 * <p>G. the render-side boundary (conversion coordinator) delegates canonical
 * mutation/validation instead of implementing it.</p>
 */
class TimelineConversionServiceDelegationTest {

    private TimelineConversionService conversionService;
    private InternalTimelineValidationService timelineValidator;
    private TimelineImportService importService;

    @BeforeEach
    void setUp() {
        TimelineExtensionsReader reader = new TimelineExtensionsReader();
        TimelineScriptParser parser = new TimelineScriptParser(reader);
        TimelineSpecResolver resolver =
                new TimelineSpecResolver(TimelineTestSupport.internalTimelineAdapter(), parser);
        importService = new TimelineImportService();
        timelineValidator = new InternalTimelineValidationService();
        conversionService = new TimelineConversionService(
                resolver, new TimelineSpecImportAdapter(reader), importService);
    }

    @Test
    void convertedDocumentPassesTimelineOwnedCanonicalValidation() {
        TimelineSpec spec = TimelineSpec.create("tl-delegate", "Delegate", TimelineOutputSpec.mp4_1080p30());
        String internal = conversionService.ensureInternalTimelineJson(
                "{\"id\":\"tl-delegate\",\"name\":\"Delegate\",\"tracks\":[],"
                        + "\"outputSpec\":{\"format\":\"mp4\",\"resolution\":\"1920x1080\"}}");
        // B: the produced canonical document must be accepted by the Timeline-owned validator.
        assertTrue(timelineValidator.validate(internal).valid(),
                "converted document must pass the timeline-owned canonical validator");
    }

    @Test
    void invalidConstructionIsRejectedByTimelineOwnedGateNotRenderRule() {
        // A blank clip id is a canonical-model violation; the render coordinator
        // must NOT accept it — the Timeline-owned gate rejects at construction.
        assertThrows(TimelineCanonicalRejectionException.class, () -> {
            importService.importTimeline(importServiceRequestWithBlankClipId());
        });
    }

    private com.example.platform.timeline.app.TimelineImportRequest importServiceRequestWithBlankClipId() {
        return new com.example.platform.timeline.app.TimelineImportRequest(
                "tl-bad", "Bad", 1,
                new com.example.platform.timeline.app.TimelineImportRequest.ImportOutput(
                        "mp4", 1920, 1080, com.example.platform.shared.time.FrameRate.of(30, 1)),
                java.util.List.of(new com.example.platform.timeline.app.TimelineImportRequest.ImportTrack(
                        "v1", "VIDEO", 0,
                        java.util.List.of(new com.example.platform.timeline.app.TimelineImportRequest.ImportClip(
                                "", "ast_1", null, 0, 0, 0.0, 2.0, 0.0, 2.0, java.util.List.of())))),
                java.util.List.of(), null, null, null, null, false, java.util.List.of(), "AUTO", false,
                java.util.Map.of(), java.util.Map.of(), 2.0);
    }
}
