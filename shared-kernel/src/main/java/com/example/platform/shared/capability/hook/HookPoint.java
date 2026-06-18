package com.example.platform.shared.capability.hook;

import com.example.platform.shared.capability.CapabilityStability;

import java.time.Duration;
import java.util.Set;

/**
 * Represents a controlled lifecycle interception point.
 *
 * <p>HookPoint defines where in the lifecycle a hook can intercept.
 * Hooks are lifecycle interception points, not facts (like events).</p>
 *
 * <p><strong>Contract only:</strong> This defines the hook point shape.
 * Hook runtime is not implemented.</p>
 */
public record HookPoint(
    String key,
    HookPhase phase,
    String inputSchemaRef,
    String outputSchemaRef,
    Set<String> requiredPermissions,
    Duration timeout,
    HookFailurePolicy failurePolicy,
    CapabilityStability stability
) {
    public HookPoint {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("HookPoint key must not be blank");
        }
        if (phase == null) {
            throw new IllegalArgumentException("HookPoint phase must not be null");
        }
        if (failurePolicy == null) {
            throw new IllegalArgumentException("HookPoint failurePolicy must not be null");
        }
        requiredPermissions = requiredPermissions != null ? Set.copyOf(requiredPermissions) : Set.of();
    }
}
