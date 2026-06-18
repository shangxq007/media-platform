package com.example.platform.shared.capability;

import java.time.Duration;
import java.util.Set;

/**
 * Represents a stable capability contract that providers can implement.
 *
 * <p>ExtensionPoint defines the interface for extensible platform capabilities.
 * Providers implement ExtensionPoints to add functionality.</p>
 *
 * <p><strong>Contract only:</strong> This defines the vocabulary for extension points.
 * Provider runtime is not implemented.</p>
 */
public interface ExtensionPoint {

    /**
     * Unique extension point key (e.g., "ai.transcribe", "media.generate_thumbnail").
     */
    String key();

    /**
     * Version of this extension point (semver).
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
     * Required permissions to use this extension point.
     */
    Set<String> requiredPermissions();

    /**
     * Stability level of this extension point.
     */
    CapabilityStability stability();

    /**
     * Maximum execution timeout for providers.
     */
    Duration timeout();

    /**
     * Allowed provider runtime types.
     */
    Set<ProviderRuntimeType> allowedProviderTypes();

    /**
     * Human-readable description.
     */
    String description();
}
