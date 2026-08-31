package com.example.platform.operation.invocation;

import com.example.platform.operation.operation.OperationDefinitionId;
import com.example.platform.operation.operation.OperationDefinitionVersion;

import java.util.Objects;

/**
 * Immutable public outcome of invoking an Operation.
 */
@org.springframework.modulith.NamedInterface("invocation")
public sealed interface OperationInvocationResult permits
        OperationInvocationResult.Applied,
        OperationInvocationResult.NoOp {

    /** A semantic change produced and persisted a distinct new revision. */
    @org.springframework.modulith.NamedInterface("invocation")
    record Applied(
            OperationDefinitionId definitionId,
            OperationDefinitionVersion definitionVersion,
            String planDigest,
            String baseRevisionId,
            String newRevisionId,
            String resultContentHash,
            String invocationId,
            String correlationId) implements OperationInvocationResult {

        public Applied {
            requireCommon(definitionId, definitionVersion, planDigest, baseRevisionId,
                    invocationId, correlationId);
            requireText(newRevisionId, "newRevisionId", 256);
            requireText(resultContentHash, "resultContentHash", 512);
            if (baseRevisionId.equals(newRevisionId)) {
                throw new IllegalArgumentException("applied revision must differ from baseRevisionId");
            }
        }
    }

    /** A valid invocation whose canonical content remained exactly unchanged. */
    @org.springframework.modulith.NamedInterface("invocation")
    record NoOp(
            OperationDefinitionId definitionId,
            OperationDefinitionVersion definitionVersion,
            String planDigest,
            String baseRevisionId,
            String unchangedContentHash,
            String invocationId,
            String correlationId) implements OperationInvocationResult {

        public NoOp {
            requireCommon(definitionId, definitionVersion, planDigest, baseRevisionId,
                    invocationId, correlationId);
            requireText(unchangedContentHash, "unchangedContentHash", 512);
        }
    }

    private static void requireCommon(
            OperationDefinitionId definitionId,
            OperationDefinitionVersion definitionVersion,
            String planDigest,
            String baseRevisionId,
            String invocationId,
            String correlationId) {
        Objects.requireNonNull(definitionId, "definitionId");
        Objects.requireNonNull(definitionVersion, "definitionVersion");
        requireText(planDigest, "planDigest", 512);
        requireText(baseRevisionId, "baseRevisionId", 256);
        requireText(invocationId, "invocationId", 256);
        if (correlationId != null) {
            requireText(correlationId, "correlationId", 256);
        }
    }

    private static void requireText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " required");
        }
        if (value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " exceeds " + maximumLength + " characters");
        }
    }
}
