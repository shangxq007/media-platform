package com.example.platform.storage.contract;
import java.io.Serializable;
public record StorageProviderId(String value) implements Serializable {
    public StorageProviderId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("StorageProviderId must not be blank");
    }
    @Override public String toString() { return value; }
}
