package com.example.platform.storage.app.identity;

import com.example.platform.storage.api.StorageObjectIssuance.IssuanceResult;
import java.util.Optional;

/** Storage-owned persistence port for logical objects, placements, and receipts. */
public interface StorageObjectAuthorityRepository {

    void lockIdempotencyKey(String idempotencyKey);

    Optional<IssuanceResult> findByIdempotencyKey(String idempotencyKey);

    void save(IssuanceResult result);
}
