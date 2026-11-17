package com.example.platform.operation.batch;

import java.util.List;

/**
 * Immutable, fully validated plan for one batch on one exact base.
 *
 * <p>{@code finalCandidate} is the domain's canonical state; the generic layer
 * treats it as opaque and never inspects it. Intermediate candidates never leave
 * the planning call and are never written to a revision store.
 */
@org.springframework.modulith.NamedInterface("batch")
public record OperationBatchPlan<S>(
        String formatVersion,
        String batchId,
        VersionedAggregateRef target,
        String baseRevisionId,
        String baseContentHash,
        List<BatchStepDescriptor> steps,
        S finalCandidate,
        String finalCandidateHash,
        String batchDigest,
        boolean noOp,
        List<BatchStepOutcome> outcomes) {

    public static final String FORMAT_VERSION = "operation-batch-format-v1";

    public OperationBatchPlan {
        if (formatVersion == null || formatVersion.isBlank()) {
            throw new IllegalArgumentException("formatVersion required");
        }
        if (batchId == null || batchId.isBlank()) {
            throw new IllegalArgumentException("batchId required");
        }
        if (target == null || baseRevisionId == null || baseContentHash == null
                || finalCandidate == null || finalCandidateHash == null || batchDigest == null) {
            throw new IllegalArgumentException("complete batch plan required");
        }
        steps = List.copyOf(steps);
        outcomes = List.copyOf(outcomes);
    }
}
