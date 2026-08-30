package com.example.platform.web.assets;

import com.example.platform.render.domain.asset.Asset;
import com.example.platform.render.domain.asset.AssetGovernanceMetadata;
import com.example.platform.render.infrastructure.asset.AssetService;
import com.example.platform.render.app.asset.AssetRegistryService;
import com.example.platform.render.app.asset.AssetJsonLdExporter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.List;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for project assets.
 *
 * <p>All endpoints enforce tenant + project scoping and return redacted metadata.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/assets")
@Tag(name = "Asset API", description = "Project asset management")
public class AssetController {

    private final AssetService assetService;
    private final AssetRegistryService assetRegistryService;

    public AssetController(AssetService assetService, AssetRegistryService assetRegistryService) {
        this.assetService = assetService;
        this.assetRegistryService = assetRegistryService;
    }

    @GetMapping
    @Operation(summary = "List project assets")
    public ResponseEntity<List<AssetResponse>> listAssets(@PathVariable String projectId) {
        return ResponseEntity.ok(assetService.listByProject(projectId).stream()
                .map(AssetController::toAssetResponse).toList());
    }

    @GetMapping("/{assetId}")
    @Operation(summary = "Get asset by ID")
    public ResponseEntity<AssetResponse> getAsset(@PathVariable String projectId, @PathVariable String assetId) {
        return ResponseEntity.ok(toAssetResponse(assetService.getById(projectId, assetId)));
    }

    @GetMapping("/{assetId}/preview-url")
    @Operation(summary = "Get asset preview URL")
    public ResponseEntity<Map<String, String>> getPreviewUrl(
            @PathVariable String projectId,
            @PathVariable String assetId) {
        String url = assetService.getPreviewUrl(projectId, assetId);
        return ResponseEntity.ok(Map.of("previewUrl", url));
    }

    @DeleteMapping("/{assetId}")
    @Operation(summary = "Delete an asset")
    public ResponseEntity<Map<String, Object>> deleteAsset(
            @PathVariable String projectId,
            @PathVariable String assetId) {
        boolean deleted = assetService.delete(projectId, assetId);
        return ResponseEntity.ok(Map.of("deleted", deleted, "assetId", assetId));
    }

    @GetMapping("/{assetId}/versions")
    @Operation(summary = "Get asset version history")
    public ResponseEntity<AssetVersionResponse> getVersions(
            @PathVariable String projectId,
            @PathVariable String assetId) {
        return assetRegistryService.resolve(assetId)
                .map(r -> ResponseEntity.ok(toVersionResponse(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{assetId}/governance")
    @Operation(summary = "Get asset governance metadata")
    public ResponseEntity<AssetGovernanceResponse> getGovernance(
            @PathVariable String projectId,
            @PathVariable String assetId) {
        return assetRegistryService.resolve(assetId)
                .map(r -> ResponseEntity.ok(toGovernanceResponse(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{assetId}/jsonld")
    @Operation(summary = "Export asset as JSON-LD")
    public ResponseEntity<Map<String, Object>> exportJsonLd(
            @PathVariable String projectId,
            @PathVariable String assetId) {
        return assetRegistryService.resolve(assetId)
                .map(r -> ResponseEntity.ok(assetRegistryService.buildJsonLdProjection(assetId)))
                .orElse(ResponseEntity.notFound().build());
    }

    public record AssetResponse(
            String id, String tenantId, String projectId, String mediaType, String filename,
            Long sizeBytes, String checksum, String assetVersion, String ownerId,
            String classification, String license, String retentionPolicy, String securityLevel,
            boolean containsPii, boolean aiGenerated, String publishStatus,
            java.time.Instant createdAt, java.time.Instant updatedAt) {}

    public record AssetVersionResponse(
            String assetId,
            String assetVersion,
            String assetType,
            String ownerId,
            String projectId,
            String entityRef,
            String checksum,
            String createdAt,
            String updatedAt,
            boolean currentOnly) {}

    public record AssetGovernanceResponse(
            String assetId,
            String assetVersion,
            String classification,
            String license,
            String retentionPolicy,
            String securityLevel,
            boolean containsPii,
            boolean aiGenerated) {}

    private static AssetVersionResponse toVersionResponse(
            com.example.platform.render.domain.asset.AssetRegistryRecord r) {
        return new AssetVersionResponse(
                r.assetId(),
                r.assetVersion(),
                r.assetType(),
                r.ownerId(),
                r.projectId(),
                r.entityRef(),
                r.checksum(),
                r.createdAt() != null ? r.createdAt().toString() : null,
                r.updatedAt() != null ? r.updatedAt().toString() : null,
                true);
    }

    private static AssetGovernanceResponse toGovernanceResponse(
            com.example.platform.render.domain.asset.AssetRegistryRecord r) {
        AssetGovernanceMetadata g = r.governance();
        return new AssetGovernanceResponse(
                r.assetId(),
                r.assetVersion(),
                g != null ? g.classification() : null,
                g != null ? g.license() : null,
                g != null ? g.retentionPolicy() : null,
                g != null ? g.securityLevel() : null,
                g != null && g.containsPii(),
                g != null && g.aiGenerated());
    }

    private static AssetResponse toAssetResponse(Asset asset) {
        return new AssetResponse(
                asset.id(), asset.tenantId(), asset.projectId(), asset.mediaType(), asset.filename(),
                asset.sizeBytes(), asset.checksum(), asset.assetVersion(), asset.ownerId(),
                asset.classification(), asset.license(), asset.retentionPolicy(), asset.securityLevel(),
                asset.containsPii(), asset.aiGenerated(), asset.publishStatus(),
                asset.createdAt(), asset.updatedAt());
    }
}
