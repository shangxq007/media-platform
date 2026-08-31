package com.example.platform.storage.app.identity;

import com.example.platform.storage.api.IssuanceIdempotencyKey;
import com.example.platform.storage.api.StorageOwnershipScope;
import com.example.platform.storage.domain.identity.StorageWriteIntent;
import java.time.Instant;
import java.util.Optional;

/** Storage persistence port for durable write intents and their preallocated logical object. */
public interface StorageWriteIntentRepository {

    void lockOwnerKey(StorageOwnershipScope owner, IssuanceIdempotencyKey key);

    void lockWriteIntent(String writeIntentId);

    Optional<StorageWriteIntent> findByOwnerKey(
            StorageOwnershipScope owner, IssuanceIdempotencyKey key);

    Optional<StorageWriteIntent> findById(String writeIntentId);

    void create(StorageWriteIntent intent);

    StorageWriteIntent recordProviderCompleted(
            String writeIntentId, String providerCorrelationId,
            String providerCompletionFingerprint, Instant updatedAt);

    StorageWriteIntent recordCanonicalCommitted(String writeIntentId, Instant completedAt);
}
