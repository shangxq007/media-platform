package com.example.platform.storage.delivery.contract;

import java.util.regex.Pattern;

public record StorageDeliveryProfileId(String value) {
    private static final Pattern KEBAB_CASE = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    public static final StorageDeliveryProfileId PREVIEW_R2_SIGNED_URL = new StorageDeliveryProfileId("preview-r2-signed-url");
    public static final StorageDeliveryProfileId LAB_OPENDAL_FS_INTERNAL = new StorageDeliveryProfileId("lab-opendal-fs-internal");
    public static final StorageDeliveryProfileId LAB_RUSTFS_S3_SIGNED_URL = new StorageDeliveryProfileId("lab-rustfs-s3-signed-url");
    public static final StorageDeliveryProfileId LAB_MINIO_S3_SIGNED_URL = new StorageDeliveryProfileId("lab-minio-s3-signed-url");
    public static final StorageDeliveryProfileId CUSTOMER_OWNED_S3_EXTERNAL_BUCKET = new StorageDeliveryProfileId("customer-owned-s3-external-bucket");
    public static final StorageDeliveryProfileId EXPORT_BUNDLE_R2 = new StorageDeliveryProfileId("export-bundle-r2");
    public static final StorageDeliveryProfileId PRIVATE_RENDER_ARTIFACTS_R2 = new StorageDeliveryProfileId("private-render-artifacts-r2");
    public static final StorageDeliveryProfileId INTERNAL_CACHE_LOCAL = new StorageDeliveryProfileId("internal-cache-local");

    public StorageDeliveryProfileId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Profile ID must not be blank");
        }
        if (!KEBAB_CASE.matcher(value).matches()) {
            throw new IllegalArgumentException("Profile ID must be lowercase kebab-case: " + value);
        }
    }
}
