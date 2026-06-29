package com.example.platform.render.domain.visual;

import java.util.Map;

/**
 * Issue found during visual capability validation.
 * Immutable record. Internal domain model.
 */
public record VisualCapabilityIssue(
        VisualCapabilityIssueCode code,
        VisualCapabilityIssueSeverity severity,
        String message,
        Map<String, String> safeMetadata
) {
    public VisualCapabilityIssue {
        if (code == null) throw new IllegalArgumentException("VisualCapabilityIssue.code must not be null");
        if (severity == null) throw new IllegalArgumentException("VisualCapabilityIssue.severity must not be null");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("VisualCapabilityIssue.message must not be blank");
        safeMetadata = safeMetadata == null ? Map.of() : Map.copyOf(safeMetadata);
    }

    public static VisualCapabilityIssue blocking(VisualCapabilityIssueCode code, String message) {
        return new VisualCapabilityIssue(code, VisualCapabilityIssueSeverity.BLOCKING, message, Map.of());
    }

    public static VisualCapabilityIssue error(VisualCapabilityIssueCode code, String message) {
        return new VisualCapabilityIssue(code, VisualCapabilityIssueSeverity.ERROR, message, Map.of());
    }

    public static VisualCapabilityIssue warning(VisualCapabilityIssueCode code, String message) {
        return new VisualCapabilityIssue(code, VisualCapabilityIssueSeverity.WARNING, message, Map.of());
    }
}
