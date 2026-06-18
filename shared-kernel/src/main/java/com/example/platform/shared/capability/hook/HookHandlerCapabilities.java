package com.example.platform.shared.capability.hook;

import java.util.Set;

/**
 * Represents the capabilities of a hook handler.
 *
 * <p><strong>Contract only:</strong> This defines the handler capability shape.
 * Hook runtime is not implemented.</p>
 */
public record HookHandlerCapabilities(
    Set<HookPhase> supportedPhases,
    boolean requiresAuthentication,
    boolean supportsArtifactInput,
    boolean supportsArtifactOutput,
    long maxInputSizeBytes,
    long timeoutMillis
) {
    public HookHandlerCapabilities {
        supportedPhases = supportedPhases != null ? Set.copyOf(supportedPhases) : Set.of();
    }
}
