package com.example.platform.render.domain.asset.search;

import java.util.List;

/**
 * Paginated search response.
 */
public record AssetSearchResponse(
        int total,
        int page,
        int pageSize,
        List<AssetSearchResult> results) {

    public static AssetSearchResponse empty(int page, int pageSize) {
        return new AssetSearchResponse(0, page, pageSize, List.of());
    }
}
