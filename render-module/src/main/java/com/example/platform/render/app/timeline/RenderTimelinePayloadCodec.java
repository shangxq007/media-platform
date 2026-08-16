package com.example.platform.render.app.timeline;

import com.example.platform.timeline.app.TimelinePayloadCodec;
import org.springframework.stereotype.Component;

/**
 * GCR-1 CORRECTION V1: Render-side implementation of the Timeline-owned
 * {@link TimelinePayloadCodec} port. Wraps the interchange/editor converters
 * that operate on Render interchange models (TimelineSpec) — the revision
 * authority in timeline-module depends only on the port.
 */
@Component
public class RenderTimelinePayloadCodec implements TimelinePayloadCodec {

    private final TimelineConversionService conversionService;
    private final InternalTimelineToEditorConverter editorConverter;

    public RenderTimelinePayloadCodec(TimelineConversionService conversionService,
                                      InternalTimelineToEditorConverter editorConverter) {
        this.conversionService = conversionService;
        this.editorConverter = editorConverter;
    }

    @Override
    public String ensureInternalTimelineJson(String payload) {
        return conversionService.ensureInternalTimelineJson(payload);
    }

    @Override
    public String toEditorJson(String payload) {
        return editorConverter.toEditorJson(payload);
    }
}
