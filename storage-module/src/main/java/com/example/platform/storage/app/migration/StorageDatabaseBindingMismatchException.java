package com.example.platform.storage.app.migration;

/** Fail-closed signal that the connected database or schema is not the declared target. */
public final class StorageDatabaseBindingMismatchException extends IllegalStateException {

    public StorageDatabaseBindingMismatchException(String message) {
        super(message);
    }
}
