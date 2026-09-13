package com.example.platform.storage.api;
import java.util.Optional;
/** Canonical persisted receipt lookup; a supplied receipt is never sufficient evidence alone. */
public interface StoragePlacementQuery {
    Optional<StorageObjectIssuance.IssuanceResult> find(StorageOwnershipScope owner, IssuanceIdempotencyKey key);
    byte[] read(StorageOwnershipScope owner, IssuanceIdempotencyKey key);
}
