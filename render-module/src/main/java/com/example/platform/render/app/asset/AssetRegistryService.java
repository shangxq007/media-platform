package com.example.platform.render.app.asset;

import com.example.platform.render.domain.asset.Asset;
import com.example.platform.render.domain.asset.AssetGovernanceMetadata;
import com.example.platform.render.domain.asset.AssetIdentity;
import com.example.platform.render.domain.asset.AssetLineageMetadata;
import com.example.platform.render.domain.asset.AssetRegistryRecord;
import com.example.platform.shared.tenant.StorageKeyPolicy;
import com.example.platform.shared.web.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Phase 1 lightweight Asset Registry service.
 *
 * <p>Provides asset identity resolution, governance attachment, lineage attachment,
 * and produces OTIO metadata references and JSON-LD projections.
 * Lives within {@code render-module} — not a standalone module.</p>
 */
@Service
public class AssetRegistryService {

    private final com.example.platform.render.infrastructure.asset.AssetRepository assetRepository;

    public AssetRegistryService(com.example.platform.render.infrastructure.asset.AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    /**
     * Register or upsert asset identity.
     */
    public AssetRegistryRecord register(String projectId, String storageKey, String mediaType,
                                         String filename, Long sizeBytes, String checksum,
                                         Long durationMs, Integer width, Integer height,
                                         String assetVersion, String ownerId) {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new SecurityException("Tenant context required for asset registration");
        }
        StorageKeyPolicy.assertValidPath(storageKey);

        Asset asset = assetRepository.register(tenantId, projectId, storageKey, mediaType,
                filename, sizeBytes, checksum, durationMs, width, height);

        String entityRef = "asset://" + asset.id() + "?v=" + (assetVersion != null ? assetVersion : "v1");
        String xmpUri = "xmp://asset/" + asset.id() + "/version/" + (assetVersion != null ? assetVersion : "v1");

        return new AssetRegistryRecord(
                asset.id(),
                assetVersion != null ? assetVersion : "v1",
                asset.mediaType(),
                ownerId,
                projectId,
                entityRef,
                xmpUri,
                storageKey,
                checksum,
                AssetGovernanceMetadata.defaults(),
                asset.createdAt(),
                asset.createdAt());
    }

    /**
     * Resolve asset by ID.
     */
    public Optional<AssetRegistryRecord> resolve(String assetId) {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new SecurityException("Tenant context required");
        }
        return assetRepository.findById(tenantId, assetId)
                .map(a -> toRegistryRecord(a, null));
    }

    /**
     * Resolve asset identity (lightweight).
     */
    public Optional<AssetIdentity> resolveIdentity(String assetId) {
        String tenantId = TenantContext.get();
        if (tenantId == null || tenantId.isBlank()) {
            throw new SecurityException("Tenant context required");
        }
        return assetRepository.findById(tenantId, assetId)
                .map(a -> new AssetIdentity(a.id(), null, "asset://" + assetId, "xmp://asset/" + assetId));
    }

    /**
     * Attach governance metadata to an asset.
     */
    public AssetRegistryRecord attachGovernance(String assetId, AssetGovernanceMetadata governance) {
        var resolved = resolve(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));
        return new AssetRegistryRecord(
                resolved.assetId(), resolved.assetVersion(), resolved.assetType(),
                resolved.ownerId(), resolved.projectId(), resolved.entityRef(), resolved.xmpUri(),
                resolved.storageUri(), resolved.checksum(), governance,
                resolved.createdAt(), Instant.now());
    }

    /**
     * Produce OTIO metadata reference map for a clip.
     */
    public java.util.Map<String, String> buildOtioClipMetadataRef(String assetId, String assetVersion) {
        var refs = new java.util.LinkedHashMap<String, String>();
        refs.put("bluepulse.asset_id", assetId);
        refs.put("bluepulse.asset_version", assetVersion != null ? assetVersion : "v1");
        refs.put("bluepulse.xmp_uri", "xmp://asset/" + assetId + "/version/" + (assetVersion != null ? assetVersion : "v1"));
        refs.put("bluepulse.entity_ref", "asset://" + assetId + "?v=" + (assetVersion != null ? assetVersion : "v1"));
        return refs;
    }

    /**
     * Produce JSON-LD projection for an asset.
     */
    public java.util.Map<String, Object> buildJsonLdProjection(String assetId) {
        var resolved = resolve(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));
        var ld = new java.util.LinkedHashMap<String, Object>();
        ld.put("@context", jsonLdContext());
        ld.put("@id", "asset:" + resolved.assetId());
        ld.put("@type", "MediaAsset");
        ld.put("asset:id", resolved.assetId());
        ld.put("asset:version", resolved.assetVersion());
        ld.put("asset:type", resolved.assetType());
        ld.put("asset:storageUri", resolved.storageUri());
        ld.put("asset:checksum", resolved.checksum());
        if (resolved.governance() != null) {
            ld.put("governance:classification", resolved.governance().classification());
            ld.put("governance:license", resolved.governance().license());
            ld.put("governance:retentionPolicy", resolved.governance().retentionPolicy());
            ld.put("governance:securityLevel", resolved.governance().securityLevel());
            ld.put("governance:containsPii", resolved.governance().containsPii());
            ld.put("governance:aiGenerated", resolved.governance().aiGenerated());
        }
        ld.put("asset:entityRef", resolved.entityRef());
        ld.put("asset:createdAt", resolved.createdAt() != null ? resolved.createdAt().toString() : null);
        ld.put("asset:updatedAt", resolved.updatedAt() != null ? resolved.updatedAt().toString() : null);
        return ld;
    }

    private java.util.Map<String, String> jsonLdContext() {
        var ctx = new java.util.LinkedHashMap<String, String>();
        ctx.put("asset", "https://open-media.org/xmp/asset/1.0/");
        ctx.put("ai", "https://open-media.org/xmp/ai/1.0/");
        ctx.put("lineage", "https://open-media.org/xmp/lineage/1.0/");
        ctx.put("governance", "https://open-media.org/xmp/governance/1.0/");
        ctx.put("MediaAsset", "asset:MediaAsset");
        return ctx;
    }

    private AssetRegistryRecord toRegistryRecord(Asset asset, AssetGovernanceMetadata governance) {
        return new AssetRegistryRecord(
                asset.id(),
                null,
                asset.mediaType(),
                null,
                asset.projectId(),
                "asset://" + asset.id(),
                "xmp://asset/" + asset.id(),
                asset.storageKey(),
                asset.checksum(),
                governance != null ? governance : AssetGovernanceMetadata.defaults(),
                asset.createdAt(),
                asset.createdAt());
    }
}
