package com.example.platform.render.infrastructure.asset;

import com.example.platform.render.domain.asset.search.SearchProjection;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.SearchProjection.SEARCH_PROJECTION;


@Repository
public class SearchProjectionRepository {

    private final DSLContext dsl;

    public SearchProjectionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void upsert(SearchProjection proj) {
        LocalDateTime now = LocalDateTime.now();
        String searchText = proj.searchText() != null ? proj.searchText() : "";
        dsl.execute(
                "INSERT INTO search_projection (asset_id, tenant_id, project_id, filename, asset_type, "
                        + "transcript_text, scene_labels, objects, brands, people, "
                        + "classification, license, publish_status, search_text, search_vector, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, to_tsvector('english', ?), ?) "
                        + "ON CONFLICT (asset_id) DO UPDATE SET "
                        + "filename = EXCLUDED.filename, asset_type = EXCLUDED.asset_type, "
                        + "transcript_text = EXCLUDED.transcript_text, scene_labels = EXCLUDED.scene_labels, "
                        + "objects = EXCLUDED.objects, brands = EXCLUDED.brands, people = EXCLUDED.people, "
                        + "classification = EXCLUDED.classification, license = EXCLUDED.license, "
                        + "publish_status = EXCLUDED.publish_status, search_text = EXCLUDED.search_text, "
                        + "search_vector = to_tsvector('english', ?), updated_at = ?",
                proj.assetId(), proj.tenantId(), proj.projectId(),
                proj.filename(), proj.assetType(),
                proj.transcriptText(), toString(proj.sceneLabels()),
                toString(proj.objects()), toString(proj.brands()),
                toString(proj.people()),
                proj.classification(), proj.license(),
                proj.publishStatus(), searchText, searchText, now,
                searchText, now);
    }

    public Optional<SearchProjection> findByAssetId(String assetId) {
        Record r = dsl.select().from(SEARCH_PROJECTION)
                .where(SEARCH_PROJECTION.ASSET_ID.eq(assetId)).fetchOne();
        return r == null ? Optional.empty() : Optional.of(map(r));
    }

    @SuppressWarnings("unchecked")
    public List<SearchProjection> ftsSearch(String query, String projectId, int limit) {
        if (query == null || query.isBlank()) return listByProject(projectId, limit);
        try {
            var results = dsl.fetch(
                    "SELECT *, ts_rank(search_vector, plainto_tsquery('english', ?)) as rank "
                            + "FROM search_projection "
                            + "WHERE search_vector @@ plainto_tsquery('english', ?) "
                            + (projectId != null ? "AND project_id = '" + projectId + "' " : "")
                            + "ORDER BY rank DESC LIMIT ?",
                    query, query, limit);
            return results.map(r -> {
                var p = map(r);
                Double rankVal = r.get("rank", Double.class);
                return new SearchProjection(p.assetId(), p.tenantId(), p.projectId(),
                        p.filename(), p.assetType(), p.transcriptText(),
                        p.sceneLabels(), p.objects(), p.brands(), p.people(),
                        p.classification(), p.license(), p.publishStatus(),
                        p.searchText(), rankVal != null ? rankVal.intValue() : 0);
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<SearchProjection> listByProject(String projectId, int limit) {
        return dsl.select().from(SEARCH_PROJECTION)
                .where(SEARCH_PROJECTION.PROJECT_ID.eq(projectId))
                .orderBy(SEARCH_PROJECTION.UPDATED_AT.desc())
                .limit(limit)
                .fetch().map(SearchProjectionRepository::map);
    }

    public void delete(String assetId) {
        dsl.deleteFrom(SEARCH_PROJECTION)
                .where(SEARCH_PROJECTION.ASSET_ID.eq(assetId)).execute();
    }

    private static SearchProjection map(Record r) {
        return new SearchProjection(
                r.get(SEARCH_PROJECTION.ASSET_ID),
                r.get(SEARCH_PROJECTION.TENANT_ID),
                r.get(SEARCH_PROJECTION.PROJECT_ID),
                r.get(SEARCH_PROJECTION.FILENAME),
                r.get(SEARCH_PROJECTION.ASSET_TYPE),
                r.get(SEARCH_PROJECTION.TRANSCRIPT_TEXT),
                parseList(r.get(SEARCH_PROJECTION.SCENE_LABELS)),
                parseList(r.get(SEARCH_PROJECTION.OBJECTS)),
                parseList(r.get(SEARCH_PROJECTION.BRANDS)),
                parseList(r.get(SEARCH_PROJECTION.PEOPLE)),
                r.get(SEARCH_PROJECTION.CLASSIFICATION),
                r.get(SEARCH_PROJECTION.LICENSE),
                r.get(SEARCH_PROJECTION.PUBLISH_STATUS),
                r.get(SEARCH_PROJECTION.SEARCH_TEXT), 0);
    }

    private static String toString(List<String> list) {
        return list != null && !list.isEmpty() ? String.join(",", list) : null;
    }

    private static List<String> parseList(String str) {
        if (str == null || str.isBlank()) return List.of();
        return Arrays.asList(str.split(","));
    }
}
