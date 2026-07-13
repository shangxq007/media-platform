package com.example.platform.ingest.preflight.persistence.retention;

import java.time.Instant;
import java.util.List;

public record SafePreflightReportRetentionDryRunResponse(
    String tenantId,
    String projectId,
    String mode,
    String accessScope,
    SafePreflightReportRetentionDryRunStrategy strategy,
    Instant now,
    int retentionDaysMax,
    int batchLimit,
    int maxBatchLimit,
    long eligibleExpiredCount,
    int wouldProcessCount,
    int wouldDeleteCount,
    int wouldMarkExpiredCount,
    Instant oldestExpiredAt,
    Instant newestExpiredAt,
    boolean safetyChecksPassed,
    List<SafePreflightReportRetentionSafetyCheck> safetyChecks,
    SafePreflightReportRetentionDryRunOutcome outcome,
    List<String> warnings
) {}
