package com.example.platform.ingest.preflight.persistence.writer;

import com.example.platform.ingest.contract.SafePreflightReportSummary;
import com.example.platform.ingest.preflight.policy.PreflightPolicyEvaluationResult;
import java.time.Instant;

public record SafePreflightPersistenceWriteRequest(
    String tenantId,
    String projectId,
    String rawMediaProductId,
    String uploadAttemptId,
    Instant createdAt,
    SafePreflightReportSummary safeReport,
    PreflightPolicyEvaluationResult policyResult
) {}
