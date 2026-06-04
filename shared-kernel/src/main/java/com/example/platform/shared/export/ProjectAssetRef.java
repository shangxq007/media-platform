package com.example.platform.shared.export;

/**
 * Immutable reference to a project asset for export purposes.
 *
 * <p>Contains all metadata needed for export. The {@code storageUri} field is
 * for internal use only (signed URL generation) and must never be exposed in
 * API responses.
 */
public record ProjectAssetRef(
        String assetId,
        String assetType,
        String mimeType,
        String filename,
        Long sizeBytes,
        String storageUri,
        String checksum,
        Long durationMs,
        Integer width,
        Integer height
) {
    /**
     * Convenience constructor without optional media metadata.
     */
    public ProjectAssetRef(String assetId, String assetType, String mimeType,
                            String filename, Long sizeBytes, String storageUri, String checksum) {
        this(assetId, assetType, mimeType, filename, sizeBytes, storageUri, checksum, null, null, null);
    }
}
