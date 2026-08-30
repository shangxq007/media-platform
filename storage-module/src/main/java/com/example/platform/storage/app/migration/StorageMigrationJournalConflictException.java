package com.example.platform.storage.app.migration;

/** Fail-closed signal for reuse of a migration key with a different fingerprint. */
public final class StorageMigrationJournalConflictException extends IllegalStateException {

    public StorageMigrationJournalConflictException(String message) {
        super(message);
    }
}
