package com.example.platform.render.domain.timeline.canonicalmodel;

import java.util.Comparator;
import java.util.Objects;

public record TimelineDiagnostic(
        TimelineDiagnosticCode code,
        TimelineDiagnosticSeverity severity,
        TimelineModelPath path,
        String relatedIdentifier,
        String message) implements Comparable<TimelineDiagnostic> {

    private static final Comparator<TimelineDiagnostic> ORDERING = Comparator
            .comparing(TimelineDiagnostic::severity)
            .thenComparing(TimelineDiagnostic::code)
            .thenComparing(TimelineDiagnostic::path)
            .thenComparing(d -> d.relatedIdentifier == null ? "" : d.relatedIdentifier);

    public TimelineDiagnostic {
        code = Objects.requireNonNull(code, "code");
        severity = Objects.requireNonNull(severity, "severity");
        path = Objects.requireNonNull(path, "path");
        message = message == null ? code.name() : message;
    }

    public static TimelineDiagnostic error(TimelineDiagnosticCode code, TimelineModelPath path,
            String relatedIdentifier, String message) {
        return new TimelineDiagnostic(code, TimelineDiagnosticSeverity.ERROR, path, relatedIdentifier, message);
    }

    @Override
    public int compareTo(TimelineDiagnostic other) {
        return ORDERING.compare(this, other);
    }
}
