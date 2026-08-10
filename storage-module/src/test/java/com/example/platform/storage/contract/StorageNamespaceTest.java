package com.example.platform.storage.contract;
import com.example.platform.storage.contract.namespace.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorageNamespaceTest {
    @Test void validNamespace() {
        StorageNamespace ns = new StorageNamespace("tenant-1", "proj-1", NamespaceClass.SOURCE, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        assertEquals("tenant-1", ns.tenantId());
        assertEquals(NamespaceClass.SOURCE, ns.namespaceClass());
    }
    @Test void blankTenantId_rejected() {
        assertThrows(IllegalArgumentException.class, () -> new StorageNamespace("", "p1", NamespaceClass.SOURCE, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL));
    }
}
