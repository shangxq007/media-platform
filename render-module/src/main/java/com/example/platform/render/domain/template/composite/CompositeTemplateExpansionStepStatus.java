package com.example.platform.render.domain.template.composite;

/**
 * Status of a composite template expansion step.
 * Internal domain model — dry-run / semantic status only.
 */
public enum CompositeTemplateExpansionStepStatus {
    PENDING,
    READY,
    VALIDATION_FAILED,
    NOT_IMPLEMENTED,
    SKIPPED
}
