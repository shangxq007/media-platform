package com.example.platform.render.domain.storage;
import com.example.platform.render.domain.storage.lease.*;
import com.example.platform.render.domain.storage.identity.*;
import com.example.platform.render.domain.storage.namespace.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class StorageLeaseTest {
    @Test void lease_expired() {
        StorageObjectId obj = new StorageObjectId("obj-1");
        StorageNamespace ns = new StorageNamespace("t1", "p1", NamespaceClass.TEMPORARY, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        StorageLease lease = new StorageLease("lease-1", obj, ns, Instant.parse("2024-01-01T00:00:00Z"), "owner-1", "test");
        assertTrue(lease.isExpired(Instant.parse("2025-01-01T00:00:00Z")));
    }
    @Test void lease_notExpired() {
        StorageObjectId obj = new StorageObjectId("obj-1");
        StorageNamespace ns = new StorageNamespace("t1", "p1", NamespaceClass.TEMPORARY, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        StorageLease lease = new StorageLease("lease-1", obj, ns, Instant.parse("2025-01-01T00:00:00Z"), "owner-1", "test");
        assertFalse(lease.isExpired(Instant.parse("2024-01-01T00:00:00Z")));
    }
    @Test void lease_usesExplicitEvaluationTime() {
        StorageObjectId obj = new StorageObjectId("obj-1");
        StorageNamespace ns = new StorageNamespace("t1", "p1", NamespaceClass.TEMPORARY, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        StorageLease lease = new StorageLease("lease-1", obj, ns, Instant.parse("2025-01-01T00:00:00Z"), "owner-1", "test");
        // Exactly at expiresAt = expired
        assertTrue(lease.isExpired(Instant.parse("2025-01-01T00:00:00Z")));
    }
}
