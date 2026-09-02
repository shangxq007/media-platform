package com.example.platform.operation.operation;

import com.example.platform.operation.operation.OperationDefinitionVersion;

import java.util.Objects;

/**
 * OPERATION_MODEL_FOUNDATION_V1 (OM9/OM13/§3): caller-facing typed Operation
 * request — definition + version + variant target request + typed parameters
 * + optional non-semantic metadata. Base binding is explicit (baseRevisionId +
 * baseContentHash) so REQUEST->RESOLVE can never read mutable latest.
 */
@org.springframework.modulith.NamedInterface("invocation")
public record OperationRequest(
        OperationDefinitionId definitionId,
        OperationDefinitionVersion version,
        OperationTargetRequest target,
        OperationParameters parameters,
        String baseRevisionId,
        String baseContentHash,
        String requestMetadata) {

    public OperationRequest {
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(parameters, "parameters");
        if (baseRevisionId == null || baseRevisionId.isBlank()) {
            throw new IllegalArgumentException("baseRevisionId required (no mutable-latest)");
        }
        if (baseContentHash == null || baseContentHash.isBlank()) {
            throw new IllegalArgumentException("baseContentHash required (exact immutable base)");
        }
    }
}
