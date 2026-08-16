package com.example.platform.timeline.app;

/**
 * GCR-1 CORRECTION V1: Timeline-owned port for payload normalization and editor
 * projection. Implemented at the Render boundary (RenderTimelinePayloadCodec)
 * where interchange/editor models live; the revision authority depends only on
 * this port, never on Render implementations.
 */
public interface TimelinePayloadCodec {

    /** Normalize an input payload to Internal Timeline Schema 1.0 JSON. */
    String ensureInternalTimelineJson(String payload);

    /** Project a timeline payload to editor JSON. */
    String toEditorJson(String payload);
}
