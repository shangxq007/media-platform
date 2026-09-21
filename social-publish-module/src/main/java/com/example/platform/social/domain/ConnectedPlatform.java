package com.example.platform.social.domain;

import java.time.Instant;

public record ConnectedPlatform(
        String id,
        String tenantId,
        String userId,
        String platformType,
        String platformUserId,
        String platformUsername,
        String status,
        long bindingVersion,
        Instant createdAt,
        Instant updatedAt,
        long credentialRevision,
        Instant credentialExpiresAt
) {
    /** Non-secret validation identity; null expiry means no locally recorded expiry, not provider validity. */
    public record CredentialSnapshot(long revision, Instant expiresAt) {
        public CredentialSnapshot {
            if (revision < 1) throw new IllegalArgumentException("credential revision must be positive");
        }
    }

    public CredentialSnapshot credentialSnapshot() { return new CredentialSnapshot(credentialRevision, credentialExpiresAt); }

    public ConnectedPlatform {
        if (credentialRevision < 1) throw new IllegalArgumentException("credentialRevision must be positive");
        if (bindingVersion < 1) {
            throw new IllegalArgumentException("bindingVersion must be positive");
        }
    }
}
