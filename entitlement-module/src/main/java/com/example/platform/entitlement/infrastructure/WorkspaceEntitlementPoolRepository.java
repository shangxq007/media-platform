package com.example.platform.entitlement.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.entitlement.domain.WorkspaceEntitlementPool;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(DSLContext.class)
public class WorkspaceEntitlementPoolRepository {

    private final DSLContext dsl;

    public WorkspaceEntitlementPoolRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(WorkspaceEntitlementPool pool) {
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(table("WORKSPACE_ENTITLEMENT_POOL"))
                .columns(field("ID"), field("WORKSPACE_ID"), field("FEATURE_KEY"),
                        field("TOTAL_QUOTA"), field("USED_QUOTA"), field("PERIOD"),
                        field("CREATED_AT"), field("UPDATED_AT"))
                .values(pool.id(), pool.workspaceId(), pool.featureKey(),
                        pool.totalQuota(), pool.usedQuota(), pool.period(),
                        now, now)
                .execute();
    }

    public Optional<WorkspaceEntitlementPool> findByWorkspaceAndFeature(String workspaceId, String featureKey) {
        return dsl.select()
                .from(table("WORKSPACE_ENTITLEMENT_POOL"))
                .where(field("WORKSPACE_ID").eq(workspaceId))
                .and(field("FEATURE_KEY").eq(featureKey))
                .fetchOptional(this::mapRecord);
    }

    public List<WorkspaceEntitlementPool> findByWorkspaceId(String workspaceId) {
        return dsl.select()
                .from(table("WORKSPACE_ENTITLEMENT_POOL"))
                .where(field("WORKSPACE_ID").eq(workspaceId))
                .orderBy(field("FEATURE_KEY").asc())
                .fetch(this::mapRecord);
    }

    public void updateUsage(String id, long usedQuota) {
        dsl.update(table("WORKSPACE_ENTITLEMENT_POOL"))
                .set(field("USED_QUOTA"), usedQuota)
                .set(field("UPDATED_AT"), OffsetDateTime.now())
                .where(field("ID").eq(id))
                .execute();
    }

    public void updateTotal(String id, long totalQuota) {
        dsl.update(table("WORKSPACE_ENTITLEMENT_POOL"))
                .set(field("TOTAL_QUOTA"), totalQuota)
                .set(field("UPDATED_AT"), OffsetDateTime.now())
                .where(field("ID").eq(id))
                .execute();
    }

    private WorkspaceEntitlementPool mapRecord(Record r) {
        return new WorkspaceEntitlementPool(
                r.get(field("ID"), String.class),
                r.get(field("WORKSPACE_ID"), String.class),
                r.get(field("FEATURE_KEY"), String.class),
                r.get(field("TOTAL_QUOTA"), Long.class),
                r.get(field("USED_QUOTA"), Long.class),
                r.get(field("PERIOD"), String.class),
                toInstant(r.get(field("CREATED_AT"), OffsetDateTime.class)),
                toInstant(r.get(field("UPDATED_AT"), OffsetDateTime.class))
        );
    }

    private Instant toInstant(OffsetDateTime odt) {
        return odt != null ? odt.toInstant() : null;
    }
}
