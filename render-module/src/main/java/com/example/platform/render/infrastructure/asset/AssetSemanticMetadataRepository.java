package com.example.platform.render.infrastructure.asset;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.AssetSemanticMetadata.ASSET_SEMANTIC_METADATA;


@Repository
public class AssetSemanticMetadataRepository {

    private final DSLContext dsl;

    public AssetSemanticMetadataRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(String assetId, String assetVersion, String status,
                       String language, String semanticJson) {
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(ASSET_SEMANTIC_METADATA)
                .columns(ASSET_SEMANTIC_METADATA.ASSET_ID, ASSET_SEMANTIC_METADATA.ASSET_VERSION, ASSET_SEMANTIC_METADATA.STATUS,
                        ASSET_SEMANTIC_METADATA.LANGUAGE, ASSET_SEMANTIC_METADATA.SEMANTIC_JSON,
                        ASSET_SEMANTIC_METADATA.CREATED_AT, ASSET_SEMANTIC_METADATA.UPDATED_AT)
                .values(assetId, assetVersion, status, language, semanticJson, now, now)
                .onConflict(ASSET_SEMANTIC_METADATA.ASSET_ID)
                .doUpdate()
                .set(ASSET_SEMANTIC_METADATA.ASSET_VERSION, assetVersion)
                .set(ASSET_SEMANTIC_METADATA.STATUS, status)
                .set(ASSET_SEMANTIC_METADATA.LANGUAGE, language)
                .set(ASSET_SEMANTIC_METADATA.SEMANTIC_JSON, semanticJson)
                .set(ASSET_SEMANTIC_METADATA.UPDATED_AT, now)
                .execute();
    }

    public void update(String assetId, String status, String semanticJson) {
        dsl.update(ASSET_SEMANTIC_METADATA)
                .set(ASSET_SEMANTIC_METADATA.STATUS, status)
                .set(ASSET_SEMANTIC_METADATA.SEMANTIC_JSON, semanticJson)
                .set(ASSET_SEMANTIC_METADATA.UPDATED_AT, LocalDateTime.now())
                .where(ASSET_SEMANTIC_METADATA.ASSET_ID.eq(assetId))
                .execute();
    }

    public Optional<SemanticRow> findById(String assetId) {
        Record r = dsl.select().from(ASSET_SEMANTIC_METADATA)
                .where(ASSET_SEMANTIC_METADATA.ASSET_ID.eq(assetId))
                .fetchOne();
        return r == null ? Optional.empty() : Optional.of(map(r));
    }

    public boolean exists(String assetId) {
        return dsl.fetchCount(ASSET_SEMANTIC_METADATA,
                ASSET_SEMANTIC_METADATA.ASSET_ID.eq(assetId)) > 0;
    }

    public void delete(String assetId) {
        dsl.deleteFrom(ASSET_SEMANTIC_METADATA)
                .where(ASSET_SEMANTIC_METADATA.ASSET_ID.eq(assetId))
                .execute();
    }

    private static SemanticRow map(Record r) {
        return new SemanticRow(
                r.get(ASSET_SEMANTIC_METADATA.ASSET_ID),
                r.get(ASSET_SEMANTIC_METADATA.ASSET_VERSION),
                r.get(ASSET_SEMANTIC_METADATA.STATUS),
                r.get(ASSET_SEMANTIC_METADATA.LANGUAGE),
                r.get(ASSET_SEMANTIC_METADATA.SEMANTIC_JSON),
                toOffsetDateTime(r.get(ASSET_SEMANTIC_METADATA.CREATED_AT)),
                toOffsetDateTime(r.get(ASSET_SEMANTIC_METADATA.UPDATED_AT)));
    }

    private static OffsetDateTime toOffsetDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime odt) {
            return odt;
        }
        if (value instanceof java.time.Instant instant) {
            return instant.atOffset(java.time.ZoneOffset.UTC);
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.atOffset(java.time.ZoneOffset.UTC);
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }

    public record SemanticRow(String assetId, String assetVersion, String status,
                                String language, String semanticJson,
                                OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
}
