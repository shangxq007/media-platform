package com.example.platform.execution.admission;

import com.example.platform.execution.planning.PlatformExecutionPlan;
import com.example.platform.execution.result.PlatformCompletionReference;
import java.util.Optional;

/** Domain-neutral admission/lifecycle port; implementations must use the canonical lifecycle tables. */
public interface PlatformExecutionAdmissionPort {
    AdmissionDecision admit(PlatformExecutionPlan plan);
    default AdmissionDecision admit(ProviderBoundExecutionPlan plan) {
        throw new UnsupportedOperationException("provider-bound admission is not implemented");
    }
    boolean cancel(String executionId, long ownershipGeneration);
    boolean retry(String executionId, long ownershipGeneration);
    Optional<PlatformCompletionReference> completed(String tenantId, String idempotencyKey, String requestHash);

    /** Artifact-sourced capabilities use the same canonical admission/result persistence. */
    interface ArtifactCapabilities {
        CapabilityExecution admit(ArtifactCapabilityExecutionPlan plan);
        Optional<CapabilityExecution> find(String tenantId, String projectId, String executionId);
        CapabilityExecution begin(String tenantId, String projectId, String executionId, long generation);
        CapabilityExecution cancel(String tenantId, String projectId, String executionId, long generation);
        CapabilityExecution retry(String tenantId, String projectId, String executionId, long generation);
    }
    record CapabilityExecution(String executionId, String tenantId, String projectId,
            long generation, PlatformExecutionState state, String artifactId, String failureCode,
            boolean retryBlocked, boolean newlyAdmitted, ArtifactCapabilityExecutionPlan plan) {
        public CapabilityHandle handle() { return new CapabilityHandle(executionId,generation,state,artifactId,failureCode,retryBlocked); }
    }
    /** Public provider-neutral execution handle; immutable plan/worker details stay internal. */
    record CapabilityHandle(String executionId,long generation,PlatformExecutionState state,String artifactId,
            String failureCode,boolean retryBlocked) {}

    record AdmissionDecision(String executionId, long ownershipGeneration, boolean newlyAdmitted,
            String state, boolean quotaCharged, ProviderBoundExecutionPlan plan) {
        public AdmissionDecision(String executionId, long ownershipGeneration, boolean newlyAdmitted) {
            this(executionId, ownershipGeneration, newlyAdmitted, "ADMITTED", false, null);
        }
        public AdmissionDecision {
            if (executionId == null || executionId.isBlank()) throw new IllegalArgumentException("executionId required");
            if (ownershipGeneration < 0) throw new IllegalArgumentException("ownershipGeneration must be non-negative");
            if (state == null || state.isBlank()) throw new IllegalArgumentException("state required");
        }
    }
}
