package com.example.platform.ingest.preflight.persistence.read;

import java.time.Instant;

public record SafePreflightReportRecordListItem(
    Long recordId,
    String rawMediaProductId,
    String uploadAttemptId,
    Instant createdAt,
    Instant expiresAt,
    String lifecycleState,
    String persistenceMode,
    String accessScope,
    String overallDecision,
    Integer warningCount,
    Integer findingCount,
    Integer rejectCandidateCount,
    String policyProfile,
    String policyMode,
    String policyDecision,
    Integer policyFindingCount,
    Integer policyRejectCandidateCount,
    Boolean uploadContinues,
    Boolean blocking
) {}
