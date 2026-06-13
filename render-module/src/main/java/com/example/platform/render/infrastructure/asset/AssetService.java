package com.example.platform.render.infrastructure.asset;

import com.example.platform.render.domain.asset.Asset;
import com.example.platform.shared.tenant.StorageKeyPolicy;
import com.example.platform.shared.web.TenantContext;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service for project asset management.
 *
 * <p>Enforces tenant isolation and validates storage keys via {@link StorageKeyPolicy}.
 */
@Service
public class AssetService {

    private final AssetRepository assetRepository;

    public AssetService(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    /**
     * Register a new asset for a project.
     */
    public Asset register(String projectId, String storageKey, String mediaType,
                          String filename, Long sizeBytes, String checksum,
                          Long durationMs, Integer width, Integer height) {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new SecurityException("Tenant context required for asset registration");
        }

        // Validate storage key
        StorageKeyPolicy.assertValidPath(storageKey);

        return assetRepository.register(tenantId, projectId, storageKey, mediaType,
                filename, sizeBytes, checksum, durationMs, width, height);
    }

    /**
     * Get an asset by ID, scoped to current tenant.
     */
    public Asset getById(String projectId, String assetId) {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new SecurityException("Tenant context required");
        }

        Asset asset = assetRepository.findById(tenantId, assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

        // Verify project scoping
        if (!asset.projectId().equals(projectId)) {
            throw new IllegalArgumentException("Asset not found in project: " + projectId);
        }

        return asset;
    }

    /**
     * List all assets for a project, scoped to current tenant.
     */
    public List<Asset> listByProject(String projectId) {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new SecurityException("Tenant context required");
        }

        return assetRepository.listByProject(tenantId, projectId);
    }

    /**
     * Delete an asset, scoped to current tenant.
     */
    public boolean delete(String projectId, String assetId) {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new SecurityException("Tenant context required");
        }

        // Verify ownership before delete
        Asset asset = assetRepository.findById(tenantId, assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

        if (!asset.projectId().equals(projectId)) {
            throw new IllegalArgumentException("Asset not found in project: " + projectId);
        }

        return assetRepository.delete(tenantId, assetId);
    }

    /**
     * Generate a preview URL for an asset.
     * Currently returns the storage key — in production this would generate a signed URL.
     */
    public String getPreviewUrl(String projectId, String assetId) {
        Asset asset = getById(projectId, assetId);
        // In production: generate signed URL from storage service
        // For now, return the storage key as a reference
        return "/api/v1/projects/" + projectId + "/assets/" + assetId + "/raw";
    }
}
