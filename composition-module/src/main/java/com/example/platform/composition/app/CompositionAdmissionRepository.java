package com.example.platform.composition.app;

import com.example.platform.execution.planning.PlatformExecutionPlan;
import java.util.Optional;

public interface CompositionAdmissionRepository {
    AdmissionRecord admit(PlatformExecutionPlan plan, String compositionId, long compositionRevision);
    Optional<AdmissionRecord> find(String tenantId, String workspaceId, String idempotencyKey);
    Optional<AdmissionRecord> findByExecutionId(String executionId);
    boolean claimQuotaCharge(String executionId);
    void releaseQuotaCharge(String executionId);
    boolean transition(String executionId, long expectedGeneration, String fromState, String toState);

    record AdmissionRecord(String executionId, String tenantId, String workspaceId, String actorId,
            String compositionId, long compositionRevision, String planId, String idempotencyKey,
            String requestHash, long ownershipGeneration, String state, boolean quotaCharged) {}
}
