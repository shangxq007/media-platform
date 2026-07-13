package com.example.platform.ingest.preflight.persistence.contract;

import com.example.platform.ingest.preflight.policy.*;
import java.time.Instant;
import java.util.List;

public record SafePreflightPolicyResultRecordContract(
    String recordId,
    String preflightReportRecordId,
    String tenantId,
    String projectId,
    String rawMediaProductId,
    Instant createdAt,
    Instant expiresAt,
    SafePreflightPersistenceMode mode,
    SafePreflightPersistenceAccessScope accessScope,
    SafePreflightPersistenceLifecycleState lifecycleState,
    PreflightPolicyProfile policyProfile,
    PreflightPolicyMode policyMode,
    PreflightPolicyDecision decision,
    boolean reportOnly,
    boolean failOpen,
    boolean blocking,
    boolean uploadContinues,
    int findingCount,
    int rejectCandidateCount,
    List<SafeFindingRecord> findings,
    List<String> userSafeMessages
) {
    public record SafeFindingRecord(
        String code,
        PreflightPolicySeverity severity,
        String ruleId,
        String messageCode,
        boolean diagnosticOnly,
        boolean blocking
    ) {}
}
