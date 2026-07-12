package com.example.platform.storage.delivery.contract;

public record StorageDeliveryProfileCapabilities(
    boolean writeArtifact, boolean readArtifact, boolean presignRead,
    boolean internalStream, boolean externalBucket, boolean exportBundle,
    boolean deleteArtifact, boolean supportsRangeRead, boolean supportsContentMetadata
) {
    public static StorageDeliveryProfileCapabilities signedUrlR2Preview() {
        return new StorageDeliveryProfileCapabilities(true, true, true, false, false, false, false, false, true);
    }
    public static StorageDeliveryProfileCapabilities internalStreamLab() {
        return new StorageDeliveryProfileCapabilities(true, true, false, true, false, false, false, false, true);
    }
    public static StorageDeliveryProfileCapabilities externalBucketDesignOnly() {
        return new StorageDeliveryProfileCapabilities(false, false, false, false, true, false, false, false, false);
    }
    public static StorageDeliveryProfileCapabilities noPublicAccess() {
        return new StorageDeliveryProfileCapabilities(true, true, false, false, false, false, false, false, false);
    }
}
