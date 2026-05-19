package com.example.platform.compatibility.domain;

import java.util.List;

public record MigrationPlan(
        String migrationPlanId,
        SchemaFamily schemaFamily,
        SchemaVersion sourceVersion,
        SchemaVersion targetVersion,
        List<MigrationStep> steps,
        boolean reversible,
        String estimatedCost
) {}
