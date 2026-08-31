package com.example.platform.storage.domain.identity;

import com.example.platform.storage.api.IssuanceIdempotencyKey;
import com.example.platform.storage.api.StorageOwnershipScope;
import com.example.platform.storage.contract.StorageObjectId;
import java.time.Instant;
import java.util.Objects;

/** Durable normal-write state spanning the non-atomic provider and PostgreSQL boundary. */
public record StorageWriteIntent(
        String writeIntentId,
        StorageObjectId objectId,
        StorageOwnershipScope owner,
        IssuanceIdempotencyKey idempotencyKey,
        String semanticFingerprint,
        String providerRequestId,
        String providerCorrelationId,
        String providerCompletionFingerprint,
        State state,
        String lastErrorCode,
        String reconciliationDetail,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {

    public StorageWriteIntent {
        requireText(writeIntentId, "writeIntentId");
        Objects.requireNonNull(objectId, "objectId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        requireSha256(semanticFingerprint);
        requireNullableText(providerRequestId, "providerRequestId");
        requireNullableText(providerCorrelationId, "providerCorrelationId");
        if (providerCompletionFingerprint != null
                && !providerCompletionFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "providerCompletionFingerprint must be null or 64 lowercase hex characters");
        }
        Objects.requireNonNull(state, "state");
        requireNullableText(lastErrorCode, "lastErrorCode");
        requireNullableText(reconciliationDetail, "reconciliationDetail");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
        if ((state == State.CANONICAL_COMMITTED) != (completedAt != null)) {
            throw new IllegalArgumentException(
                    "completedAt is present exactly for CANONICAL_COMMITTED intents");
        }
        if (state == State.PENDING_PROVIDER
                && (providerCorrelationId != null || providerCompletionFingerprint != null)) {
            throw new IllegalArgumentException(
                    "pending provider intent must not carry completion facts");
        }
        if ((state == State.PROVIDER_COMPLETED || state == State.CANONICAL_COMMITTED)
                && (providerCorrelationId == null || providerCompletionFingerprint == null)) {
            throw new IllegalArgumentException(
                    "provider-completed intent requires correlation and completion fingerprint");
        }
    }

    public enum State {
        PENDING_PROVIDER,
        PROVIDER_COMPLETED,
        CANONICAL_COMMITTED,
        RECONCILIATION_REQUIRED,
        FAILED_REVIEW_REQUIRED
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requireNullableText(String value, String field) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(field + " must be null or non-blank");
        }
    }

    private static void requireSha256(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "semanticFingerprint must be 64 lowercase hex characters");
        }
    }
}
