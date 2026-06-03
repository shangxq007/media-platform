package com.example.platform.shared.export;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Port interface for querying project assets and generating signed download URLs
 * during project export (linked_assets mode).
 *
 * <p>Implementations are provided by modules that have access to both project asset
 * metadata and storage signing capabilities (e.g., artifact-catalog-module + storage-module).
 */
public interface ProjectAssetExportPort {

    /**
     * List exportable assets for a project.
     *
     * @param projectId project identifier
     * @return list of asset descriptors for export
     */
    List<ProjectAssetDescriptor> listProjectAssets(String projectId);

    /**
     * Generate a signed download URL for a project asset.
     *
     * @param projectId  project identifier
     * @param assetId    asset identifier
     * @param storageUri storage URI (provider://bucket/key)
     * @param ttl        time-to-live for the signed URL
     * @return signed URL, or empty if signing failed
     */
    Optional<String> generateSignedAssetUrl(String projectId, String assetId,
                                             String storageUri, Duration ttl);

    /**
     * Check if this port is available (storage signing is configured).
     */
    boolean isAvailable();
}
