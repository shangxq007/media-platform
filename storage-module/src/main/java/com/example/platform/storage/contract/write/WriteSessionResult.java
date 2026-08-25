package com.example.platform.storage.contract.write;

import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageReplicaId;
import java.util.Objects;

/** Authoritative identity of a successfully completed durable write. */
public record WriteSessionResult(
        StorageObjectId objectId,
        StorageReplicaId replicaId,
        boolean alreadyCommitted,
        String idempotencyKey) {

    public WriteSessionResult {
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(replicaId, "replicaId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
    }
}
