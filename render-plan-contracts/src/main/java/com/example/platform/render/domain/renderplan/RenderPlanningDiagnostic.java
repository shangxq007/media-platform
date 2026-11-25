package com.example.platform.render.domain.renderplan;

import java.util.Objects;
import java.util.Optional;

/**
 * Typed planning diagnostic (C24). Deterministic ordering: by code, then node id,
 * then message.
 *
 * @param code    diagnostic code
 * @param nodeId  optional node the diagnostic is bound to
 * @param severity error/warning/info
 * @param message human-readable explanation (never an arbitrary exception)
 */
public record RenderPlanningDiagnostic(
        RenderPlanningDiagnosticCode code,
        Optional<RenderNodeId> nodeId,
        RenderDiagnosticSeverity severity,
        String message) {

    public RenderPlanningDiagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(message, "message");
    }

    public static RenderPlanningDiagnostic diagnostic(
            RenderPlanningDiagnosticCode code,
            RenderDiagnosticSeverity severity,
            String message) {
        return new RenderPlanningDiagnostic(code, Optional.empty(), severity, message);
    }

    public static RenderPlanningDiagnostic forNode(
            RenderPlanningDiagnosticCode code,
            RenderNodeId nodeId,
            RenderDiagnosticSeverity severity,
            String message) {
        return new RenderPlanningDiagnostic(code, Optional.of(nodeId), severity, message);
    }
}
