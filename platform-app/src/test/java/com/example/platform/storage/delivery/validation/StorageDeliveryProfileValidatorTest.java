package com.example.platform.storage.delivery.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.storage.delivery.contract.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class StorageDeliveryProfileValidatorTest {

    private final StorageDeliveryProfileValidator validator = new StorageDeliveryProfileValidator();

    @Test
    void testPreviewR2ProfileValidates() {
        var result = validator.validate(StorageDeliveryProfile.previewR2SignedUrl());
        assertTrue(result.valid(), "Preview R2 profile should be valid");
        assertTrue(result.errors().isEmpty());
    }

    @Test
    void testSignedUrlRequiresPresign() {
        var profile = new StorageDeliveryProfile(
            StorageDeliveryProfileId.PREVIEW_R2_SIGNED_URL,
            StorageDeliveryProfileStatus.PREVIEW_VERIFIED, true, true,
            StorageProviderType.S3_COMPATIBLE, StorageBackendType.R2,
            StorageAccessMode.SIGNED_URL, AccessDescriptorContractType.SIGNED_URL,
            new StorageDeliveryProfileCapabilities(true, true, false, false, false, false, false, false, true),
            StorageDeliveryProfileSecurityPolicy.safeUserFacingSignedUrl()
        );
        var result = validator.validate(profile);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.code().equals("SIGNED_URL_REQUIRES_PRESIGN_READ")));
    }

    @Test
    void testSignedUrlMustNotPersist() {
        var profile = new StorageDeliveryProfile(
            StorageDeliveryProfileId.PREVIEW_R2_SIGNED_URL,
            StorageDeliveryProfileStatus.PREVIEW_VERIFIED, true, true,
            StorageProviderType.S3_COMPATIBLE, StorageBackendType.R2,
            StorageAccessMode.SIGNED_URL, AccessDescriptorContractType.SIGNED_URL,
            StorageDeliveryProfileCapabilities.signedUrlR2Preview(),
            new StorageDeliveryProfileSecurityPolicy(false, false, false, false, true, true, true)
        );
        var result = validator.validate(profile);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.code().equals("SIGNED_URL_MUST_NOT_BE_PERSISTED")));
    }

    @Test
    void testRuntimeSelectableRequiresEnabled() {
        var profile = new StorageDeliveryProfile(
            StorageDeliveryProfileId.PREVIEW_R2_SIGNED_URL,
            StorageDeliveryProfileStatus.PREVIEW_VERIFIED, false, true,
            StorageProviderType.S3_COMPATIBLE, StorageBackendType.R2,
            StorageAccessMode.SIGNED_URL, AccessDescriptorContractType.SIGNED_URL,
            StorageDeliveryProfileCapabilities.signedUrlR2Preview(),
            StorageDeliveryProfileSecurityPolicy.safeUserFacingSignedUrl()
        );
        var result = validator.validate(profile);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.code().equals("RUNTIME_SELECTABLE_REQUIRES_ENABLED")));
    }

    @Test
    void testLabProfilesDisabled() {
        var result = validator.validate(StorageDeliveryProfile.previewR2SignedUrl());
        assertTrue(result.valid());
    }

    @Test
    void testLocalPathNotUserFacing() {
        var profile = new StorageDeliveryProfile(
            new StorageDeliveryProfileId("test-local-path"),
            StorageDeliveryProfileStatus.EXPERIMENTAL, true, false,
            StorageProviderType.LOCAL_FILESYSTEM, StorageBackendType.LOCAL_FS,
            StorageAccessMode.LOCAL_PATH, AccessDescriptorContractType.NOT_USER_FACING,
            StorageDeliveryProfileCapabilities.internalStreamLab(),
            new StorageDeliveryProfileSecurityPolicy(false, false, false, false, false, false, true)
        );
        var result = validator.validate(profile);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(e -> e.code().equals("LOCAL_PATH_NOT_USER_FACING")));
    }

    @Test
    void testCatalogValidation() {
        var profiles = List.of(StorageDeliveryProfile.previewR2SignedUrl());
        var result = validator.validateAll(profiles);
        assertTrue(result.valid());
    }
}
