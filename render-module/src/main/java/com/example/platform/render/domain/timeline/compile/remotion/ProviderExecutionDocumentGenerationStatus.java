package com.example.platform.render.domain.timeline.compile.remotion;

/**
 * Status of provider execution document generation.
 * Internal only.
 */
public enum ProviderExecutionDocumentGenerationStatus {
    GENERATED,
    GENERATED_WITH_WARNINGS,
    REJECTED_UNSUPPORTED,
    REJECTED_INVALID,
    SKIPPED_NON_REMOTION,
    FAILED_CLOSED
}
