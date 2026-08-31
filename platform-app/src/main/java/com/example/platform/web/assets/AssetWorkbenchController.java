package com.example.platform.web.assets;

import com.example.platform.render.app.asset.*;
import com.example.platform.render.domain.asset.AssetGovernanceMetadata;
import com.example.platform.render.domain.asset.AssetRegistryRecord;
import com.example.platform.render.infrastructure.asset.MarketplaceListingRepository;
import com.example.platform.render.infrastructure.asset.SearchProjectionRepository;
import com.example.platform.shared.authorization.FailClosedAuthorization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assets/{assetId}/workspace")
@Tag(name = "Asset Workbench", description = "Aggregated asset workspace APIs for frontend")
public class AssetWorkbenchController {

    private static final Logger log = LoggerFactory.getLogger(AssetWorkbenchController.class);
    private final AssetRegistryService registryService;
    private final AssetSemanticMetadataService semanticService;
    private final AssetEnrichmentService enrichmentService;
    private final AssetReviewService reviewService;
    private final MarketplaceListingRepository marketplaceRepo;
    private final SearchProjectionRepository searchProjectionRepo;

    public AssetWorkbenchController(AssetRegistryService registryService,
                                      AssetSemanticMetadataService semanticService,
                                      AssetEnrichmentService enrichmentService,
                                      AssetReviewService reviewService,
                                      MarketplaceListingRepository marketplaceRepo,
                                      SearchProjectionRepository searchProjectionRepo) {
        this.registryService = registryService;
        this.semanticService = semanticService;
        this.enrichmentService = enrichmentService;
        this.reviewService = reviewService;
        this.marketplaceRepo = marketplaceRepo;
        this.searchProjectionRepo = searchProjectionRepo;
    }

    @GetMapping
    @Operation(summary = "Full asset workbench view")
    public ResponseEntity<AssetWorkbenchDto> workbench(@PathVariable String assetId) {
        throw FailClosedAuthorization.unavailable("tenant-unscoped asset workbench read");
    }

    @GetMapping("/semantic")
    @Operation(summary = "Semantic metadata workspace")
    public ResponseEntity<SemanticWsDto> semantic(@PathVariable String assetId) {
        throw FailClosedAuthorization.unavailable("tenant-unscoped asset semantic read");
    }

    @GetMapping("/governance")
    @Operation(summary = "Governance workspace")
    public ResponseEntity<GovernanceWsDto> governance(@PathVariable String assetId) {
        throw FailClosedAuthorization.unavailable("tenant-unscoped asset governance read");
    }

    @GetMapping("/marketplace")
    @Operation(summary = "Marketplace listing workspace")
    public ResponseEntity<MarketplaceWsDto> marketplace(@PathVariable String assetId) {
        throw FailClosedAuthorization.unavailable("tenant-unscoped asset marketplace read");
    }

    @GetMapping("/search")
    @Operation(summary = "Search projection workspace")
    public ResponseEntity<SearchWsDto> search(@PathVariable String assetId) {
        throw FailClosedAuthorization.unavailable("tenant-unscoped asset search read");
    }

    public record AssetWorkbenchDto(String assetId, String assetType, String storageUri,
            String checksum, String createdAt, String updatedAt,
            String publishStatus, String semanticStatus, String marketplaceStatus,
            boolean searchIndexed, String classification, String license, String version) {}

    public record SemanticWsDto(String transcript, int sceneCount, int objectCount,
            int brandCount, int peopleCount, String language, String status) {}

    public record GovernanceWsDto(String classification, String license, boolean containsPii,
            String retentionPolicy, String securityLevel, String ownerId, String version) {}

    public record MarketplaceWsDto(String listingId, String status, String listingType,
            String previewUrl, String coverUrl, String updatedAt) {}

    public record SearchWsDto(boolean indexed, int searchTextSize) {}
}
