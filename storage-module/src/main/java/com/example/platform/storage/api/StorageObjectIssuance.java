package com.example.platform.storage.api;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.contract.identity.StorageObjectLocation;
import com.example.platform.storage.contract.replica.ReplicaState;
import java.time.Instant;
import java.util.Objects;

/**
 * Storage-owned application boundary for canonical logical object issuance.
 *
 * <p>A backend completion is typed placement mechanics only. This boundary allocates the
 * logical {@link StorageObjectId} and atomically records the object, its initial placement,
 * and an immutable receipt. It is intentionally not wired into current production writers.
 */
public interface StorageObjectIssuance {

    IssuanceResult issue(IssuanceCommand command);

    record IssuanceCommand(
            String idempotencyKey,
            String semanticFingerprint,
            BackendPlacementResult backendPlacement) {

        public IssuanceCommand {
            requireText(idempotencyKey, "idempotencyKey");
            requireSha256(semanticFingerprint, "semanticFingerprint");
            Objects.requireNonNull(backendPlacement, "backendPlacement");
        }
    }

    /** Typed provider result; it contains no logical object identity authority. */
    record BackendPlacementResult(
            StorageReplicaId replicaId,
            StorageObjectLocation location,
            ReplicaState state,
            ContentDigest committedDigest,
            long committedLength,
            String providerCorrelationId) {

        public BackendPlacementResult {
            Objects.requireNonNull(replicaId, "replicaId");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(committedDigest, "committedDigest");
            if (committedLength < 0) {
                throw new IllegalArgumentException("committedLength must be non-negative");
            }
            requireText(providerCorrelationId, "providerCorrelationId");
        }
    }

    record PlacementReceipt(
            String receiptId,
            String idempotencyKey,
            String semanticFingerprint,
            StorageObjectId objectId,
            StorageReplicaId replicaId,
            StorageObjectLocation location,
            ContentDigest committedDigest,
            long committedLength,
            String providerCorrelationId,
            Instant issuedAt) {

        public PlacementReceipt {
            requireText(receiptId, "receiptId");
            requireText(idempotencyKey, "idempotencyKey");
            requireSha256(semanticFingerprint, "semanticFingerprint");
            Objects.requireNonNull(objectId, "objectId");
            Objects.requireNonNull(replicaId, "replicaId");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(committedDigest, "committedDigest");
            if (committedLength < 0) {
                throw new IllegalArgumentException("committedLength must be non-negative");
            }
            requireText(providerCorrelationId, "providerCorrelationId");
            Objects.requireNonNull(issuedAt, "issuedAt");
        }
    }

    record IssuanceResult(
            StorageObjectId objectId,
            BackendPlacementResult placement,
            PlacementReceipt receipt) {

        public IssuanceResult {
            Objects.requireNonNull(objectId, "objectId");
            Objects.requireNonNull(placement, "placement");
            Objects.requireNonNull(receipt, "receipt");
            if (!objectId.equals(receipt.objectId())
                    || !placement.replicaId().equals(receipt.replicaId())
                    || !placement.location().equals(receipt.location())
                    || !placement.committedDigest().equals(receipt.committedDigest())
                    || placement.committedLength() != receipt.committedLength()
                    || !placement.providerCorrelationId().equals(receipt.providerCorrelationId())) {
                throw new IllegalArgumentException("receipt must describe the issued placement exactly");
            }
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requireSha256(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be 64 lowercase hex characters");
        }
    }
}
