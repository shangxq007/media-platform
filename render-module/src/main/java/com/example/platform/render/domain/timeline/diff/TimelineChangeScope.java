package com.example.platform.render.domain.timeline.diff;

/**
 * Scope of a timeline change operation.
 * Internal domain model.
 */
public enum TimelineChangeScope {
    TIMELINE,
    TRACK,
    CLIP,
    ASSET_BINDING,
    TEXT_OVERLAY,
    CAPTION,
    WATERMARK,
    TEMPLATE_APPLICATION,
    COMPOSITE_TEMPLATE,
    WORKFLOW_STEP,
    OUTPUT_PROFILE,
    METADATA,
    ARTIFACT_DAG,
    PRODUCT_LINEAGE
}
