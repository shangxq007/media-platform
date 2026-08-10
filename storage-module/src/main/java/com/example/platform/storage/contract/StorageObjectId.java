package com.example.platform.storage.contract;

import java.io.Serializable;
import java.util.Objects;

/**
 * Canonical platform-level storage object identity.
 * Logical identity is independent of physical location, provider, or storage backend.
 */
public record StorageObjectId(String value) implements Serializable {
    public StorageObjectId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("StorageObjectId must not be blank");
    }

    @Override
    public String toString() {
        return value;
    }
}
