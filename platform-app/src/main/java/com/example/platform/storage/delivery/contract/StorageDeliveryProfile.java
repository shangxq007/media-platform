package com.example.platform.storage.delivery.contract;

public record StorageDeliveryProfile(
    StorageDeliveryProfileId id, StorageDeliveryProfileStatus status,
    boolean enabled, boolean runtimeSelectable,
    StorageProviderType provider, StorageBackendType backend,
    StorageAccessMode accessMode, AccessDescriptorContractType accessDescriptorType,
    StorageDeliveryProfileCapabilities capabilities, StorageDeliveryProfileSecurityPolicy securityPolicy
) {
    public static StorageDeliveryProfile previewR2SignedUrl() {
        return new StorageDeliveryProfile(
            StorageDeliveryProfileId.PREVIEW_R2_SIGNED_URL,
            StorageDeliveryProfileStatus.PREVIEW_VERIFIED, true, true,
            StorageProviderType.S3_COMPATIBLE, StorageBackendType.R2,
            StorageAccessMode.SIGNED_URL, AccessDescriptorContractType.SIGNED_URL,
            StorageDeliveryProfileCapabilities.signedUrlR2Preview(),
            StorageDeliveryProfileSecurityPolicy.safeUserFacingSignedUrl()
        );
    }
    public boolean isUserFacing() { return securityPolicy.userFacing(); }
    public boolean isLabOnly() { return status == StorageDeliveryProfileStatus.LAB_ONLY; }
    public boolean isDesignOnly() { return status == StorageDeliveryProfileStatus.DESIGN_ONLY; }
    public boolean supportsSignedUrl() { return capabilities.presignRead(); }
}
