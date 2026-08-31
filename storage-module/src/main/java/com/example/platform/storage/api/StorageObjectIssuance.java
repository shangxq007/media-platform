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
 * <p>A backend completion is typed placement mechanics only. This boundary delegates logical
 * identity allocation to the durable write-intent recovery authority, then transactionally
 * records the initial placement and immutable receipt. It never implies provider/database
 * atomicity and is intentionally not wired into current production writers.
 */
public interface StorageObjectIssuance {

    // CANONICAL_STORAGE_OBJECT_ISSUANCE_AUTHORITY
    IssuanceResult issue(IssuanceCommand command);

    record IssuanceCommand(
            StorageOwnershipScope owner,
            IssuanceIdempotencyKey idempotencyKey,
            String semanticFingerprint,
            BackendPlacementResult backendPlacement) {

        public IssuanceCommand {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
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
            IssuanceIdempotencyKey idempotencyKey,
            String semanticFingerprint,
            ReceiptPurpose purpose,
            StorageObjectId objectId,
            StorageReplicaId replicaId,
            StorageObjectLocation location,
            ReplicaState state,
            ContentDigest committedDigest,
            long committedLength,
            String providerCorrelationId,
            Instant issuedAt) {

        public PlacementReceipt {
            requireText(receiptId, "receiptId");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
            requireSha256(semanticFingerprint, "semanticFingerprint");
            Objects.requireNonNull(purpose, "purpose");
            Objects.requireNonNull(objectId, "objectId");
            Objects.requireNonNull(replicaId, "replicaId");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(committedDigest, "committedDigest");
            if (committedLength < 0) {
                throw new IllegalArgumentException("committedLength must be non-negative");
            }
            requireText(providerCorrelationId, "providerCorrelationId");
            Objects.requireNonNull(issuedAt, "issuedAt");
        }
    }

    record IssuanceResult(
            StorageOwnershipScope owner,
            StorageObjectId objectId,
            BackendPlacementResult placement,
            PlacementReceipt receipt) {

        public IssuanceResult {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(objectId, "objectId");
            Objects.requireNonNull(placement, "placement");
            Objects.requireNonNull(receipt, "receipt");
            if (!objectId.equals(receipt.objectId())
                    || !placement.replicaId().equals(receipt.replicaId())
                    || !placement.location().equals(receipt.location())
                    || placement.state() != receipt.state()
                    || !placement.committedDigest().equals(receipt.committedDigest())
                    || placement.committedLength() != receipt.committedLength()
                    || !placement.providerCorrelationId().equals(receipt.providerCorrelationId())) {
                throw new IllegalArgumentException("receipt must describe the issued placement exactly");
            }
        }
    }

    enum ReceiptPurpose {
        ORIGINAL_ISSUANCE,
        ADDITIONAL_PLACEMENT
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
