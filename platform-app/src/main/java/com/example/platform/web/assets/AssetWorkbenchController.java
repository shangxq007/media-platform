package com.example.platform.web.assets;

import com.example.platform.render.app.asset.*;
import com.example.platform.render.domain.asset.AssetGovernanceMetadata;
import com.example.platform.render.domain.asset.AssetRegistryRecord;
import com.example.platform.render.infrastructure.asset.MarketplaceListingRepository;
import com.example.platform.render.infrastructure.asset.SearchProjectionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assets/{assetId}/workspace")
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
        long start = System.currentTimeMillis();
        var asset = registryService.resolve(assetId);
        if (asset.isEmpty()) return ResponseEntity.notFound().build();

        AssetRegistryRecord r = asset.get();
        var semantic = semanticService.get(assetId);
        var pubStatus = reviewService.getPublishStatus(assetId);
        var marketplace = marketplaceRepo.findByAssetId(assetId, null);
        var searchProj = searchProjectionRepo.findByAssetId(assetId);

        var dto = new AssetWorkbenchDto(
                r.assetId(), r.assetType(), r.storageUri(), r.checksum(),
                r.createdAt() != null ? r.createdAt().toString() : null,
                r.updatedAt() != null ? r.updatedAt().toString() : null,
                pubStatus.map(Enum::name).orElse("DRAFT"),
                semantic.map(s -> s.status().name()).orElse("PENDING"),
                marketplace.map(m -> m.status().name()).orElse(null),
                searchProj.isPresent(),
                r.governance() != null ? r.governance().classification() : null,
                r.governance() != null ? r.governance().license() : null,
                r.assetVersion()
        );
        log.info("Asset workbench loaded: asset={} latency={}ms",
                assetId, System.currentTimeMillis() - start);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/semantic")
    @Operation(summary = "Semantic metadata workspace")
    public ResponseEntity<SemanticWsDto> semantic(@PathVariable String assetId) {
        return semanticService.get(assetId)
                .map(s -> ResponseEntity.ok(new SemanticWsDto(
                        s.transcripts() != null ? s.transcripts().stream()
                                .map(t -> t.text()).reduce("", (a, b) -> a + " " + b).trim() : "",
                        s.scenes() != null ? s.scenes().size() : 0,
                        s.objects() != null ? s.objects().size() : 0,
                        s.brands() != null ? s.brands().size() : 0,
                        s.people() != null ? s.people().size() : 0,
                        s.language(), s.status().name())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/governance")
    @Operation(summary = "Governance workspace")
    public ResponseEntity<GovernanceWsDto> governance(@PathVariable String assetId) {
        return registryService.resolve(assetId)
                .map(r -> {
                    AssetGovernanceMetadata g = r.governance() != null ? r.governance()
                            : AssetGovernanceMetadata.defaults();
                    return ResponseEntity.ok(new GovernanceWsDto(
                            g.classification(), g.license(), g.containsPii(),
                            g.retentionPolicy(), g.securityLevel(),
                            r.ownerId(), r.assetVersion()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/marketplace")
    @Operation(summary = "Marketplace listing workspace")
    public ResponseEntity<MarketplaceWsDto> marketplace(@PathVariable String assetId) {
        return marketplaceRepo.findByAssetId(assetId, null)
                .map(m -> ResponseEntity.ok(new MarketplaceWsDto(
                        m.id(), m.status().name(),
                        m.listingType() != null ? m.listingType().name() : null,
                        m.previewUrl(), m.coverUrl(),
                        m.updatedAt() != null ? m.updatedAt().toString() : null)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search")
    @Operation(summary = "Search projection workspace")
    public ResponseEntity<SearchWsDto> search(@PathVariable String assetId) {
        return searchProjectionRepo.findByAssetId(assetId)
                .map(p -> ResponseEntity.ok(new SearchWsDto(
                        true,
                        p.searchText() != null ? p.searchText().length() : 0)))
                .orElse(ResponseEntity.ok(new SearchWsDto(false, 0)));
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
