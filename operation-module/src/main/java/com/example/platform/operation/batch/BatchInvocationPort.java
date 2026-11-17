package com.example.platform.operation.batch;

/**
 * Entry point shared by every batch consumer (REST, agent, automation). Entries
 * must not implement their own planning/commit path.
 */
@org.springframework.modulith.NamedInterface("batch")
public interface BatchInvocationPort {

    /** Full planning + per-step/final validation; never writes a revision or moves a ref. */
    BatchPreview preview(OperationBatchRequest request, OperationBatchContext context);

    /** Plan, re-verify and commit exactly one revision (or the semantic NO_OP record). */
    BatchCommitResult commit(OperationBatchRequest request, OperationBatchContext context);
}
