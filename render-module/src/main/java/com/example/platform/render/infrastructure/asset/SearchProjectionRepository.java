package com.example.platform.render.infrastructure.asset;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.render.domain.asset.search.SearchProjection;
import java.time.OffsetDateTime;
import java.util.*;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class SearchProjectionRepository {

    private final DSLContext dsl;

    public SearchProjectionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void upsert(SearchProjection proj) {
        OffsetDateTime now = OffsetDateTime.now();
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
        Record r = dsl.select().from(table("search_projection"))
                .where(field("asset_id").eq(assetId)).fetchOne();
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
        return dsl.select().from(table("search_projection"))
                .where(field("project_id").eq(projectId))
                .orderBy(field("updated_at").desc())
                .limit(limit)
                .fetch().map(SearchProjectionRepository::map);
    }

    public void delete(String assetId) {
        dsl.deleteFrom(table("search_projection"))
                .where(field("asset_id").eq(assetId)).execute();
    }

    private static SearchProjection map(Record r) {
        return new SearchProjection(
                r.get(field("asset_id", String.class)),
                r.get(field("tenant_id", String.class)),
                r.get(field("project_id", String.class)),
                r.get(field("filename", String.class)),
                r.get(field("asset_type", String.class)),
                r.get(field("transcript_text", String.class)),
                parseList(r.get(field("scene_labels", String.class))),
                parseList(r.get(field("objects", String.class))),
                parseList(r.get(field("brands", String.class))),
                parseList(r.get(field("people", String.class))),
                r.get(field("classification", String.class)),
                r.get(field("license", String.class)),
                r.get(field("publish_status", String.class)),
                r.get(field("search_text", String.class)), 0);
    }

    private static String toString(List<String> list) {
        return list != null && !list.isEmpty() ? String.join(",", list) : null;
    }

    private static List<String> parseList(String str) {
        if (str == null || str.isBlank()) return List.of();
        return Arrays.asList(str.split(","));
    }
}
