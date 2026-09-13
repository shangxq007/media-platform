package com.example.platform.storage.app.identity;

import com.example.platform.storage.api.StorageObjectIssuance.IssuanceResult;
import com.example.platform.storage.api.IssuanceIdempotencyKey;
import com.example.platform.storage.api.StorageOwnershipScope;
import java.util.Optional;

/** Storage-owned persistence port for logical objects, placements, and receipts. */
public interface StorageObjectAuthorityRepository {
    java.util.List<IssuanceResult> references(String storageUri,String projectId,int limit);

    Optional<IssuanceResult> findOriginalIssuance(
            StorageOwnershipScope owner, IssuanceIdempotencyKey idempotencyKey);

    Optional<IssuanceResult> findPlacement(StorageOwnershipScope owner,
            com.example.platform.storage.contract.StorageObjectId objectId,
            com.example.platform.storage.contract.StorageReplicaId replicaId);

    void saveInitialPlacementAndReceipt(IssuanceResult result);
}
