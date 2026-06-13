package com.example.platform.web.assets;

import com.example.platform.render.domain.asset.Asset;
import com.example.platform.render.infrastructure.asset.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
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
}
