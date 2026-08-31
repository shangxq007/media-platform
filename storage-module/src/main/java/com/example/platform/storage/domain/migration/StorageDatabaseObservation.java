package com.example.platform.storage.domain.migration;

import java.time.Instant;
import java.util.Objects;

/** Facts observed from the connected PostgreSQL database without any trust decision. */
public record StorageDatabaseObservation(
        long databaseOid,
        String databaseName,
        String schemaName,
        String endpointDiagnostic,
        Instant observedAt) {

    public StorageDatabaseObservation {
        if (databaseOid <= 0) {
            throw new IllegalArgumentException("databaseOid must be positive");
        }
        requireText(databaseName, "databaseName");
        requireText(schemaName, "schemaName");
        if (endpointDiagnostic != null && endpointDiagnostic.isBlank()) {
            throw new IllegalArgumentException("endpointDiagnostic must be null or non-blank");
        }
        Objects.requireNonNull(observedAt, "observedAt");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
