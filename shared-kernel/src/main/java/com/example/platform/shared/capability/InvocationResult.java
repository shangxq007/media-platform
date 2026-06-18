package com.example.platform.shared.capability;

import java.util.List;
import java.util.Map;

/**
 * Result of invoking a system action or extension provider.
 *
 * <p><strong>Contract only:</strong> This defines the invocation result shape.
 * No runtime result processing is implemented.</p>
 */
public record InvocationResult(
    InvocationStatus status,
    Map<String, Object> output,
    List<ArtifactRef> artifactRefs,
    String logsRef,
    Map<String, Object> metrics,
    String errorCode,
    boolean retryable,
    String errorMessage
) {
    /**
     * Create a successful result.
     */
    public static InvocationResult success(Map<String, Object> output) {
        return new InvocationResult(
            InvocationStatus.SUCCEEDED,
            output,
            List.of(),
            null,
            Map.of(),
            null,
            false,
            null
        );
    }

    /**
     * Create a failed result.
     */
    public static InvocationResult failure(String errorCode, String errorMessage) {
        return new InvocationResult(
            InvocationStatus.FAILED,
            Map.of(),
            List.of(),
            null,
            Map.of(),
            errorCode,
            false,
            errorMessage
        );
    }

    /**
     * Create a retryable failed result.
     */
    public static InvocationResult retryableFailure(String errorCode, String errorMessage) {
        return new InvocationResult(
            InvocationStatus.RETRYABLE_FAILED,
            Map.of(),
            List.of(),
            null,
            Map.of(),
            errorCode,
            true,
            errorMessage
        );
    }
}
