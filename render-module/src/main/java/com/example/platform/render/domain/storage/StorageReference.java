package com.example.platform.render.domain.storage;

import java.time.Instant;

/**
 * Storage reference — physical location of a Product or Asset.
 * No signed URLs. No credentials. No permissions.
 */
public record StorageReference(
        String storageReferenceId,
        String providerType,
        StorageClass storageClass,
        String rootPath,
        String relativePath,
        String checksum,
        String contentHash,
        long fileSize,
        String mimeType,
        Instant createdAt,
        Instant updatedAt) {

    public String absolutePath() {
        if (rootPath != null && relativePath != null) {
            return rootPath.endsWith("/") ? rootPath + relativePath : rootPath + "/" + relativePath;
        }
        return relativePath;
    }
}
