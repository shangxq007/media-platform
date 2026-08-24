package com.example.platform.workerfabric.domain;

/**
 * Maps farm success into completion evidence which must still pass the common completion fence.
 *
 * <p>This contract cannot issue Artifact commit evidence or complete a task directly.
 */
@FunctionalInterface
public interface OpenCueCompletionMappingContract<S> {

    CompletionEvidence normalizeBackendSuccess(S openCueStatusEnvelope);
}
