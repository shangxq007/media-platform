package com.example.platform.storage.domain.identity;

import com.example.platform.storage.contract.StorageObjectId;
import java.util.UUID;

/** The sole Storage application policy for new logical object identity allocation. */
public final class CanonicalStorageObjectIdAllocator {

    // CANONICAL_STORAGE_OBJECT_ID_ALLOCATOR_AUTHORITY
    public StorageObjectId allocate() {
        return new StorageObjectId("so-" + UUID.randomUUID());
    }
}
