package com.example.platform.render.infrastructure.product;

import static org.jooq.impl.DSL.*;
import com.example.platform.render.domain.product.*;
import com.example.platform.shared.Ids;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT;
import org.jooq.impl.DSL;


@Repository
public class ProductRepository {

    private final DSLContext dsl;

    protected ProductRepository() { this.dsl = null; }

    @org.springframework.beans.factory.annotation.Autowired
    public ProductRepository(DSLContext dsl) { this.dsl = dsl; }

    public Product save(Product p) {
        var id = p.productId() != null ? p.productId() : Ids.newId("prod");
        var now = LocalDateTime.now();
        dsl.insertInto(PRODUCT)
                .columns(PRODUCT.PRODUCT_ID, PRODUCT.TENANT_ID, PRODUCT.PROJECT_ID,
                        PRODUCT.OWNER_ASSET_ID, PRODUCT.PRODUCT_TYPE, PRODUCT.REPRESENTATION_KIND,
                        PRODUCT.PRODUCER_TYPE, PRODUCT.PRODUCER_ID, PRODUCT.SOURCE_TIMELINE_REVISION_ID,
                        PRODUCT.STATUS, PRODUCT.STORAGE_REFERENCE_ID, PRODUCT.CHECKSUM,
                        PRODUCT.CONTENT_HASH, PRODUCT.MIME_TYPE, PRODUCT.VERSION,
                        PRODUCT.METADATA_JSON, PRODUCT.CREATED_AT, PRODUCT.UPDATED_AT)
                .values(id, p.tenantId(), p.projectId(), p.ownerAssetId(),
                        p.productType().name(), p.representationKind().name(),
                        p.producerType(), p.producerId(), p.sourceTimelineRevisionId(),
                        p.status().name(), p.storageReferenceId(), p.checksum(),
                        p.contentHash(), p.mimeType(), p.version(),
                        p.metadataJson(), now, now)
                .onConflict(PRODUCT.PRODUCT_ID).doUpdate()
                .set(PRODUCT.STATUS, p.status().name())
                .set(PRODUCT.UPDATED_AT, now)
                .execute();
        return findById(id).orElseThrow();
    }

    public Optional<Product> findById(String productId) {
        var r = dsl.select().from(PRODUCT).where(PRODUCT.PRODUCT_ID.eq(productId)).fetchOne();
        return r == null ? Optional.empty() : Optional.of(map(r));
    }

    public List<Product> findByProject(String projectId, int limit) {
        return dsl.select().from(PRODUCT)
                .where(PRODUCT.PROJECT_ID.eq(projectId))
                .orderBy(PRODUCT.CREATED_AT.desc()).limit(limit)
                .fetch().map(ProductRepository::map);
    }

    public List<Product> findByAsset(String assetId) {
        return dsl.select().from(PRODUCT)
                .where(PRODUCT.OWNER_ASSET_ID.eq(assetId))
                .orderBy(PRODUCT.CREATED_AT.desc())
                .fetch().map(ProductRepository::map);
    }

    public Optional<Product> findLatest(String assetId, ProductType type) {
        var r = dsl.select().from(PRODUCT)
                .where(PRODUCT.OWNER_ASSET_ID.eq(assetId).and(PRODUCT.PRODUCT_TYPE.eq(type.name())))
                .orderBy(PRODUCT.CREATED_AT.desc()).limit(1).fetchOne();
        return r == null ? Optional.empty() : Optional.of(map(r));
    }

    /**
     * Find products by source timeline revision ID.
     * Used for render deduplication — no DB migration needed (column exists).
     */
    public List<Product> findBySourceTimelineRevisionId(String timelineRevisionId) {
        return dsl.select().from(PRODUCT)
                .where(PRODUCT.SOURCE_TIMELINE_REVISION_ID.eq(timelineRevisionId))
                .orderBy(PRODUCT.CREATED_AT.desc())
                .fetch().map(ProductRepository::map);
    }

    private static Product map(Record r) {
        return new Product(
                r.get(PRODUCT.PRODUCT_ID), r.get(PRODUCT.TENANT_ID),
                r.get(PRODUCT.PROJECT_ID), r.get(PRODUCT.OWNER_ASSET_ID),
                e(ProductType.class, r.get(PRODUCT.PRODUCT_TYPE)),
                e(RepresentationKind.class, r.get(PRODUCT.REPRESENTATION_KIND)),
                r.get(PRODUCT.PRODUCER_TYPE), r.get(PRODUCT.PRODUCER_ID),
                r.get(PRODUCT.SOURCE_TIMELINE_REVISION_ID),
                e(ProductStatus.class, r.get(PRODUCT.STATUS)),
                r.get(PRODUCT.STORAGE_REFERENCE_ID), r.get(PRODUCT.CHECKSUM),
                r.get(PRODUCT.CONTENT_HASH), r.get(PRODUCT.MIME_TYPE),
                r.get(PRODUCT.VERSION), r.get(PRODUCT.METADATA_JSON),
                toInst(r.get(PRODUCT.CREATED_AT)),
                toInst(r.get(PRODUCT.UPDATED_AT)));
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
