package com.example.platform.storage.delivery.contract;

public record StorageDeliveryProfileSecurityPolicy(
    boolean exposeStorageReference, boolean exposeBucket, boolean exposeObjectKey,
    boolean exposeLocalPath, boolean persistSignedUrl, boolean requireTenantProjectScope, boolean userFacing
) {
    public static StorageDeliveryProfileSecurityPolicy safeUserFacingSignedUrl() {
        return new StorageDeliveryProfileSecurityPolicy(false, false, false, false, false, true, true);
    }
    public static StorageDeliveryProfileSecurityPolicy internalOnly() {
        return new StorageDeliveryProfileSecurityPolicy(false, false, false, false, false, false, false);
    }
}
