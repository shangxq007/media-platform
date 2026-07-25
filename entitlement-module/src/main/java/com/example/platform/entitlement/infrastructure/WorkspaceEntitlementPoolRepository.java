package com.example.platform.entitlement.infrastructure;

import com.example.platform.entitlement.domain.WorkspaceEntitlementPool;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.WorkspaceEntitlementPool.WORKSPACE_ENTITLEMENT_POOL;


@Repository

public class WorkspaceEntitlementPoolRepository {

    private final DSLContext dsl;

    public WorkspaceEntitlementPoolRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(WorkspaceEntitlementPool pool) {
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(WORKSPACE_ENTITLEMENT_POOL)
                .columns(WORKSPACE_ENTITLEMENT_POOL.ID, WORKSPACE_ENTITLEMENT_POOL.WORKSPACE_ID, WORKSPACE_ENTITLEMENT_POOL.FEATURE_KEY,
                        WORKSPACE_ENTITLEMENT_POOL.TOTAL_QUOTA, WORKSPACE_ENTITLEMENT_POOL.USED_QUOTA, WORKSPACE_ENTITLEMENT_POOL.PERIOD,
                        WORKSPACE_ENTITLEMENT_POOL.CREATED_AT, WORKSPACE_ENTITLEMENT_POOL.UPDATED_AT)
                .values(pool.id(), pool.workspaceId(), pool.featureKey(),
                        pool.totalQuota(), pool.usedQuota(), pool.period(),
                        now, now)
                .execute();
    }

    public Optional<WorkspaceEntitlementPool> findByWorkspaceAndFeature(String workspaceId, String featureKey) {
        return dsl.select()
                .from(WORKSPACE_ENTITLEMENT_POOL)
                .where(WORKSPACE_ENTITLEMENT_POOL.WORKSPACE_ID.eq(workspaceId))
                .and(WORKSPACE_ENTITLEMENT_POOL.FEATURE_KEY.eq(featureKey))
                .fetchOptional(this::mapRecord);
    }

    public List<WorkspaceEntitlementPool> findByWorkspaceId(String workspaceId) {
        return dsl.select()
                .from(WORKSPACE_ENTITLEMENT_POOL)
                .where(WORKSPACE_ENTITLEMENT_POOL.WORKSPACE_ID.eq(workspaceId))
                .orderBy(WORKSPACE_ENTITLEMENT_POOL.FEATURE_KEY.asc())
                .fetch(this::mapRecord);
    }

    public void updateUsage(String id, long usedQuota) {
        dsl.update(WORKSPACE_ENTITLEMENT_POOL)
                .set(WORKSPACE_ENTITLEMENT_POOL.USED_QUOTA, usedQuota)
                .set(WORKSPACE_ENTITLEMENT_POOL.UPDATED_AT, LocalDateTime.now())
                .where(WORKSPACE_ENTITLEMENT_POOL.ID.eq(id))
                .execute();
    }

    public void updateTotal(String id, long totalQuota) {
        dsl.update(WORKSPACE_ENTITLEMENT_POOL)
                .set(WORKSPACE_ENTITLEMENT_POOL.TOTAL_QUOTA, totalQuota)
                .set(WORKSPACE_ENTITLEMENT_POOL.UPDATED_AT, LocalDateTime.now())
                .where(WORKSPACE_ENTITLEMENT_POOL.ID.eq(id))
                .execute();
    }

    private WorkspaceEntitlementPool mapRecord(Record r) {
        return new WorkspaceEntitlementPool(
                r.get(WORKSPACE_ENTITLEMENT_POOL.ID, String.class),
                r.get(WORKSPACE_ENTITLEMENT_POOL.WORKSPACE_ID, String.class),
                r.get(WORKSPACE_ENTITLEMENT_POOL.FEATURE_KEY, String.class),
                r.get(WORKSPACE_ENTITLEMENT_POOL.TOTAL_QUOTA, Long.class),
                r.get(WORKSPACE_ENTITLEMENT_POOL.USED_QUOTA, Long.class),
                r.get(WORKSPACE_ENTITLEMENT_POOL.PERIOD, String.class),
                toInstant(r.get(WORKSPACE_ENTITLEMENT_POOL.CREATED_AT, LocalDateTime.class)),
                toInstant(r.get(WORKSPACE_ENTITLEMENT_POOL.UPDATED_AT, LocalDateTime.class))
        );
    }

    private Instant toInstant(LocalDateTime odt) {
        return odt != null ? odt.toInstant(java.time.ZoneOffset.UTC) : null;
    }
}
