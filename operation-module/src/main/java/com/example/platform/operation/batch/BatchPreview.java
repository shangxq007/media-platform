package com.example.platform.operation.batch;

import java.util.List;

/**
 * Side-effect-free preview of a fully planned and validated batch: formal
 * candidate hashes and ordered step results, no revision and no ref change.
 */
@org.springframework.modulith.NamedInterface("batch")
public record BatchPreview(
        String status,
        String batchId,
        String batchDigest,
        String baseRevisionId,
        String baseContentHash,
        String finalCandidateHash,
        boolean noOp,
        List<BatchStepDescriptor> steps,
        List<BatchStepOutcome> outcomes) {

    public static final String PLANNED = "PLANNED";

    public BatchPreview {
        if (!PLANNED.equals(status)) {
            throw new IllegalArgumentException("unsupported preview status: " + status);
        }
        steps = List.copyOf(steps);
        outcomes = List.copyOf(outcomes);
    }

    public static BatchPreview of(OperationBatchPlan<?> plan) {
        return new BatchPreview(PLANNED, plan.batchId(), plan.batchDigest(),
                plan.baseRevisionId(), plan.baseContentHash(), plan.finalCandidateHash(),
                plan.noOp(), plan.steps(), plan.outcomes());
    }
}
