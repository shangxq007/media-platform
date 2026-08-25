package com.example.platform.workerfabric.reuse;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import java.util.Objects;

/** Reconciliation-safe evidence returned only after StorageProvider.completeWrite succeeds. */
public record DurableStoragePublication(
        StorageProviderId providerId,
        StorageObjectId objectId,
        StorageReplicaId replicaId,
        ContentDigest contentDigest,
        long byteLength,
        String idempotencyKey,
        boolean alreadyCommitted) {

    public DurableStoragePublication {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(replicaId, "replicaId");
        Objects.requireNonNull(contentDigest, "contentDigest");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (byteLength < 0 || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("valid length and idempotencyKey are required");
        }
    }
}
