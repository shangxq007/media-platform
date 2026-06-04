package com.example.platform.shared.imports;

import java.nio.file.Path;

/**
 * Result of downloading an import asset.
 *
 * @param tempFile    path to the downloaded temporary file (caller must clean up)
 * @param sizeBytes   actual downloaded size in bytes
 * @param checksum    computed SHA-256 checksum (sha256:<hex>)
 */
public record DownloadedAsset(
        Path tempFile,
        long sizeBytes,
        String checksum
) {}
