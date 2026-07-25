package com.example.platform.render.infrastructure.product;

import static org.jooq.impl.DSL.*;
import com.example.platform.render.domain.product.*;
import com.example.platform.shared.Ids;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.ProductDependency.PRODUCT_DEPENDENCY;
import org.jooq.impl.DSL;


@Repository
public class ProductDependencyRepository {

    private final DSLContext dsl;

    protected ProductDependencyRepository() { this.dsl = null; }

    @org.springframework.beans.factory.annotation.Autowired
    public ProductDependencyRepository(DSLContext dsl) { this.dsl = dsl; }

    public ProductDependency save(ProductDependency dep) {
        var id = dep.dependencyId() != null ? dep.dependencyId() : Ids.newId("pdep");
        var now = LocalDateTime.now();
        dsl.insertInto(PRODUCT_DEPENDENCY)
                .columns(PRODUCT_DEPENDENCY.DEPENDENCY_ID, PRODUCT_DEPENDENCY.TENANT_ID, PRODUCT_DEPENDENCY.PROJECT_ID,
                        PRODUCT_DEPENDENCY.PRODUCT_ID, PRODUCT_DEPENDENCY.DEPENDS_ON_PRODUCT_ID,
                        PRODUCT_DEPENDENCY.DEPENDENCY_TYPE, PRODUCT_DEPENDENCY.CREATED_AT)
                .values(id, dep.tenantId(), dep.projectId(), dep.productId(),
                        dep.dependsOnProductId(), dep.dependencyType().name(), now)
                .onConflict(PRODUCT_DEPENDENCY.PRODUCT_ID, PRODUCT_DEPENDENCY.DEPENDS_ON_PRODUCT_ID, PRODUCT_DEPENDENCY.DEPENDENCY_TYPE)
                .doNothing().execute();
        return new ProductDependency(id, dep.tenantId(), dep.projectId(), dep.productId(),
                dep.dependsOnProductId(), dep.dependencyType(), now.toInstant(ZoneOffset.UTC));
    }

    public List<ProductDependency> findDependencies(String productId) {
        return dsl.select().from(PRODUCT_DEPENDENCY)
                .where(PRODUCT_DEPENDENCY.PRODUCT_ID.eq(productId)).fetch().map(ProductDependencyRepository::map);
    }

    public List<ProductDependency> findDependents(String productId) {
        return dsl.select().from(PRODUCT_DEPENDENCY)
                .where(PRODUCT_DEPENDENCY.DEPENDS_ON_PRODUCT_ID.eq(productId)).fetch().map(ProductDependencyRepository::map);
    }

    public boolean exists(String productId, String dependsOnId) {
        return dsl.fetchCount(PRODUCT_DEPENDENCY,
                PRODUCT_DEPENDENCY.PRODUCT_ID.eq(productId).and(PRODUCT_DEPENDENCY.DEPENDS_ON_PRODUCT_ID.eq(dependsOnId))) > 0;
    }

    public void delete(String depId) {
        dsl.deleteFrom(PRODUCT_DEPENDENCY).where(PRODUCT_DEPENDENCY.DEPENDENCY_ID.eq(depId)).execute();
    }

    private static ProductDependency map(Record r) {
        return new ProductDependency(
                r.get(PRODUCT_DEPENDENCY.DEPENDENCY_ID), r.get(PRODUCT_DEPENDENCY.TENANT_ID),
                r.get(PRODUCT_DEPENDENCY.PROJECT_ID), r.get(PRODUCT_DEPENDENCY.PRODUCT_ID),
                r.get(PRODUCT_DEPENDENCY.DEPENDS_ON_PRODUCT_ID),
                e(DependencyType.class, r.get(PRODUCT_DEPENDENCY.DEPENDENCY_TYPE)),
                toInst(r.get(PRODUCT_DEPENDENCY.CREATED_AT)));
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
