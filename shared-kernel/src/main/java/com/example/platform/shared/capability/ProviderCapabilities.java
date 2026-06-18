package com.example.platform.shared.capability;

import java.util.Set;

/**
 * Represents the capabilities of an extension provider.
 *
 * <p><strong>Contract only:</strong> This defines provider capability shape.
 * No runtime capability checking is implemented.</p>
 */
public record ProviderCapabilities(
    Set<String> supportedModalities,
    Set<String> supportedExtensionPoints,
    long maxInputSizeBytes,
    long timeoutMillis,
    boolean streamingSupport,
    boolean costReportingSupport,
    boolean artifactInputSupport,
    boolean artifactOutputSupport
) {
    public ProviderCapabilities {
        supportedModalities = Set.copyOf(supportedModalities);
        supportedExtensionPoints = Set.copyOf(supportedExtensionPoints);
    }
}
