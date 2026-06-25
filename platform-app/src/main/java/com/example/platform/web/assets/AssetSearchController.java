package com.example.platform.web.assets;

import com.example.platform.render.app.asset.AssetSearchService;
import com.example.platform.render.domain.asset.search.*;
import com.example.platform.shared.web.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/assets/search")
@Tag(name = "Asset Search", description = "Search assets by keyword and filters")
public class AssetSearchController {

    private final AssetSearchService searchService;

    public AssetSearchController(AssetSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    @Operation(summary = "Search assets with keyword query")
    public ResponseEntity<SearchResponseDto> search(
            @PathVariable String projectId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String assetType,
            @RequestParam(required = false) String classification,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        String tenantId = TenantContext.get();
        AssetSearchRequest req = new AssetSearchRequest(
                q, assetType != null ? List.of(assetType) : List.of(),
                classification, null, null, null, page, pageSize, "score");
        AssetSearchResponse result = searchService.search(req, tenantId, projectId);
        return ResponseEntity.ok(toDto(result));
    }

    @PostMapping
    @Operation(summary = "Search assets with full filter support")
    public ResponseEntity<SearchResponseDto> searchPost(
            @PathVariable String projectId,
            @RequestBody SearchPostRequest body) {
        String tenantId = TenantContext.get();
        AssetSearchRequest req = new AssetSearchRequest(
                body.query(), body.assetTypes(), body.classification(), body.license(),
                body.aiGenerated(), body.containsPii(),
                body.page() > 0 ? body.page() : 1,
                body.pageSize() > 0 ? body.pageSize() : 20,
                body.sort() != null ? body.sort() : "score");
        AssetSearchResponse result = searchService.search(req, tenantId, projectId);
        return ResponseEntity.ok(toDto(result));
    }

    private static SearchResponseDto toDto(AssetSearchResponse r) {
        return new SearchResponseDto(r.total(), r.page(), r.pageSize(),
                r.results().stream().map(AssetSearchController::toDto).toList());
    }

    private static SearchResultDto toDto(AssetSearchResult r) {
        return new SearchResultDto(r.assetId(), r.assetVersion(), r.assetType(), r.filename(),
                r.storageKey(), r.checksum(), r.score(),
                r.matchedFields().stream().map(f -> new MatchedFieldDto(f.field(), f.value(), f.scoreContribution())).toList());
    }

    public record SearchPostRequest(String query, List<String> assetTypes,
                                       String classification, String license,
                                       Boolean aiGenerated, Boolean containsPii,
                                       int page, int pageSize, String sort) {}

    public record SearchResponseDto(int total, int page, int pageSize, List<SearchResultDto> results) {}

    public record SearchResultDto(String assetId, String assetVersion, String assetType,
                                    String filename, String storageKey, String checksum,
                                    int score, List<MatchedFieldDto> matchedFields) {}

    public record MatchedFieldDto(String field, String value, int scoreContribution) {}
}
