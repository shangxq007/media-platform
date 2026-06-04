package com.example.platform.storage.domain;

import java.nio.file.Path;

/**
 * Command to write an object to blob storage.
 *
 * <p>Two write modes are supported:
 * <ul>
 *   <li><b>byte[]</b>: in-memory content via {@code content} field (legacy, for small objects)</li>
 *   <li><b>Path</b>: streaming from file via {@code contentPath} field (preferred for large files)</li>
 * </ul>
 *
 * <p>When both are provided, implementations should prefer {@code contentPath} for streaming.
 * When neither is provided, implementations should throw.
 */
public record PutObjectCommand(
        String bucket,
        String objectKey,
        byte[] content,
        Path contentPath,
        String contentType
) {
    /**
     * Legacy constructor for byte[] content (backward compatible).
     */
    public PutObjectCommand(String bucket, String objectKey, byte[] content, String contentType) {
        this(bucket, objectKey, content, null, contentType);
    }

    /**
     * Factory for file-based streaming write.
     */
    public static PutObjectCommand fromPath(String bucket, String objectKey, Path filePath, String contentType) {
        return new PutObjectCommand(bucket, objectKey, null, filePath, contentType);
    }

    /**
     * Returns true if this command uses file-based streaming.
     */
    public boolean isFileBased() {
        return contentPath != null;
    }
}
