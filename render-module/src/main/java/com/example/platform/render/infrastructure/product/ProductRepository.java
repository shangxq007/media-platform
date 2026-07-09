package com.example.platform.render.infrastructure.product;

import static org.jooq.impl.DSL.*;
import com.example.platform.render.domain.product.*;
import com.example.platform.shared.Ids;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {

    private final DSLContext dsl;

    protected ProductRepository() { this.dsl = null; }

    @org.springframework.beans.factory.annotation.Autowired
    public ProductRepository(DSLContext dsl) { this.dsl = dsl; }

    public Product save(Product p) {
        var id = p.productId() != null ? p.productId() : Ids.newId("prod");
        var now = OffsetDateTime.now();
        dsl.insertInto(table("product"))
                .columns(field("product_id"), field("tenant_id"), field("project_id"),
                        field("owner_asset_id"), field("product_type"), field("representation_kind"),
                        field("producer_type"), field("producer_id"), field("source_timeline_revision_id"),
                        field("status"), field("storage_reference_id"), field("checksum"),
                        field("content_hash"), field("mime_type"), field("version"),
                        field("metadata_json"), field("created_at"), field("updated_at"))
                .values(id, p.tenantId(), p.projectId(), p.ownerAssetId(),
                        p.productType().name(), p.representationKind().name(),
                        p.producerType(), p.producerId(), p.sourceTimelineRevisionId(),
                        p.status().name(), p.storageReferenceId(), p.checksum(),
                        p.contentHash(), p.mimeType(), p.version(),
                        p.metadataJson(), now, now)
                .onConflict(field("product_id")).doUpdate()
                .set(field("status"), p.status().name())
                .set(field("updated_at"), now)
                .execute();
        return findById(id).orElseThrow();
    }

    public Optional<Product> findById(String productId) {
        var r = dsl.select().from(table("product")).where(field("product_id").eq(productId)).fetchOne();
        return r == null ? Optional.empty() : Optional.of(map(r));
    }

    public List<Product> findByProject(String projectId, int limit) {
        return dsl.select().from(table("product"))
                .where(field("project_id").eq(projectId))
                .orderBy(field("created_at").desc()).limit(limit)
                .fetch().map(ProductRepository::map);
    }

    public List<Product> findByAsset(String assetId) {
        return dsl.select().from(table("product"))
                .where(field("owner_asset_id").eq(assetId))
                .orderBy(field("created_at").desc())
                .fetch().map(ProductRepository::map);
    }

    public Optional<Product> findLatest(String assetId, ProductType type) {
        var r = dsl.select().from(table("product"))
                .where(field("owner_asset_id").eq(assetId).and(field("product_type").eq(type.name())))
                .orderBy(field("created_at").desc()).limit(1).fetchOne();
        return r == null ? Optional.empty() : Optional.of(map(r));
    }

    /**
     * Find products by source timeline revision ID.
     * Used for render deduplication — no DB migration needed (column exists).
     */
    public List<Product> findBySourceTimelineRevisionId(String timelineRevisionId) {
        return dsl.select().from(table("product"))
                .where(field("source_timeline_revision_id").eq(timelineRevisionId))
                .orderBy(field("created_at").desc())
                .fetch().map(ProductRepository::map);
    }

    private static Product map(Record r) {
        return new Product(
                r.get(field("product_id", String.class)), r.get(field("tenant_id", String.class)),
                r.get(field("project_id", String.class)), r.get(field("owner_asset_id", String.class)),
                e(ProductType.class, r.get(field("product_type", String.class))),
                e(RepresentationKind.class, r.get(field("representation_kind", String.class))),
                r.get(field("producer_type", String.class)), r.get(field("producer_id", String.class)),
                r.get(field("source_timeline_revision_id", String.class)),
                e(ProductStatus.class, r.get(field("status", String.class))),
                r.get(field("storage_reference_id", String.class)), r.get(field("checksum", String.class)),
                r.get(field("content_hash", String.class)), r.get(field("mime_type", String.class)),
                r.get(field("version", Integer.class)), r.get(field("metadata_json", String.class)),
                toInst(r.get(field("created_at"))),
                toInst(r.get(field("updated_at"))));
    }

    private static Instant toInst(Object o) {
        if (o == null) return null;
        if (o instanceof OffsetDateTime odt) return odt.toInstant();
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (o instanceof Instant i) return i;
        return null;
    }
    private static <E extends Enum<E>> E e(Class<E> t, String v) { try { return Enum.valueOf(t, v); } catch (Exception ex) { return null; } }
}
