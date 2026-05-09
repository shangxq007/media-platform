package com.example.platform.identity.app;

import java.time.Instant;
import java.util.Objects;

public record ApiKeyRecord(
        String id,
        String tenantId,
        String fingerprint,
        String hashedKey,
        String principal,
        Instant createdAt,
        Instant lastUsedAt,
        Instant revokedAt
) {
    public ApiKeyRecord {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");
        Objects.requireNonNull(hashedKey, "hashedKey must not be null");
        Objects.requireNonNull(principal, "principal must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public ApiKeyRecord withLastUsedAt(Instant lastUsedAt) {
        return new ApiKeyRecord(id, tenantId, fingerprint, hashedKey, principal, createdAt, lastUsedAt, revokedAt);
    }

    public ApiKeyRecord withRevokedAt(Instant revokedAt) {
        return new ApiKeyRecord(id, tenantId, fingerprint, hashedKey, principal, createdAt, lastUsedAt, revokedAt);
    }
}
