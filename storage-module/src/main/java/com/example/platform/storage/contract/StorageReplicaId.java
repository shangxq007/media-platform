package com.example.platform.storage.contract;
import java.io.Serializable;
public record StorageReplicaId(String value) implements Serializable {
    public StorageReplicaId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("StorageReplicaId must not be blank");
    }
    @Override public String toString() { return value; }
}
