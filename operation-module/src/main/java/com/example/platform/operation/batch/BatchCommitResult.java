package com.example.platform.operation.batch;

import java.util.List;

/**
 * Result of one batch apply. {@code status} is {@code APPLIED} or {@code NO_OP};
 * {@code replayed} distinguishes an idempotent replay of a previously completed
 * command from a first-time commit.
 */
@org.springframework.modulith.NamedInterface("batch")
public record BatchCommitResult(
        String status,
        String batchId,
        String batchDigest,
        String baseRevisionId,
        String newRevisionId,
        String newContentHash,
        String parentRevisionId,
        String targetRefId,
        List<BatchStepOutcome> outcomes,
        boolean replayed) {

    public static final String APPLIED = "APPLIED";
    public static final String NO_OP = "NO_OP";

    public BatchCommitResult {
        if (!APPLIED.equals(status) && !NO_OP.equals(status)) {
            throw new IllegalArgumentException("unsupported batch status: " + status);
        }
        if (batchId == null || batchId.isBlank() || batchDigest == null || batchDigest.isBlank()) {
            throw new IllegalArgumentException("batch identity required");
        }
        outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
    }

    public static BatchCommitResult applied(
            String batchId, String batchDigest, String baseRevisionId,
            String newRevisionId, String newContentHash, String parentRevisionId,
            String targetRefId, List<BatchStepOutcome> outcomes, boolean replayed) {
        return new BatchCommitResult(APPLIED, batchId, batchDigest, baseRevisionId,
                newRevisionId, newContentHash, parentRevisionId, targetRefId, outcomes, replayed);
    }

    public static BatchCommitResult noOp(
            String batchId, String batchDigest, String baseRevisionId,
            String newContentHash, String targetRefId,
            List<BatchStepOutcome> outcomes, boolean replayed) {
        return new BatchCommitResult(NO_OP, batchId, batchDigest, baseRevisionId,
                null, newContentHash, baseRevisionId, targetRefId, outcomes, replayed);
    }
}
