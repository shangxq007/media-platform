package com.example.platform.entitlement.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.entitlement.domain.WorkspaceQuotaAllocation;
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
public class WorkspaceQuotaAllocationRepository {

    private final DSLContext dsl;

    public WorkspaceQuotaAllocationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(WorkspaceQuotaAllocation allocation) {
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(table("WORKSPACE_QUOTA_ALLOCATION"))
                .columns(field("ID"), field("WORKSPACE_ID"), field("MEMBER_ID"),
                        field("QUOTA_PROFILE_KEY"), field("ALLOCATED_AMOUNT"),
                        field("USED_AMOUNT"), field("PERIOD"),
                        field("CREATED_AT"), field("UPDATED_AT"))
                .values(allocation.id(), allocation.workspaceId(), allocation.memberId(),
                        allocation.quotaProfileKey(), allocation.allocatedAmount(),
                        allocation.usedAmount(), allocation.period(),
                        now, now)
                .execute();
    }

    public Optional<WorkspaceQuotaAllocation> findByWorkspaceAndMember(String workspaceId, String memberId) {
        return dsl.select()
                .from(table("WORKSPACE_QUOTA_ALLOCATION"))
                .where(field("WORKSPACE_ID").eq(workspaceId))
                .and(field("MEMBER_ID").eq(memberId))
                .fetchOptional(this::mapRecord);
    }

    public List<WorkspaceQuotaAllocation> findByWorkspaceId(String workspaceId) {
        return dsl.select()
                .from(table("WORKSPACE_QUOTA_ALLOCATION"))
                .where(field("WORKSPACE_ID").eq(workspaceId))
                .orderBy(field("CREATED_AT").desc())
                .fetch(this::mapRecord);
    }

    public void updateUsedAmount(String id, long usedAmount) {
        dsl.update(table("WORKSPACE_QUOTA_ALLOCATION"))
                .set(field("USED_AMOUNT"), usedAmount)
                .set(field("UPDATED_AT"), OffsetDateTime.now())
                .where(field("ID").eq(id))
                .execute();
    }

    public void updateAllocatedAmount(String id, long allocatedAmount) {
        dsl.update(table("WORKSPACE_QUOTA_ALLOCATION"))
                .set(field("ALLOCATED_AMOUNT"), allocatedAmount)
                .set(field("UPDATED_AT"), OffsetDateTime.now())
                .where(field("ID").eq(id))
                .execute();
    }

    private WorkspaceQuotaAllocation mapRecord(Record r) {
        return new WorkspaceQuotaAllocation(
                r.get(field("ID"), String.class),
                r.get(field("WORKSPACE_ID"), String.class),
                r.get(field("MEMBER_ID"), String.class),
                r.get(field("QUOTA_PROFILE_KEY"), String.class),
                r.get(field("ALLOCATED_AMOUNT"), Long.class),
                r.get(field("USED_AMOUNT"), Long.class),
                r.get(field("PERIOD"), String.class),
                toInstant(r.get(field("CREATED_AT"), OffsetDateTime.class)),
                toInstant(r.get(field("UPDATED_AT"), OffsetDateTime.class))
        );
    }

    private Instant toInstant(OffsetDateTime odt) {
        return odt != null ? odt.toInstant() : null;
    }
}
