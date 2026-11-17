package com.example.platform.operation.batch;

import com.example.platform.operation.operation.OperationRequest;

import java.util.List;
import java.util.Objects;

/**
 * Ordered, single-base batch intent: N operations that must commit as one
 * versioned change.
 *
 * <p>Every operation carries the same exact base revision/hash; mixed bases are
 * rejected here (before any planning or write). Order is semantic and is part of
 * the batch digest and fingerprint. {@code batchId} is the durable idempotency
 * identity ({@code apply_command_id}) of the whole batch.
 */
@org.springframework.modulith.NamedInterface("batch")
public record OperationBatchRequest(
        String batchId,
        VersionedAggregateRef target,
        String expectedHeadRevisionId,
        List<OperationRequest> operations,
        String message) {

    public static final int MAX_STEPS = 200;
    public static final int MAX_MESSAGE_LENGTH = 512;

    public OperationBatchRequest {
        if (batchId == null || batchId.isBlank() || batchId.length() > 64
                || !batchId.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("batchId must be a bounded identifier token");
        }
        Objects.requireNonNull(target, "target");
        if (expectedHeadRevisionId == null || expectedHeadRevisionId.isBlank()
                || expectedHeadRevisionId.length() > 64) {
            throw new IllegalArgumentException("expectedHeadRevisionId required");
        }
        if (operations == null || operations.isEmpty()) {
            throw new BatchException(BatchErrorCode.EMPTY_BATCH, null,
                    "batch must contain at least one operation");
        }
        if (operations.size() > MAX_STEPS) {
            throw new IllegalArgumentException("batch exceeds " + MAX_STEPS + " operations");
        }
        for (OperationRequest operation : operations) {
            if (operation == null) {
                throw new IllegalArgumentException("batch operations must not contain null");
            }
        }
        if (message != null && message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message exceeds " + MAX_MESSAGE_LENGTH + " characters");
        }
        String baseHash = operations.get(0).baseContentHash();
        for (int index = 0; index < operations.size(); index++) {
            OperationRequest operation = operations.get(index);
            if (!expectedHeadRevisionId.equals(operation.baseRevisionId())) {
                throw new BatchException(BatchErrorCode.MIXED_BASE, index,
                        "operation base revision differs from the batch base revision");
            }
            if (!baseHash.equals(operation.baseContentHash())) {
                throw new BatchException(BatchErrorCode.MIXED_BASE, index,
                        "operation base content hash differs from the batch base content hash");
            }
        }
        operations = List.copyOf(operations);
    }

    /** Exact immutable base content hash shared by every operation. */
    public String baseContentHash() {
        return operations.get(0).baseContentHash();
    }

    public String baseRevisionId() {
        return expectedHeadRevisionId;
    }
}
