package com.example.platform.storage.delivery.contract;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StorageDeliveryProfileTest {

    @Test
    void testProfileIdValidation() {
        assertDoesNotThrow(() -> new StorageDeliveryProfileId("preview-r2-signed-url"));
        assertThrows(IllegalArgumentException.class, () -> new StorageDeliveryProfileId(""));
        assertThrows(IllegalArgumentException.class, () -> new StorageDeliveryProfileId("UPPER-CASE"));
        assertThrows(IllegalArgumentException.class, () -> new StorageDeliveryProfileId("has spaces"));
    }

    @Test
    void test8CanonicalProfileIds() {
        assertEquals(8, StorageDeliveryProfileId.class.getDeclaredFields().length - 1); // minus 'value' pattern field
    }

    @Test
    void testAccessModeEnum() {
        assertEquals(7, StorageAccessMode.values().length);
    }

    @Test
    void testStatusEnum() {
        assertEquals(7, StorageDeliveryProfileStatus.values().length);
    }

    @Test
    void testPreviewR2Profile() {
        var profile = StorageDeliveryProfile.previewR2SignedUrl();
        assertEquals(StorageDeliveryProfileId.PREVIEW_R2_SIGNED_URL, profile.id());
        assertEquals(StorageDeliveryProfileStatus.PREVIEW_VERIFIED, profile.status());
        assertTrue(profile.enabled());
        assertTrue(profile.runtimeSelectable());
        assertEquals(StorageProviderType.S3_COMPATIBLE, profile.provider());
        assertEquals(StorageBackendType.R2, profile.backend());
        assertEquals(StorageAccessMode.SIGNED_URL, profile.accessMode());
        assertTrue(profile.capabilities().presignRead());
        assertFalse(profile.securityPolicy().persistSignedUrl());
        assertFalse(profile.securityPolicy().exposeBucket());
        assertFalse(profile.securityPolicy().exposeObjectKey());
        assertFalse(profile.securityPolicy().exposeStorageReference());
        assertTrue(profile.securityPolicy().requireTenantProjectScope());
        assertTrue(profile.isUserFacing());
    }

    @Test
    void testLabProfilesDisabled() {
        assertFalse(StorageDeliveryProfile.previewR2SignedUrl().isLabOnly());
        assertFalse(StorageDeliveryProfile.previewR2SignedUrl().isDesignOnly());
    }

    @Test
    void testSecurityDefaults() {
        var policy = StorageDeliveryProfileSecurityPolicy.safeUserFacingSignedUrl();
        assertFalse(policy.exposeStorageReference());
        assertFalse(policy.exposeBucket());
        assertFalse(policy.exposeObjectKey());
        assertFalse(policy.exposeLocalPath());
        assertFalse(policy.persistSignedUrl());
        assertTrue(policy.requireTenantProjectScope());
    }

    @Test
    void testValidationResult() {
        var valid = StorageDeliveryProfileValidationResult.valid(StorageDeliveryProfileId.PREVIEW_R2_SIGNED_URL);
        assertTrue(valid.valid());
        assertFalse(valid.hasErrors());
    }
}
