package com.example.platform.render.domain.asset.search;

import java.util.List;

/**
 * Search request for assets — supports keyword query and governance filters.
 */
public record AssetSearchRequest(
        String query,
        List<String> assetTypes,
        String classification,
        String license,
        Boolean aiGenerated,
        Boolean containsPii,
        int page,
        int pageSize,
        String sort) {

    public static AssetSearchRequest of(String query) {
        return new AssetSearchRequest(query, List.of(), null, null, null, null, 1, 20, "score");
    }

    public static AssetSearchRequest of(String query, int page, int pageSize) {
        return new AssetSearchRequest(query, List.of(), null, null, null, null, page, pageSize, "score");
    }
}
