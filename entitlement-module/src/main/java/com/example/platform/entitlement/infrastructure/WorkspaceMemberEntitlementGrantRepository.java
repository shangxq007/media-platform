package com.example.platform.entitlement.infrastructure;

import com.example.platform.entitlement.domain.WorkspaceMemberEntitlementGrant;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.WorkspaceMemberEntitlementGrant.WORKSPACE_MEMBER_ENTITLEMENT_GRANT;


@Repository

public class WorkspaceMemberEntitlementGrantRepository {

    private final DSLContext dsl;

    public WorkspaceMemberEntitlementGrantRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(WorkspaceMemberEntitlementGrant grant) {
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(WORKSPACE_MEMBER_ENTITLEMENT_GRANT)
                .columns(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.ID, WORKSPACE_MEMBER_ENTITLEMENT_GRANT.WORKSPACE_ID, WORKSPACE_MEMBER_ENTITLEMENT_GRANT.MEMBER_ID,
                        WORKSPACE_MEMBER_ENTITLEMENT_GRANT.FEATURE_KEY, WORKSPACE_MEMBER_ENTITLEMENT_GRANT.QUOTA_AMOUNT,
                        WORKSPACE_MEMBER_ENTITLEMENT_GRANT.STARTS_AT, WORKSPACE_MEMBER_ENTITLEMENT_GRANT.EXPIRES_AT,
                        WORKSPACE_MEMBER_ENTITLEMENT_GRANT.STATUS, WORKSPACE_MEMBER_ENTITLEMENT_GRANT.GRANTED_BY,
                        WORKSPACE_MEMBER_ENTITLEMENT_GRANT.CREATED_AT, WORKSPACE_MEMBER_ENTITLEMENT_GRANT.UPDATED_AT)
                .values(grant.id(), grant.workspaceId(), grant.memberId(),
                        grant.featureKey(), grant.quotaAmount(),
                        LocalDateTime.ofInstant(grant.startsAt(), ZoneOffset.UTC),
                        grant.expiresAt() != null ? LocalDateTime.ofInstant(grant.expiresAt(), ZoneOffset.UTC) : null,
                        grant.status(), grant.grantedBy(),
                        now, now)
                .execute();
    }

    public Optional<WorkspaceMemberEntitlementGrant> findById(String id) {
        return dsl.select()
                .from(WORKSPACE_MEMBER_ENTITLEMENT_GRANT)
                .where(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.ID.eq(id))
                .fetchOptional(this::mapRecord);
    }

    public List<WorkspaceMemberEntitlementGrant> findByWorkspaceId(String workspaceId) {
        return dsl.select()
                .from(WORKSPACE_MEMBER_ENTITLEMENT_GRANT)
                .where(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.WORKSPACE_ID.eq(workspaceId))
                .orderBy(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    public List<WorkspaceMemberEntitlementGrant> findActiveByMemberId(String workspaceId, String memberId) {
        LocalDateTime now = LocalDateTime.now();
        return dsl.select()
                .from(WORKSPACE_MEMBER_ENTITLEMENT_GRANT)
                .where(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.WORKSPACE_ID.eq(workspaceId))
                .and(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.MEMBER_ID.eq(memberId))
                .and(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.STATUS.eq("ACTIVE"))
                .and(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.STARTS_AT.lessOrEqual(now))
                .and(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.EXPIRES_AT.greaterThan(now).or(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.EXPIRES_AT.isNull()))
                .orderBy(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    public void updateStatus(String id, String status) {
        dsl.update(WORKSPACE_MEMBER_ENTITLEMENT_GRANT)
                .set(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.STATUS, status)
                .set(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.UPDATED_AT, LocalDateTime.now())
                .where(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.ID.eq(id))
                .execute();
    }

    public void updateExpiresAt(String id, Instant expiresAt) {
        dsl.update(WORKSPACE_MEMBER_ENTITLEMENT_GRANT)
                .set(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.EXPIRES_AT, expiresAt != null ? LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC) : null)
                .set(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.UPDATED_AT, LocalDateTime.now())
                .where(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.ID.eq(id))
                .execute();
    }

    private WorkspaceMemberEntitlementGrant mapRecord(Record r) {
        return new WorkspaceMemberEntitlementGrant(
                r.get(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.ID, String.class),
                r.get(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.WORKSPACE_ID, String.class),
                r.get(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.MEMBER_ID, String.class),
                r.get(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.FEATURE_KEY, String.class),
                r.get(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.QUOTA_AMOUNT, Long.class),
                toInstant(r.get(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.STARTS_AT, LocalDateTime.class)),
                toInstant(r.get(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.EXPIRES_AT, LocalDateTime.class)),
                r.get(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.STATUS, String.class),
                r.get(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.GRANTED_BY, String.class),
                toInstant(r.get(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.CREATED_AT, LocalDateTime.class)),
                toInstant(r.get(WORKSPACE_MEMBER_ENTITLEMENT_GRANT.UPDATED_AT, LocalDateTime.class))
        );
    }

    private Instant toInstant(LocalDateTime odt) {
        return odt != null ? odt.toInstant(java.time.ZoneOffset.UTC) : null;
    }
}
