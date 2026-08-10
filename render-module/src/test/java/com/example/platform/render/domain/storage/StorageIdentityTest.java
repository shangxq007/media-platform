package com.example.platform.render.domain.storage;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.render.domain.storage.namespace.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import com.example.platform.render.domain.storage.identity.StorageObjectLocation;

class StorageIdentityTest {
    @Test void storageObjectId_valid() {
        StorageObjectId id = new StorageObjectId("obj-1");
        assertEquals("obj-1", id.value());
    }
    @Test void storageObjectId_rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new StorageObjectId(""));
        assertThrows(IllegalArgumentException.class, () -> new StorageObjectId(null));
    }
    @Test void storageReplicaId_valid() {
        StorageReplicaId id = new StorageReplicaId("rep-1");
        assertEquals("rep-1", id.value());
    }
    @Test void storageProviderId_valid() {
        StorageProviderId id = new StorageProviderId("aws-s3");
        assertEquals("aws-s3", id.value());
    }
    @Test void location_opaqueLocator_isolation() {
        StorageProviderId provider = new StorageProviderId("s3");
        StorageNamespace ns = new StorageNamespace("tenant-1", "proj-1", NamespaceClass.SOURCE, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        StorageObjectLocation loc = new StorageObjectLocation(provider, ns, "bucket/key", null, "us-east-1");
        assertEquals("bucket/key", loc.opaqueLocator());
    }
    @Test void location_rejectsCredentials() {
        StorageProviderId provider = new StorageProviderId("s3");
        StorageNamespace ns = new StorageNamespace("tenant-1", "proj-1", NamespaceClass.SOURCE, RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        assertThrows(IllegalArgumentException.class, () -> new StorageObjectLocation(provider, ns, "https://key:secret@bucket", null, "us-east-1"));
    }
}
