package com.example.platform.storage.delivery.registry;

import com.example.platform.storage.delivery.contract.*;
import java.util.List;

public final class StorageDeliveryProfileCatalog {
    private StorageDeliveryProfileCatalog() {}

    public static List<StorageDeliveryProfile> canonicalProfiles() {
        return List.of(previewR2SignedUrl(), labOpenDalFsInternal(), labRustFsS3SignedUrl(),
            labMinioS3SignedUrl(), customerOwnedS3ExternalBucket(), exportBundleR2(),
            privateRenderArtifactsR2(), internalCacheLocal());
    }

    public static StorageDeliveryProfile previewR2SignedUrl() { return StorageDeliveryProfile.previewR2SignedUrl(); }

    public static StorageDeliveryProfile labOpenDalFsInternal() {
        return new StorageDeliveryProfile(StorageDeliveryProfileId.LAB_OPENDAL_FS_INTERNAL,
            StorageDeliveryProfileStatus.EXPERIMENTAL, false, false,
            StorageProviderType.OPENDAL, StorageBackendType.LOCAL_FS,
            StorageAccessMode.INTERNAL_STREAM, AccessDescriptorContractType.INTERNAL_STREAM,
            StorageDeliveryProfileCapabilities.internalStreamLab(), StorageDeliveryProfileSecurityPolicy.internalOnly());
    }

    public static StorageDeliveryProfile labRustFsS3SignedUrl() {
        return new StorageDeliveryProfile(StorageDeliveryProfileId.LAB_RUSTFS_S3_SIGNED_URL,
            StorageDeliveryProfileStatus.LAB_ONLY, false, false,
            StorageProviderType.S3_COMPATIBLE, StorageBackendType.RUSTFS,
            StorageAccessMode.SIGNED_URL, AccessDescriptorContractType.SIGNED_URL,
            StorageDeliveryProfileCapabilities.signedUrlR2Preview(), StorageDeliveryProfileSecurityPolicy.internalOnly());
    }

    public static StorageDeliveryProfile labMinioS3SignedUrl() {
        return new StorageDeliveryProfile(StorageDeliveryProfileId.LAB_MINIO_S3_SIGNED_URL,
            StorageDeliveryProfileStatus.DESIGN_ONLY, false, false,
            StorageProviderType.S3_COMPATIBLE, StorageBackendType.MINIO,
            StorageAccessMode.SIGNED_URL, AccessDescriptorContractType.SIGNED_URL,
            StorageDeliveryProfileCapabilities.signedUrlR2Preview(), StorageDeliveryProfileSecurityPolicy.internalOnly());
    }

    public static StorageDeliveryProfile customerOwnedS3ExternalBucket() {
        return new StorageDeliveryProfile(StorageDeliveryProfileId.CUSTOMER_OWNED_S3_EXTERNAL_BUCKET,
            StorageDeliveryProfileStatus.DESIGN_ONLY, false, false,
            StorageProviderType.CUSTOMER_S3, StorageBackendType.EXTERNAL_S3,
            StorageAccessMode.EXTERNAL_BUCKET, AccessDescriptorContractType.EXTERNAL_BUCKET,
            StorageDeliveryProfileCapabilities.externalBucketDesignOnly(), StorageDeliveryProfileSecurityPolicy.internalOnly());
    }

    public static StorageDeliveryProfile exportBundleR2() {
        return new StorageDeliveryProfile(StorageDeliveryProfileId.EXPORT_BUNDLE_R2,
            StorageDeliveryProfileStatus.DESIGN_ONLY, false, false,
            StorageProviderType.EXPORT_BUNDLE, StorageBackendType.R2,
            StorageAccessMode.EXPORT_BUNDLE, AccessDescriptorContractType.EXPORT_BUNDLE,
            StorageDeliveryProfileCapabilities.noPublicAccess(), StorageDeliveryProfileSecurityPolicy.internalOnly());
    }

    public static StorageDeliveryProfile privateRenderArtifactsR2() {
        return new StorageDeliveryProfile(StorageDeliveryProfileId.PRIVATE_RENDER_ARTIFACTS_R2,
            StorageDeliveryProfileStatus.DESIGN_ONLY, false, false,
            StorageProviderType.S3_COMPATIBLE, StorageBackendType.R2,
            StorageAccessMode.NO_PUBLIC_ACCESS, AccessDescriptorContractType.NO_PUBLIC_ACCESS,
            StorageDeliveryProfileCapabilities.noPublicAccess(), StorageDeliveryProfileSecurityPolicy.internalOnly());
    }

    public static StorageDeliveryProfile internalCacheLocal() {
        return new StorageDeliveryProfile(StorageDeliveryProfileId.INTERNAL_CACHE_LOCAL,
            StorageDeliveryProfileStatus.DESIGN_ONLY, false, false,
            StorageProviderType.INTERNAL_CACHE, StorageBackendType.LOCAL_FS,
            StorageAccessMode.NO_PUBLIC_ACCESS, AccessDescriptorContractType.NOT_USER_FACING,
            StorageDeliveryProfileCapabilities.noPublicAccess(), StorageDeliveryProfileSecurityPolicy.internalOnly());
    }
}
