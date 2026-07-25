package com.example.platform.entitlement.infrastructure;

import com.example.platform.entitlement.domain.WorkspaceQuotaAllocation;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.WorkspaceQuotaAllocation.WORKSPACE_QUOTA_ALLOCATION;


@Repository

public class WorkspaceQuotaAllocationRepository {

    private final DSLContext dsl;

    public WorkspaceQuotaAllocationRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(WorkspaceQuotaAllocation allocation) {
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(WORKSPACE_QUOTA_ALLOCATION)
                .columns(WORKSPACE_QUOTA_ALLOCATION.ID, WORKSPACE_QUOTA_ALLOCATION.WORKSPACE_ID, WORKSPACE_QUOTA_ALLOCATION.MEMBER_ID,
                        WORKSPACE_QUOTA_ALLOCATION.QUOTA_PROFILE_KEY, WORKSPACE_QUOTA_ALLOCATION.ALLOCATED_AMOUNT,
                        WORKSPACE_QUOTA_ALLOCATION.USED_AMOUNT, WORKSPACE_QUOTA_ALLOCATION.PERIOD,
                        WORKSPACE_QUOTA_ALLOCATION.CREATED_AT, WORKSPACE_QUOTA_ALLOCATION.UPDATED_AT)
                .values(allocation.id(), allocation.workspaceId(), allocation.memberId(),
                        allocation.quotaProfileKey(), allocation.allocatedAmount(),
                        allocation.usedAmount(), allocation.period(),
                        now, now)
                .execute();
    }

    public Optional<WorkspaceQuotaAllocation> findByWorkspaceAndMember(String workspaceId, String memberId) {
        return dsl.select()
                .from(WORKSPACE_QUOTA_ALLOCATION)
                .where(WORKSPACE_QUOTA_ALLOCATION.WORKSPACE_ID.eq(workspaceId))
                .and(WORKSPACE_QUOTA_ALLOCATION.MEMBER_ID.eq(memberId))
                .fetchOptional(this::mapRecord);
    }

    public List<WorkspaceQuotaAllocation> findByWorkspaceId(String workspaceId) {
        return dsl.select()
                .from(WORKSPACE_QUOTA_ALLOCATION)
                .where(WORKSPACE_QUOTA_ALLOCATION.WORKSPACE_ID.eq(workspaceId))
                .orderBy(WORKSPACE_QUOTA_ALLOCATION.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    public void updateUsedAmount(String id, long usedAmount) {
        dsl.update(WORKSPACE_QUOTA_ALLOCATION)
                .set(WORKSPACE_QUOTA_ALLOCATION.USED_AMOUNT, usedAmount)
                .set(WORKSPACE_QUOTA_ALLOCATION.UPDATED_AT, LocalDateTime.now())
                .where(WORKSPACE_QUOTA_ALLOCATION.ID.eq(id))
                .execute();
    }

    public void updateAllocatedAmount(String id, long allocatedAmount) {
        dsl.update(WORKSPACE_QUOTA_ALLOCATION)
                .set(WORKSPACE_QUOTA_ALLOCATION.ALLOCATED_AMOUNT, allocatedAmount)
                .set(WORKSPACE_QUOTA_ALLOCATION.UPDATED_AT, LocalDateTime.now())
                .where(WORKSPACE_QUOTA_ALLOCATION.ID.eq(id))
                .execute();
    }

    private WorkspaceQuotaAllocation mapRecord(Record r) {
        return new WorkspaceQuotaAllocation(
                r.get(WORKSPACE_QUOTA_ALLOCATION.ID, String.class),
                r.get(WORKSPACE_QUOTA_ALLOCATION.WORKSPACE_ID, String.class),
                r.get(WORKSPACE_QUOTA_ALLOCATION.MEMBER_ID, String.class),
                r.get(WORKSPACE_QUOTA_ALLOCATION.QUOTA_PROFILE_KEY, String.class),
                r.get(WORKSPACE_QUOTA_ALLOCATION.ALLOCATED_AMOUNT, Long.class),
                r.get(WORKSPACE_QUOTA_ALLOCATION.USED_AMOUNT, Long.class),
                r.get(WORKSPACE_QUOTA_ALLOCATION.PERIOD, String.class),
                toInstant(r.get(WORKSPACE_QUOTA_ALLOCATION.CREATED_AT, LocalDateTime.class)),
                toInstant(r.get(WORKSPACE_QUOTA_ALLOCATION.UPDATED_AT, LocalDateTime.class))
        );
    }

    private Instant toInstant(LocalDateTime odt) {
        return odt != null ? odt.toInstant(java.time.ZoneOffset.UTC) : null;
    }
}
