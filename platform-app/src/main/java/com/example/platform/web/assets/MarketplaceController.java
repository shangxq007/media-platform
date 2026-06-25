package com.example.platform.web.assets;

import com.example.platform.render.domain.asset.marketplace.*;
import com.example.platform.render.infrastructure.asset.MarketplaceListingRepository;
import com.example.platform.render.infrastructure.asset.MarketplaceListingRepository.SearchResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Marketplace API", description = "Marketplace listing registry and search")
public class MarketplaceController {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceController.class);
    private final MarketplaceListingRepository listingRepo;

    public MarketplaceController(MarketplaceListingRepository listingRepo) {
        this.listingRepo = listingRepo;
    }

    @GetMapping("/marketplace/search")
    @Operation(summary = "Search marketplace listings with pagination and filters")
    public MarketplaceSearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String listingType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String projectId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        String filterStatus = status != null ? status : "PUBLISHED";
        SearchResult result = listingRepo.search(q, filterStatus, listingType, tenantId, projectId, offset, limit);
        return new MarketplaceSearchResponse(result.total(), result.offset(), result.limit(),
                result.results().stream().map(MarketplaceController::toDto).toList());
    }

    @GetMapping("/marketplace/listings")
    @Operation(summary = "List marketplace listings by status")
    public List<MarketplaceListingDto> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        return listingRepo.listByStatus(status != null ? status : "PUBLISHED", limit).stream()
                .map(MarketplaceController::toDto).toList();
    }

    @GetMapping("/marketplace/listings/{listingId}")
    @Operation(summary = "Get a marketplace listing")
    public ResponseEntity<MarketplaceListingDto> get(@PathVariable String listingId) {
        return listingRepo.findByAssetId(listingId, null)
                .map(l -> ResponseEntity.ok(toDto(l)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/marketplace/assets/{assetId}/listing")
    @Operation(summary = "Get listing for an asset (tenant-scoped)")
    public ResponseEntity<MarketplaceListingDto> getByAsset(
            @PathVariable String assetId,
            @RequestParam(required = false) String tenantId) {
        return listingRepo.findByAssetId(assetId, tenantId)
                .map(l -> ResponseEntity.ok(toDto(l)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/marketplace/listings/{listingId}/status")
    @Operation(summary = "Update listing status with lifecycle validation")
    public ResponseEntity<MarketplaceListingDto> updateStatus(
            @PathVariable String listingId, @RequestBody StatusUpdateRequest body) {
        MarketplaceListingStatus newStatus;
        try { newStatus = MarketplaceListingStatus.valueOf(body.status()); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().build(); }
        var listing = listingRepo.findByAssetId(listingId, null);
        if (listing.isEmpty()) return ResponseEntity.notFound().build();
        if (!isValidTransition(listing.get().status(), newStatus))
            return ResponseEntity.status(HttpStatus.CONFLICT).body(toDto(listing.get()));
        listingRepo.updateStatus(listingId, newStatus.name());
        return listingRepo.findByAssetId(listingId, null)
                .map(l -> ResponseEntity.ok(toDto(l))).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/marketplace/discovery")
    @Operation(summary = "Discovery feed — recent, popular, featured listings")
    public MarketplaceDiscoveryResponse discovery(@RequestParam(defaultValue = "10") int limit) {
        List<MarketplaceListing> recent = listingRepo.listByStatus("PUBLISHED", limit);
        return new MarketplaceDiscoveryResponse(
                recent.stream().map(MarketplaceController::toDto).toList(),
                recent.stream().map(MarketplaceController::toDto).toList(),
                List.of());
    }

    private boolean isValidTransition(MarketplaceListingStatus from, MarketplaceListingStatus to) {
        return switch (from) {
            case DRAFT -> to == MarketplaceListingStatus.READY || to == MarketplaceListingStatus.ARCHIVED;
            case READY -> to == MarketplaceListingStatus.PUBLISHED || to == MarketplaceListingStatus.ARCHIVED || to == MarketplaceListingStatus.DRAFT;
            case PUBLISHED -> to == MarketplaceListingStatus.ARCHIVED;
            case ARCHIVED -> false;
        };
    }

    private static MarketplaceListingDto toDto(MarketplaceListing l) {
        return new MarketplaceListingDto(l.id(), l.assetId(), l.tenantId(), l.projectId(),
                l.listingType() != null ? l.listingType().name() : null,
                l.title(), l.summary(), l.description(), l.previewUrl(), l.coverUrl(),
                l.status() != null ? l.status().name() : null, l.version(), l.reviewId(),
                l.createdAt() != null ? l.createdAt().toString() : null,
                l.updatedAt() != null ? l.updatedAt().toString() : null);
    }

    public record MarketplaceSearchResponse(int total, int offset, int limit,
                                               List<MarketplaceListingDto> results) {}
    public record MarketplaceDiscoveryResponse(List<MarketplaceListingDto> recent,
                                                  List<MarketplaceListingDto> popular,
                                                  List<MarketplaceListingDto> featured) {}
    public record MarketplaceListingDto(String id, String assetId, String tenantId, String projectId,
                                          String listingType, String title, String summary,
                                          String description, String previewUrl, String coverUrl,
                                          String status, String version, String reviewId,
                                          String createdAt, String updatedAt) {}
    public record StatusUpdateRequest(String status) {}
}
