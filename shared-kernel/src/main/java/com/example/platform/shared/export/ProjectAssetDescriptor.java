package com.example.platform.shared.export;

/**
 * Descriptor of a project asset for export purposes.
 *
 * <p>Contains only metadata — no storage references or internal paths.
 */
public record ProjectAssetDescriptor(
        String assetId,
        String filename,
        String type,
        String mimeType,
        long sizeBytes,
        String sha256,
        Double duration,
        Integer width,
        Integer height,
        String storageUri  // Internal use only, not included in export response
) {}
