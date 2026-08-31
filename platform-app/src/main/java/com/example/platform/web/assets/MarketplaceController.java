package com.example.platform.web.assets;

import com.example.platform.render.infrastructure.asset.MarketplaceListingRepository;
import com.example.platform.shared.authorization.FailClosedAuthorization;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Marketplace API", description = "Marketplace listing registry and search")
public class MarketplaceController {

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
        throw FailClosedAuthorization.unavailable("marketplace listing search");
    }

    @GetMapping("/marketplace/listings")
    @Operation(summary = "List marketplace listings by status")
    public List<MarketplaceListingDto> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50") int limit) {
        throw FailClosedAuthorization.unavailable("marketplace listing read");
    }

    @GetMapping("/marketplace/listings/{listingId}")
    @Operation(summary = "Get a marketplace listing")
    public ResponseEntity<MarketplaceListingDto> get(@PathVariable String listingId) {
        throw FailClosedAuthorization.unavailable("marketplace listing read");
    }

    @GetMapping("/marketplace/assets/{assetId}/listing")
    @Operation(summary = "Get listing for an asset (tenant-scoped)")
    public ResponseEntity<MarketplaceListingDto> getByAsset(
            @PathVariable String assetId,
            @RequestParam(required = false) String tenantId) {
        throw FailClosedAuthorization.unavailable("marketplace asset listing read");
    }

    @PatchMapping("/marketplace/listings/{listingId}/status")
    @Operation(summary = "Update listing status with lifecycle validation")
    public ResponseEntity<MarketplaceListingDto> updateStatus(
            @PathVariable String listingId, @RequestBody StatusUpdateRequest body) {
        throw FailClosedAuthorization.unavailable("marketplace listing status mutation");
    }

    @GetMapping("/marketplace/discovery")
    @Operation(summary = "Discovery feed — recent, popular, featured listings")
    public MarketplaceDiscoveryResponse discovery(@RequestParam(defaultValue = "10") int limit) {
        throw FailClosedAuthorization.unavailable("marketplace discovery read");
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
