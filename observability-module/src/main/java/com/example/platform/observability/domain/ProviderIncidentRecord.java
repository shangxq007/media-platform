package com.example.platform.observability.domain;

import java.time.OffsetDateTime;

/**
 * Incident record for a third-party provider.
 */
public record ProviderIncidentRecord(
        String incidentId,
        String providerKey,
        String providerType,
        String severity,
        String title,
        String description,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime resolvedAt) {

    public static final String SEVERITY_WARNING = "WARNING";
    public static final String SEVERITY_MINOR = "MINOR";
    public static final String SEVERITY_MAJOR = "MAJOR";
    public static final String SEVERITY_CRITICAL = "CRITICAL";

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_INVESTIGATING = "INVESTIGATING";
    public static final String STATUS_RESOLVED = "RESOLVED";
}
