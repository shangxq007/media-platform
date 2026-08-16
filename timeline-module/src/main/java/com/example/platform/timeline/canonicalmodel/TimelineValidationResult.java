package com.example.platform.timeline.canonicalmodel;

import java.util.Collection;
import java.util.List;

public record TimelineValidationResult(List<TimelineDiagnostic> diagnostics) {
    public TimelineValidationResult {
        diagnostics = diagnostics == null ? List.of() : diagnostics.stream().sorted().toList();
    }

    public static TimelineValidationResult of(Collection<TimelineDiagnostic> diagnostics) {
        return new TimelineValidationResult(diagnostics == null ? List.of() : List.copyOf(diagnostics));
    }

    public boolean hasFatalErrors() {
        return diagnostics.stream().anyMatch(diagnostic -> diagnostic.severity() == TimelineDiagnosticSeverity.ERROR);
    }
}
