package com.example.platform.render.domain.storage;

/**
 * Physical storage provider type.
 *
 * <p>Minimal set for this project stage. Backend-specific names (RustFS, SeaweedFS,
 * MinIO) are NOT provider types — they are deployment backends behind a generic
 * S3-compatible endpoint. Use {@code S3} or {@code S3_COMPATIBLE} for any
 * S3-compatible object storage.</p>
 *
 * <p>GCS, Azure Blob, OSS may become future native providers but are not accepted
 * as S3-compatible provider types without explicit compatibility validation.</p>
 */
public enum StorageProviderType {
    LOCAL,
    /** Generic S3-compatible object storage (preferred). */
    S3,
    /** Explicit S3-compatible alias. */
    S3_COMPATIBLE,
    /** Storage-neutral alias for object storage. */
    OBJECT_STORAGE
}
