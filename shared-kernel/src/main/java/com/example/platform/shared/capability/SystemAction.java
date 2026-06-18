package com.example.platform.shared.capability;

import java.time.Duration;
import java.util.Set;

/**
 * Represents a first-class internal platform action that can be called by automation flows.
 *
 * <p>SystemAction is the atomic unit of work in the capability opening model.
 * It defines a named operation with input/output schemas, required permissions,
 * and execution policies.</p>
 *
 * <p><strong>Contract only:</strong> This defines the vocabulary for system actions.
 * Runtime execution is not implemented.</p>
 */
public interface SystemAction {

    /**
     * Unique action key (e.g., "render.create_job", "media.generate_thumbnail").
     */
    String actionKey();

    /**
     * Version of this action (semver).
     */
    String version();

    /**
     * JSON Schema reference for input validation.
     * May return null if not yet defined.
     */
    String inputSchemaRef();

    /**
     * JSON Schema reference for output validation.
     * May return null if not yet defined.
     */
    String outputSchemaRef();

    /**
     * Required permissions to execute this action.
     */
    Set<String> requiredPermissions();

    /**
     * Maximum execution timeout.
     */
    Duration timeout();

    /**
     * Whether this action is idempotent (safe to retry).
     */
    boolean isIdempotent();

    /**
     * Stability level of this action.
     */
    CapabilityStability stability();

    /**
     * Human-readable description.
     */
    String description();
}
