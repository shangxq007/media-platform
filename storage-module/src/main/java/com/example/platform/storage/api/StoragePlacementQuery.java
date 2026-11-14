package com.example.platform.storage.api;
import java.util.Optional;
import java.nio.file.Path;
import java.util.function.Function;
/** Canonical persisted receipt lookup; a supplied receipt is never sufficient evidence alone. */
public interface StoragePlacementQuery {
    /** Bounded reverse-reference lookup for existing administrator/delete-check callers.
     * Resolves persisted ownership; it never treats a URI as logical identity. */
    java.util.List<StorageObjectIssuance.IssuanceResult> references(String storageUri,String projectId,int limit);
    Optional<StorageObjectIssuance.IssuanceResult> find(StorageOwnershipScope owner, IssuanceIdempotencyKey key);
    /** Resolve the current, receipt-backed placement using its exact project and replica identity. */
    Optional<StorageObjectIssuance.IssuanceResult> findPlacement(StorageOwnershipScope owner,
            com.example.platform.storage.contract.StorageObjectId objectId,
            com.example.platform.storage.contract.StorageReplicaId replicaId);
    byte[] read(StorageOwnershipScope owner, IssuanceIdempotencyKey key);
    /** Callback receives a verified, short-lived local source file; it must not retain the path. */
    <T> T withVerifiedSource(StorageOwnershipScope owner, IssuanceIdempotencyKey key, Function<Path, T> reader);
    /** Persisted content metadata for the exact currently available placement. */
    com.example.platform.storage.contract.StorageReference reference(StorageOwnershipScope owner,
            com.example.platform.storage.contract.StorageObjectId objectId,com.example.platform.storage.contract.StorageReplicaId replicaId);
    byte[] read(StorageOwnershipScope owner, com.example.platform.storage.contract.StorageObjectId objectId,
                com.example.platform.storage.contract.StorageReplicaId replicaId);
}
