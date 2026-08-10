package com.example.platform.storage.contract.lease;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import java.io.Serializable;
import java.time.Instant;
public record StorageLease(String leaseId, StorageObjectId objectId, StorageNamespace namespace, Instant expiresAt, String owner, String purpose) implements Serializable {
    public StorageLease {
        if (leaseId == null || leaseId.isBlank()) throw new IllegalArgumentException("leaseId required");
        if (objectId == null) throw new IllegalArgumentException("objectId required");
        if (namespace == null) throw new IllegalArgumentException("namespace required");
        if (expiresAt == null) throw new IllegalArgumentException("expiresAt required");
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("owner required");
    }
    public boolean isExpired(Instant evaluationTime) {
        if (evaluationTime == null) throw new IllegalArgumentException("evaluationTime required");
        return evaluationTime.isAfter(expiresAt) || evaluationTime.equals(expiresAt);
    }
}
