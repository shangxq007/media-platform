package com.example.platform.shared.capability.hook;

import com.example.platform.shared.capability.ArtifactRef;
import com.example.platform.shared.capability.InvocationStatus;

import java.util.List;
import java.util.Map;

/**
 * Result of a hook handler invocation.
 *
 * <p><strong>Contract only:</strong> This defines the hook result shape.
 * Hook runtime is not implemented.</p>
 */
public record HookResult(
    InvocationStatus status,
    HookDecision decision,
    Map<String, Object> output,
    List<ArtifactRef> artifactRefs,
    String errorCode,
    boolean retryable,
    String logsRef
) {
    public HookResult {
        output = output != null ? Map.copyOf(output) : Map.of();
        artifactRefs = artifactRefs != null ? List.copyOf(artifactRefs) : List.of();
    }

    /**
     * Create an ALLOW result.
     */
    public static HookResult allow(Map<String, Object> output) {
        return new HookResult(
            InvocationStatus.SUCCEEDED,
            HookDecision.ALLOW,
            output,
            List.of(),
            null,
            false,
            null
        );
    }

    /**
     * Create a DENY result.
     */
    public static HookResult deny(String errorCode, String logsRef) {
        return new HookResult(
            InvocationStatus.SUCCEEDED,
            HookDecision.DENY,
            Map.of(),
            List.of(),
            errorCode,
            false,
            logsRef
        );
    }

    /**
     * Create a NOOP result.
     */
    public static HookResult noop() {
        return new HookResult(
            InvocationStatus.SUCCEEDED,
            HookDecision.NOOP,
            Map.of(),
            List.of(),
            null,
            false,
            null
        );
    }

    /**
     * Create a failed result.
     */
    public static HookResult failure(String errorCode, boolean retryable, String logsRef) {
        return new HookResult(
            InvocationStatus.FAILED,
            HookDecision.NOOP,
            Map.of(),
            List.of(),
            errorCode,
            retryable,
            logsRef
        );
    }
}
