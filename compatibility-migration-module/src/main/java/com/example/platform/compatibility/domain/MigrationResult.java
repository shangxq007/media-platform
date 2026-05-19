package com.example.platform.compatibility.domain;

import java.time.Instant;
import java.util.List;

public record MigrationResult(
        String migrationRunId,
        String migrationPlanId,
        MigrationStatus status,
        SchemaVersion sourceVersion,
        SchemaVersion targetVersion,
        Instant startedAt,
        Instant completedAt,
        VersionedPayload migratedPayload,
        List<MigrationError> errors,
        List<String> warnings
) {}
