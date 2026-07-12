package com.example.platform.storage.delivery.registry;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.storage.delivery.contract.*;
import org.junit.jupiter.api.Test;

class StorageDeliveryProfileRegistryTest {

    @Test
    void testCanonicalRegistryContains8Profiles() {
        var registry = StorageDeliveryProfileRegistry.fromCanonicalCatalog();
        assertEquals(8, registry.profileCount());
    }

    @Test
    void testDefaultProfile() {
        var registry = StorageDeliveryProfileRegistry.fromCanonicalCatalog();
        assertEquals(StorageDeliveryProfileId.PREVIEW_R2_SIGNED_URL, registry.defaultProfileId());
        assertTrue(registry.defaultProfile().isPresent());
        assertEquals(StorageAccessMode.SIGNED_URL, registry.defaultProfile().get().accessMode());
        assertEquals(StorageBackendType.R2, registry.defaultProfile().get().backend());
    }

    @Test
    void testLookupById() {
        var registry = StorageDeliveryProfileRegistry.fromCanonicalCatalog();
        assertTrue(registry.contains(StorageDeliveryProfileId.PREVIEW_R2_SIGNED_URL));
        assertTrue(registry.findById(StorageDeliveryProfileId.PREVIEW_R2_SIGNED_URL).isPresent());
        assertFalse(registry.contains(new StorageDeliveryProfileId("nonexistent-profile")));
    }

    @Test
    void testRegistryImmutable() {
        var registry = StorageDeliveryProfileRegistry.fromCanonicalCatalog();
        var profiles = registry.profiles();
        assertThrows(UnsupportedOperationException.class, () -> profiles.add(null));
    }

    @Test
    void testCanonicalValidation() {
        var registry = StorageDeliveryProfileRegistry.fromCanonicalCatalog();
        var result = registry.validationResult();
        assertNotNull(result);
    }

    @Test
    void testLabProfilesDisabled() {
        var registry = StorageDeliveryProfileRegistry.fromCanonicalCatalog();
        var opendal = registry.findById(StorageDeliveryProfileId.LAB_OPENDAL_FS_INTERNAL);
        assertTrue(opendal.isPresent());
        assertFalse(opendal.get().enabled());
        assertFalse(opendal.get().runtimeSelectable());
    }

    @Test
    void testSnapshot() {
        var registry = StorageDeliveryProfileRegistry.fromCanonicalCatalog();
        var snapshot = registry.snapshot();
        assertEquals(8, snapshot.profileCount());
        assertEquals(StorageDeliveryProfileId.PREVIEW_R2_SIGNED_URL, snapshot.defaultProfileId());
        assertFalse(snapshot.runtimeSelectableProfileIds().isEmpty());
    }
}
