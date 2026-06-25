package com.example.platform.render.domain.asset.search;

import java.util.List;

/**
 * A single search result — asset matched with score and highlight fields.
 */
public record AssetSearchResult(
        String assetId,
        String assetVersion,
        String assetType,
        String filename,
        String storageKey,
        String checksum,
        int score,
        List<MatchedField> matchedFields) {

    public static AssetSearchResult of(String assetId, String assetVersion, String assetType,
                                         String filename, String storageKey, String checksum,
                                         int score, List<MatchedField> matchedFields) {
        return new AssetSearchResult(assetId, assetVersion, assetType, filename, storageKey,
                checksum, score, matchedFields);
    }
}
