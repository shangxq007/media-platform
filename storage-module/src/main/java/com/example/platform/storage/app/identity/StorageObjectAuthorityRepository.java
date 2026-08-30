package com.example.platform.storage.app.identity;

import com.example.platform.storage.api.StorageObjectIssuance.IssuanceResult;
import com.example.platform.storage.api.IssuanceIdempotencyKey;
import com.example.platform.storage.api.StorageOwnershipScope;
import java.util.Optional;

/** Storage-owned persistence port for logical objects, placements, and receipts. */
public interface StorageObjectAuthorityRepository {

    Optional<IssuanceResult> findOriginalIssuance(
            StorageOwnershipScope owner, IssuanceIdempotencyKey idempotencyKey);

    void saveInitialPlacementAndReceipt(IssuanceResult result);
}
