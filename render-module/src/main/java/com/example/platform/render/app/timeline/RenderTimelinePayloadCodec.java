package com.example.platform.render.app.timeline;

import com.example.platform.timeline.app.TimelinePayloadCodec;
import org.springframework.stereotype.Component;

/**
 * GCR-1 CORRECTION V2: Render-side implementation of the Timeline-owned
 * {@link TimelinePayloadCodec} port. This is a BOUNDARY ADAPTER: it performs
 * no canonical construction, validation, or serialization. Canonical
 * construction is delegated to the timeline-owned {@code TimelineImportService}
 * via {@link TimelineConversionService}; editor projection stays at the render
 * boundary. The revision authority in timeline-module depends only on the port.
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
