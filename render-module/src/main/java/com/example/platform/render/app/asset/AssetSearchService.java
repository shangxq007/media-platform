package com.example.platform.render.app.asset;

import com.example.platform.render.domain.asset.search.*;
import com.example.platform.render.infrastructure.asset.AssetRepository;
import com.example.platform.render.infrastructure.asset.AssetSemanticMetadataRepository;
import com.example.platform.render.infrastructure.asset.SearchProjectionRepository;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Asset search service — queries the search_projection read model
 * via PostgreSQL Full-Text Search, with fallback to direct Asset +
 * Semantic Metadata scan when projections are not yet built.
 */
@Service
public class AssetSearchService {

    private static final Logger log = LoggerFactory.getLogger(AssetSearchService.class);
    private static final int SCORE_TRANSCRIPT = 10;
    private static final int SCORE_SCENE = 8;
    private static final int SCORE_METADATA = 4;

    private final AssetRepository assetRepository;
    private final AssetSemanticMetadataRepository semanticRepo;
    private final SearchProjectionRepository projectionRepo;

    public AssetSearchService(AssetRepository assetRepository,
                                AssetSemanticMetadataRepository semanticRepo,
                                SearchProjectionRepository projectionRepo) {
        this.assetRepository = assetRepository;
        this.semanticRepo = semanticRepo;
        this.projectionRepo = projectionRepo;
    }

    public AssetSearchResponse search(AssetSearchRequest request, String tenantId, String projectId) {
        String query = request.query();

        if (query != null && !query.isBlank()) {
            List<SearchProjection> projections = projectionRepo.ftsSearch(query, projectId, 100);
            if (!projections.isEmpty()) {
                log.info("Search FTS hit: query='{}' results={}", query, projections.size());
                return toResponse(projections, request);
            }
            log.warn("Search FTS miss — falling back to direct scan: query='{}'", query);
        }
        return fallbackSearch(request, tenantId, projectId);
    }

    private AssetSearchResponse toResponse(List<SearchProjection> projections, AssetSearchRequest request) {
        List<AssetSearchResult> results = projections.stream()
                .filter(p -> matchesFilters(p, request))
                .map(p -> AssetSearchResult.of(p.assetId(), "v1", p.assetType(),
                        p.filename(), "", "", p.score(),
                        List.of(MatchedField.of("search_text", p.searchText() != null ? excerpt(p.searchText(), request.query(), 30) : "", p.score()))))
                .collect(Collectors.toList());
        return paginate(results, request.page(), request.pageSize());
    }

    private boolean matchesFilters(SearchProjection p, AssetSearchRequest request) {
        if (request.assetTypes() != null && !request.assetTypes().isEmpty()) {
            if (!request.assetTypes().contains(p.assetType())) return false;
        }
        if (request.classification() != null && !request.classification().equals(p.classification())) return false;
        return true;
    }

    private AssetSearchResponse fallbackSearch(AssetSearchRequest request, String tenantId, String projectId) {
        List<String> queries = extractQueries(request.query());
        if (queries.isEmpty()) {
            return filterOnly(request, tenantId, projectId);
        }
        return keywordSearch(request, queries, tenantId, projectId);
    }

    private List<String> extractQueries(String query) {
        if (query == null || query.isBlank()) return List.of();
        return Arrays.stream(query.toLowerCase().split("\\s+"))
                .filter(w -> w.length() >= 1).toList();
    }

    private AssetSearchResponse keywordSearch(AssetSearchRequest request,
                                                List<String> queries,
                                                String tenantId, String projectId) {
        List<AssetSearchResult> results = new ArrayList<>();
        List<com.example.platform.render.domain.asset.Asset> assets = assetRepository.listByProject(tenantId, projectId);
        for (var asset : assets) {
            int score = 0;
            List<MatchedField> matched = new ArrayList<>();
            String name = asset.filename() != null ? asset.filename().toLowerCase() : "";
            for (String q : queries) {
                if (name.contains(q)) { score += SCORE_METADATA; matched.add(MatchedField.of("filename", asset.filename(), SCORE_METADATA)); }
            }
            var semanticRow = semanticRepo.findById(asset.id());
            if (semanticRow.isPresent() && semanticRow.get().semanticJson() != null) {
                String sj = semanticRow.get().semanticJson().toLowerCase();
                for (String q : queries) {
                    if (sj.contains(q)) score += scoreSemanticMatch(sj, q, matched);
                }
            }
            if (score > 0 && matchesAssetFilters(asset, request)) {
                results.add(AssetSearchResult.of(asset.id(), "v1", asset.mediaType(),
                        asset.filename(), asset.storageKey(), asset.checksum(), score, dedupeFields(matched)));
            }
        }
        results.sort((a, b) -> Integer.compare(b.score(), a.score()));
        return paginate(results, request.page(), request.pageSize());
    }

    private int scoreSemanticMatch(String sj, String q, List<MatchedField> matched) {
        int s = 0;
        if (sj.contains("\"text\":") && sj.contains(q)) { s += SCORE_TRANSCRIPT; matched.add(MatchedField.of("transcript", excerpt(sj, q, 40), SCORE_TRANSCRIPT)); }
        if (sj.contains("\"label\":") && sj.contains("\"" + q + "\"")) { s += SCORE_SCENE; matched.add(MatchedField.of("scene", q, SCORE_SCENE)); }
        return s;
    }

    private boolean matchesAssetFilters(com.example.platform.render.domain.asset.Asset a, AssetSearchRequest r) {
        if (r.assetTypes() != null && !r.assetTypes().isEmpty() && !r.assetTypes().contains(a.mediaType())) return false;
        if (r.classification() != null && !r.classification().equals(a.classification())) return false;
        if (r.aiGenerated() != null && a.aiGenerated() != r.aiGenerated()) return false;
        return r.containsPii() == null || a.containsPii() == r.containsPii();
    }

    private AssetSearchResponse filterOnly(AssetSearchRequest request, String tenantId, String projectId) {
        List<AssetSearchResult> results = assetRepository.listByProject(tenantId, projectId)
                .stream().filter(a -> matchesAssetFilters(a, request))
                .map(a -> AssetSearchResult.of(a.id(), "v1", a.mediaType(), a.filename(),
                        a.storageKey(), a.checksum(), 0, List.of()))
                .collect(Collectors.toList());
        return paginate(results, request.page(), request.pageSize());
    }

    private AssetSearchResponse paginate(List<AssetSearchResult> results, int page, int pageSize) {
        int total = results.size();
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        return new AssetSearchResponse(total, page, pageSize,
                from < total ? results.subList(from, to) : List.of());
    }

    private List<MatchedField> dedupeFields(List<MatchedField> fields) {
        Set<String> seen = new HashSet<>();
        List<MatchedField> deduped = new ArrayList<>();
        for (MatchedField f : fields) {
            if (seen.add(f.field() + ":" + f.value())) deduped.add(f);
        }
        return deduped;
    }

    private static String excerpt(String text, String query, int radius) {
        int idx = text.indexOf(query);
        if (idx < 0) return query;
        int start = Math.max(0, idx - radius);
        int end = Math.min(text.length(), idx + query.length() + radius);
        return "…" + text.substring(start, end).trim() + "…";
    }
}
