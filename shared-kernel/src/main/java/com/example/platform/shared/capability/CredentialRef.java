package com.example.platform.shared.capability;

import java.util.Set;

/**
 * Represents a reference to a credential (secret).
 *
 * <p>CredentialRef is a reference only - it never contains the raw secret value.
 * Secrets are stored in Vault or other secret management systems.</p>
 *
 * <p><strong>Contract only:</strong> This defines the credential reference shape.
 * No secret retrieval is implemented.</p>
 */
public record CredentialRef(
    String tenantId,
    String credentialId,
    String providerId,
    Set<String> scopes,
    String secretPath,
    RotationMetadata rotationMetadata
) {
    public record RotationMetadata(
        java.time.Instant lastRotatedAt,
        java.time.Instant nextRotationAt,
        int rotationIntervalDays
    ) {}
}
