package com.example.platform.storage.api;
import java.util.Optional;
/** Canonical persisted receipt lookup; a supplied receipt is never sufficient evidence alone. */
public interface StoragePlacementQuery {
    /** Bounded reverse-reference lookup for existing administrator/delete-check callers.
     * Resolves persisted ownership; it never treats a URI as logical identity. */
    java.util.List<StorageObjectIssuance.IssuanceResult> references(String storageUri,String projectId,int limit);
    Optional<StorageObjectIssuance.IssuanceResult> find(StorageOwnershipScope owner, IssuanceIdempotencyKey key);
    byte[] read(StorageOwnershipScope owner, IssuanceIdempotencyKey key);
    /** Persisted content metadata for the exact currently available placement. */
    com.example.platform.storage.contract.StorageReference reference(StorageOwnershipScope owner,
            com.example.platform.storage.contract.StorageObjectId objectId,com.example.platform.storage.contract.StorageReplicaId replicaId);
    byte[] read(StorageOwnershipScope owner, com.example.platform.storage.contract.StorageObjectId objectId,
                com.example.platform.storage.contract.StorageReplicaId replicaId);
}
