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
public class ProductDependencyRepository {

    private final DSLContext dsl;

    protected ProductDependencyRepository() { this.dsl = null; }

    @org.springframework.beans.factory.annotation.Autowired
    public ProductDependencyRepository(DSLContext dsl) { this.dsl = dsl; }

    public ProductDependency save(ProductDependency dep) {
        var id = dep.dependencyId() != null ? dep.dependencyId() : Ids.newId("pdep");
        var now = OffsetDateTime.now();
        dsl.insertInto(table("product_dependency"))
                .columns(field("dependency_id"), field("tenant_id"), field("project_id"),
                        field("product_id"), field("depends_on_product_id"),
                        field("dependency_type"), field("created_at"))
                .values(id, dep.tenantId(), dep.projectId(), dep.productId(),
                        dep.dependsOnProductId(), dep.dependencyType().name(), now)
                .onConflict(field("product_id"), field("depends_on_product_id"), field("dependency_type"))
                .doNothing().execute();
        return new ProductDependency(id, dep.tenantId(), dep.projectId(), dep.productId(),
                dep.dependsOnProductId(), dep.dependencyType(), now.toInstant());
    }

    public List<ProductDependency> findDependencies(String productId) {
        return dsl.select().from(table("product_dependency"))
                .where(field("product_id").eq(productId)).fetch().map(ProductDependencyRepository::map);
    }

    public List<ProductDependency> findDependents(String productId) {
        return dsl.select().from(table("product_dependency"))
                .where(field("depends_on_product_id").eq(productId)).fetch().map(ProductDependencyRepository::map);
    }

    public boolean exists(String productId, String dependsOnId) {
        return dsl.fetchCount(table("product_dependency"),
                field("product_id").eq(productId).and(field("depends_on_product_id").eq(dependsOnId))) > 0;
    }

    public void delete(String depId) {
        dsl.deleteFrom(table("product_dependency")).where(field("dependency_id").eq(depId)).execute();
    }

    private static ProductDependency map(Record r) {
        return new ProductDependency(
                r.get(field("dependency_id", String.class)), r.get(field("tenant_id", String.class)),
                r.get(field("project_id", String.class)), r.get(field("product_id", String.class)),
                r.get(field("depends_on_product_id", String.class)),
                e(DependencyType.class, r.get(field("dependency_type", String.class))),
                toInst(r.get(field("created_at", OffsetDateTime.class))));
    }

    private static Instant toInst(OffsetDateTime o) { return o != null ? o.toInstant() : null; }
    private static <E extends Enum<E>> E e(Class<E> t, String v) { try { return Enum.valueOf(t, v); } catch (Exception ex) { return null; } }
}
