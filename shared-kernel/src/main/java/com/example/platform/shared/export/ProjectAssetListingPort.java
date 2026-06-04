package com.example.platform.shared.export;

import java.util.List;

/**
 * Port interface for listing project assets.
 *
 * <p>Implementations must enforce tenant boundary — only return assets belonging
 * to the specified tenant's project. The listing should include all exportable
 * assets (video, audio, image, subtitle) with complete metadata.
 *
 * <p>This port is separate from {@link AssetDownloadUrlPort} to maintain clean
 * separation of concerns: listing vs signing.
 */
public interface ProjectAssetListingPort {

    /**
     * List all exportable assets for a project.
     *
     * @param tenantId  tenant identifier (for access control)
     * @param projectId project identifier
     * @return list of asset references; empty list if none found
     * @throws IllegalArgumentException if tenant or project not found
     */
    List<ProjectAssetRef> listAssets(String tenantId, String projectId);

    /**
     * Check if this port is available (e.g., asset service is configured).
     */
    boolean isAvailable();
}
