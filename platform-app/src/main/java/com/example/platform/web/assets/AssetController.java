package com.example.platform.web.assets;

import com.example.platform.render.domain.asset.Asset;
import com.example.platform.render.domain.asset.AssetGovernanceMetadata;
import com.example.platform.render.infrastructure.asset.AssetService;
import com.example.platform.render.app.asset.AssetRegistryService;
import com.example.platform.render.app.asset.AssetJsonLdExporter;
import com.example.platform.render.app.event.TimelineReviewEventPublisher;
import com.example.platform.shared.events.AssetRegisteredEvent;
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
 * <p>All endpoints enforce tenant + project scoping.
 * Storage keys are validated via {@link com.example.platform.shared.tenant.StorageKeyPolicy}.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/assets")
@Tag(name = "Asset API", description = "Project asset management")
public class AssetController {

    private final AssetService assetService;
    private final AssetRegistryService assetRegistryService;
    private final TimelineReviewEventPublisher eventPublisher;

    public AssetController(AssetService assetService, AssetRegistryService assetRegistryService,
                            TimelineReviewEventPublisher eventPublisher) {
        this.assetService = assetService;
        this.assetRegistryService = assetRegistryService;
        this.eventPublisher = eventPublisher;
    }

    @GetMapping
    @Operation(summary = "List project assets")
    public ResponseEntity<List<Asset>> listAssets(@PathVariable String projectId) {
        return ResponseEntity.ok(assetService.listByProject(projectId));
    }

    @GetMapping("/{assetId}")
    @Operation(summary = "Get asset by ID")
    public ResponseEntity<Asset> getAsset(@PathVariable String projectId, @PathVariable String assetId) {
        return ResponseEntity.ok(assetService.getById(projectId, assetId));
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new asset")
    public ResponseEntity<Asset> registerAsset(
            @PathVariable String projectId,
            @Valid @RequestBody RegisterAssetRequest request) {
        Asset asset = assetService.register(
                projectId,
                request.storageKey(),
                request.mediaType(),
                request.filename(),
                request.sizeBytes(),
                request.checksum(),
                request.durationMs(),
                request.width(),
                request.height()
        );
        eventPublisher.publish(new AssetRegisteredEvent(asset.id(), "v1", asset.mediaType(),
                projectId, asset.tenantId(), asset.storageKey()));
        return ResponseEntity.status(HttpStatus.CREATED).body(asset);
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

    /**
     * Request body for asset registration.
     */
    public record RegisterAssetRequest(
            @NotBlank String storageKey,
            @NotBlank String mediaType,
            String filename,
            Long sizeBytes,
            String checksum,
            Long durationMs,
            Integer width,
            Integer height
    ) {}

    public record AssetVersionResponse(
            String assetId,
            String assetVersion,
            String assetType,
            String ownerId,
            String projectId,
            String entityRef,
            String storageUri,
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
                r.storageUri(),
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
}
