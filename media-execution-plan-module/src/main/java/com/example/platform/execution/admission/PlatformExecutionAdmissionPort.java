package com.example.platform.execution.admission;

import com.example.platform.execution.planning.PlatformExecutionPlan;
import com.example.platform.execution.result.PlatformCompletionReference;
import java.util.Optional;

/** Domain-neutral admission/lifecycle port; implementations must use the canonical lifecycle tables. */
public interface PlatformExecutionAdmissionPort {
    AdmissionDecision admit(PlatformExecutionPlan plan);
    boolean cancel(String executionId, long ownershipGeneration);
    boolean retry(String executionId, long ownershipGeneration);
    Optional<PlatformCompletionReference> completed(String idempotencyKey, String requestHash);

    record AdmissionDecision(String executionId, long ownershipGeneration, boolean newlyAdmitted) {
        public AdmissionDecision {
            if (executionId == null || executionId.isBlank()) throw new IllegalArgumentException("executionId required");
            if (ownershipGeneration < 0) throw new IllegalArgumentException("ownershipGeneration must be non-negative");
        }
    }
}
