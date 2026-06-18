package com.example.platform.shared.capability.execution;

import java.util.Map;

/**
 * Request for system action execution.
 *
 * <p><strong>Contract only:</strong> This defines the execution request shape.
 * Runtime execution is not implemented.</p>
 */
public record SystemActionExecutionRequest(
    String actionKey,
    String actionVersion,
    Map<String, Object> input,
    Map<String, Object> options,
    String idempotencyKey
) {
    public SystemActionExecutionRequest {
        if (actionKey == null || actionKey.isBlank()) {
            throw new IllegalArgumentException("actionKey must not be blank");
        }
        input = input != null ? Map.copyOf(input) : Map.of();
        options = options != null ? Map.copyOf(options) : Map.of();
    }
}
