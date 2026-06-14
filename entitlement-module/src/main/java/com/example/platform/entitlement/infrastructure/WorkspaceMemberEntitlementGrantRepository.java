package com.example.platform.entitlement.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.entitlement.domain.WorkspaceMemberEntitlementGrant;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;

@Repository

public class WorkspaceMemberEntitlementGrantRepository {

    private final DSLContext dsl;

    public WorkspaceMemberEntitlementGrantRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(WorkspaceMemberEntitlementGrant grant) {
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(table("WORKSPACE_MEMBER_ENTITLEMENT_GRANT"))
                .columns(field("ID"), field("WORKSPACE_ID"), field("MEMBER_ID"),
                        field("FEATURE_KEY"), field("QUOTA_AMOUNT"),
                        field("STARTS_AT"), field("EXPIRES_AT"),
                        field("STATUS"), field("GRANTED_BY"),
                        field("CREATED_AT"), field("UPDATED_AT"))
                .values(grant.id(), grant.workspaceId(), grant.memberId(),
                        grant.featureKey(), grant.quotaAmount(),
                        OffsetDateTime.ofInstant(grant.startsAt(), ZoneOffset.UTC),
                        grant.expiresAt() != null ? OffsetDateTime.ofInstant(grant.expiresAt(), ZoneOffset.UTC) : null,
                        grant.status(), grant.grantedBy(),
                        now, now)
                .execute();
    }

    public Optional<WorkspaceMemberEntitlementGrant> findById(String id) {
        return dsl.select()
                .from(table("WORKSPACE_MEMBER_ENTITLEMENT_GRANT"))
                .where(field("ID").eq(id))
                .fetchOptional(this::mapRecord);
    }

    public List<WorkspaceMemberEntitlementGrant> findByWorkspaceId(String workspaceId) {
        return dsl.select()
                .from(table("WORKSPACE_MEMBER_ENTITLEMENT_GRANT"))
                .where(field("WORKSPACE_ID").eq(workspaceId))
                .orderBy(field("CREATED_AT").desc())
                .fetch(this::mapRecord);
    }

    public List<WorkspaceMemberEntitlementGrant> findActiveByMemberId(String workspaceId, String memberId) {
        OffsetDateTime now = OffsetDateTime.now();
        return dsl.select()
                .from(table("WORKSPACE_MEMBER_ENTITLEMENT_GRANT"))
                .where(field("WORKSPACE_ID").eq(workspaceId))
                .and(field("MEMBER_ID").eq(memberId))
                .and(field("STATUS").eq("ACTIVE"))
                .and(field("STARTS_AT").lessOrEqual(now))
                .and(field("EXPIRES_AT").greaterThan(now).or(field("EXPIRES_AT").isNull()))
                .orderBy(field("CREATED_AT").desc())
                .fetch(this::mapRecord);
    }

    public void updateStatus(String id, String status) {
        dsl.update(table("WORKSPACE_MEMBER_ENTITLEMENT_GRANT"))
                .set(field("STATUS"), status)
                .set(field("UPDATED_AT"), OffsetDateTime.now())
                .where(field("ID").eq(id))
                .execute();
    }

    public void updateExpiresAt(String id, Instant expiresAt) {
        dsl.update(table("WORKSPACE_MEMBER_ENTITLEMENT_GRANT"))
                .set(field("EXPIRES_AT"), expiresAt != null ? OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC) : null)
                .set(field("UPDATED_AT"), OffsetDateTime.now())
                .where(field("ID").eq(id))
                .execute();
    }

    private WorkspaceMemberEntitlementGrant mapRecord(Record r) {
        return new WorkspaceMemberEntitlementGrant(
                r.get(field("ID"), String.class),
                r.get(field("WORKSPACE_ID"), String.class),
                r.get(field("MEMBER_ID"), String.class),
                r.get(field("FEATURE_KEY"), String.class),
                r.get(field("QUOTA_AMOUNT"), Long.class),
                toInstant(r.get(field("STARTS_AT"), OffsetDateTime.class)),
                toInstant(r.get(field("EXPIRES_AT"), OffsetDateTime.class)),
                r.get(field("STATUS"), String.class),
                r.get(field("GRANTED_BY"), String.class),
                toInstant(r.get(field("CREATED_AT"), OffsetDateTime.class)),
                toInstant(r.get(field("UPDATED_AT"), OffsetDateTime.class))
        );
    }

    private Instant toInstant(OffsetDateTime odt) {
        return odt != null ? odt.toInstant() : null;
    }
}
