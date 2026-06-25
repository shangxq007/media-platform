package com.example.platform.render.infrastructure.asset;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class AssetSemanticMetadataRepository {

    private final DSLContext dsl;

    public AssetSemanticMetadataRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(String assetId, String assetVersion, String status,
                       String language, String semanticJson) {
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(table("asset_semantic_metadata"))
                .columns(field("asset_id"), field("asset_version"), field("status"),
                        field("language"), field("semantic_json"),
                        field("created_at"), field("updated_at"))
                .values(assetId, assetVersion, status, language, semanticJson, now, now)
                .onConflict(field("asset_id"))
                .doUpdate()
                .set(field("asset_version"), assetVersion)
                .set(field("status"), status)
                .set(field("language"), language)
                .set(field("semantic_json"), semanticJson)
                .set(field("updated_at"), now)
                .execute();
    }

    public void update(String assetId, String status, String semanticJson) {
        dsl.update(table("asset_semantic_metadata"))
                .set(field("status"), status)
                .set(field("semantic_json"), semanticJson)
                .set(field("updated_at"), OffsetDateTime.now())
                .where(field("asset_id").eq(assetId))
                .execute();
    }

    public Optional<SemanticRow> findById(String assetId) {
        Record r = dsl.select().from(table("asset_semantic_metadata"))
                .where(field("asset_id").eq(assetId))
                .fetchOne();
        return r == null ? Optional.empty() : Optional.of(map(r));
    }

    public boolean exists(String assetId) {
        return dsl.fetchCount(table("asset_semantic_metadata"),
                field("asset_id").eq(assetId)) > 0;
    }

    public void delete(String assetId) {
        dsl.deleteFrom(table("asset_semantic_metadata"))
                .where(field("asset_id").eq(assetId))
                .execute();
    }

    private static SemanticRow map(Record r) {
        return new SemanticRow(
                r.get(field("asset_id", String.class)),
                r.get(field("asset_version", String.class)),
                r.get(field("status", String.class)),
                r.get(field("language", String.class)),
                r.get(field("semantic_json", String.class)),
                r.get(field("created_at", OffsetDateTime.class)),
                r.get(field("updated_at", OffsetDateTime.class)));
    }

    public record SemanticRow(String assetId, String assetVersion, String status,
                                String language, String semanticJson,
                                OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
}
