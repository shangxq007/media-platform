package com.example.platform.compatibility.domain;

public record MigrationAuditContext(
        String migrationRunId,
        String tenantId,
        String userId,
        String sourceObjectRef,
        boolean dryRun,
        String triggeredBy
) {}
