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
