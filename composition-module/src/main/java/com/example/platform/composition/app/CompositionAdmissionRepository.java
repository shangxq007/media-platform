package com.example.platform.composition.app;

import com.example.platform.execution.planning.ProviderBoundExecutionPlan;
import java.util.Optional;

public interface CompositionAdmissionRepository {
    AdmissionRecord admit(ProviderBoundExecutionPlan plan);
    Optional<AdmissionRecord> find(String tenantId, String workspaceId, String idempotencyKey);
    Optional<AdmissionRecord> findByExecutionId(String executionId);
    boolean claimQuotaCharge(String executionId);
    void markQuotaCharged(String executionId);
    void releaseQuotaCharge(String executionId);
    void deleteUncharged(String executionId);
    boolean transition(String executionId, long expectedGeneration, String fromState, String toState);

    record AdmissionRecord(String executionId, String tenantId, String workspaceId, String actorId,
            String compositionId, long compositionRevision, String planFingerprint, String idempotencyKey,
            String requestHash, long ownershipGeneration, String state, boolean quotaClaimed, boolean quotaCharged,
            ProviderBoundExecutionPlan plan) {}
}
