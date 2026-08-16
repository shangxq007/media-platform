package com.example.platform.render.domain.interchange;

import java.util.List;
import java.util.Map;
import com.example.platform.render.domain.legacy.TimelineMarker;
import com.example.platform.render.domain.legacy.TimelineTransition;
import com.example.platform.render.domain.planning.ExternalRenderNode;
import com.example.platform.render.domain.planning.FinalComposerHint;

/**
 * Parsed extensions from Internal Timeline JSON (v2 fields or metadata).
 */
public record TimelineExtensions(
        String schemaVersion,
        FinalComposerHint finalComposer,
        List<ExternalRenderNode> externalRenderNodes,
        List<TimelineMarker> markers,
        List<TimelineTransition> transitions,
        Map<String, String> packagingHints,
        boolean otioExportLossy) {

    public static TimelineExtensions defaults() {
        return new TimelineExtensions(
                "1.0",
                FinalComposerHint.AUTO,
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                false);
    }
}
