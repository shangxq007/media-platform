package com.example.platform.storage.infrastructure;

/**
 * Presets for S3-compatible backends. {@link #R2} applies Cloudflare R2-required client flags.
 */
public enum S3CompatibilityMode {
    /** Use {@link StorageS3Properties} values as configured (MinIO, RustFS, AWS, etc.). */
    GENERIC,
    /** Cloudflare R2: region {@code auto}, path-style, chunked encoding disabled. */
    R2
}
