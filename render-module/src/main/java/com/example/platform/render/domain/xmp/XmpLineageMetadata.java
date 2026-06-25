package com.example.platform.render.domain.xmp;

import java.time.Instant;
import java.util.List;

/**
 * XMP {@code lineage:*} namespace metadata.
 */
public record XmpLineageMetadata(
        String sourceAsset,
        List<String> derivedFrom,
        String processingStep,
        String workflowId,
        String runId,
        String operatorId,
        String parametersHash,
        Instant timestamp) {
}
