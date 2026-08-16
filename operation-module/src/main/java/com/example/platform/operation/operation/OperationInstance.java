package com.example.platform.operation.operation;

import com.example.platform.operation.operation.OperationDefinitionVersion;

import java.util.Objects;

/**
 * OPERATION_MODEL_FOUNDATION_V1 (OM10/OM11/§12): fully resolved revision-bound
 * semantic invocation. Records exact base revision/hash, resolved typed target,
 * typed parameters, deterministic parameter digest, optional auxiliary
 * invocation id. NEVER Timeline canonical state; never affects Timeline hash.
 */
public record OperationInstance(
        OperationDefinitionId definitionId,
        OperationDefinitionVersion version,
        String baseRevisionId,
        String baseContentHash,
        OperationTarget target,
        OperationParameters parameters,
        String parameterDigest,
        String invocationId) {

    public OperationInstance {
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(parameterDigest, "parameterDigest");
        if (baseRevisionId == null || baseRevisionId.isBlank()) {
            throw new IllegalArgumentException("baseRevisionId required");
        }
    }
}
