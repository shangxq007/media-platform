package com.example.platform.storage.contract;
import com.example.platform.storage.contract.provider.*;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ProviderCapabilitiesTest {
    @Test void supportsCapability() {
        StorageProviderId id = new StorageProviderId("s3");
        var caps = new StorageProviderCapabilities(id, Map.of(ProviderCapability.RANGE_READ, CapabilitySupport.SUPPORTED));
        assertTrue(caps.supports(ProviderCapability.RANGE_READ));
        assertFalse(caps.supports(ProviderCapability.RENAME));
    }
    @Test void emulatedCapabilities() {
        StorageProviderId id = new StorageProviderId("mem");
        var caps = new StorageProviderCapabilities(id, Map.of(ProviderCapability.RENAME, CapabilitySupport.EMULATED));
        assertFalse(caps.supports(ProviderCapability.RENAME));
        assertTrue(caps.supportsOrEmulated(ProviderCapability.RENAME));
    }
}
