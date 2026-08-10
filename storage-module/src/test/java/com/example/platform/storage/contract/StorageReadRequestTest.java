package com.example.platform.storage.contract;
import com.example.platform.storage.contract.read.*;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class StorageReadRequestTest {
    @Test void validReadRequest() {
        StorageObjectId id = new StorageObjectId("obj-1");
        StorageReadRequest req = new StorageReadRequest(id, Optional.of(new ByteRange(0, 100)), IntegrityRequirement.VERIFY_LENGTH);
        assertEquals("obj-1", req.objectId().value());
    }
    @Test void byteRange_validRange() {
        ByteRange range = new ByteRange(0, 100);
        assertEquals(0, range.startInclusive());
        assertEquals(100, range.endInclusive());
    }
    @Test void byteRange_invalidRange() {
        assertThrows(IllegalArgumentException.class, () -> new ByteRange(100, 50));
        assertThrows(IllegalArgumentException.class, () -> new ByteRange(-1, 100));
    }
}
