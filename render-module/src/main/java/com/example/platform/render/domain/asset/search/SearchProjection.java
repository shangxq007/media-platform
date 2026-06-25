package com.example.platform.render.domain.asset.search;

import java.util.List;

/**
 * Search projection — a persisted, rebuildable search read model.
 * Derived from Asset Registry + Semantic Metadata. The registry is
 * the authoritative source; the projection is cached for search.
 */
public record SearchProjection(
        String assetId,
        String tenantId,
        String projectId,
        String filename,
        String assetType,
        String transcriptText,
        List<String> sceneLabels,
        List<String> objects,
        List<String> brands,
        List<String> people,
        String classification,
        String license,
        String publishStatus,
        String searchText,
        int score) {

    public static SearchProjection empty(String assetId) {
        return new SearchProjection(assetId, null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), null, null, null, null, 0);
    }
}
