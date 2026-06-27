package com.example.platform.render.domain.storage;

/**
 * Physical storage provider type.
 *
 * <p>Storage-neutral naming: backend-specific names (RustFS, SeaweedFS, MinIO)
 * are NOT first-class values. Use {@code S3} or {@code S3_COMPATIBLE} for
 * any S3-compatible object storage.</p>
 */
public enum StorageProviderType {
    LOCAL,
    /** Generic S3-compatible object storage (preferred). */
    S3,
    /** Explicit S3-compatible alias. */
    S3_COMPATIBLE,
    /** Storage-neutral alias for object storage. */
    OBJECT_STORAGE,
    /** @deprecated Use {@link #S3} instead. */
    @Deprecated
    MINIO,
    OSS,
    GCS,
    AZURE
}
