package com.example.platform.storage.api;

import com.example.platform.storage.api.StorageObjectIssuance.BackendPlacementResult;
import com.example.platform.storage.api.StorageObjectIssuance.IssuanceResult;
import com.example.platform.storage.domain.identity.StorageWriteIntent;
import java.util.Objects;

/** Sole recovery authority for durable normal-write intent and canonical completion. */
public interface StorageWriteIntentRecovery {

    // STORAGE_WRITE_INTENT_RECOVERY_AUTHORITY
    StorageWriteIntent beginOrResume(BeginWriteIntentCommand command);

    StorageWriteIntent recordProviderCompleted(
            String writeIntentId, BackendPlacementResult backendPlacement);

    IssuanceResult complete(CompleteWriteIntentCommand command);

    record BeginWriteIntentCommand(
            StorageOwnershipScope owner,
            IssuanceIdempotencyKey idempotencyKey,
            String semanticFingerprint,
            String providerRequestId) {

        public BeginWriteIntentCommand {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
            if (semanticFingerprint == null
                    || !semanticFingerprint.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException(
                        "semanticFingerprint must be 64 lowercase hex characters");
            }
            if (providerRequestId != null && providerRequestId.isBlank()) {
                throw new IllegalArgumentException(
                        "providerRequestId must be null or non-blank");
            }
        }
    }

    record CompleteWriteIntentCommand(
            String writeIntentId,
            BackendPlacementResult backendPlacement) {

        public CompleteWriteIntentCommand {
            if (writeIntentId == null || writeIntentId.isBlank()) {
                throw new IllegalArgumentException("writeIntentId must not be blank");
            }
            Objects.requireNonNull(backendPlacement, "backendPlacement");
        }
    }
}
