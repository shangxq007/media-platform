package com.example.platform.storage.app.identity;

/** Fail-closed signal for reuse of an issuance key with different semantic input. */
public final class StorageIssuanceConflictException extends IllegalStateException {

    public StorageIssuanceConflictException(String message) {
        super(message);
    }
}
