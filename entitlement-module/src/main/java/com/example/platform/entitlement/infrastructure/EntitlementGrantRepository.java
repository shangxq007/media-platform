package com.example.platform.entitlement.infrastructure;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.EntitlementGrant.ENTITLEMENT_GRANT;


/**
 * Persistence repository for entitlement grants.
 *
 * <p>Only created when a {@link DSLContext} bean is available.
 * Falls back to in-memory storage when not available.</p>
 */
@Repository

public class EntitlementGrantRepository {

    private final DSLContext dsl;

    public EntitlementGrantRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(String id, String subjectType, String subjectId, String bundleCode,
                     String quotaProfileCode, String sourceType, String sourceRef,
                     String grantStatus, Instant effectiveAt, Instant expiresAt) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effAt = effectiveAt != null
                ? LocalDateTime.ofInstant(effectiveAt, ZoneOffset.UTC) : now;
        LocalDateTime expAt = expiresAt != null
                ? LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC) : null;
        int updated = dsl.update(ENTITLEMENT_GRANT)
                .set(ENTITLEMENT_GRANT.SUBJECT_TYPE, subjectType)
                .set(ENTITLEMENT_GRANT.SUBJECT_ID, subjectId)
                .set(ENTITLEMENT_GRANT.BUNDLE_CODE, bundleCode)
                .set(ENTITLEMENT_GRANT.QUOTA_PROFILE_CODE, quotaProfileCode)
                .set(ENTITLEMENT_GRANT.SOURCE_TYPE, sourceType)
                .set(ENTITLEMENT_GRANT.SOURCE_REF, sourceRef)
                .set(ENTITLEMENT_GRANT.GRANT_STATUS, grantStatus)
                .set(ENTITLEMENT_GRANT.EFFECTIVE_AT, effAt)
                .set(ENTITLEMENT_GRANT.EXPIRES_AT, expAt)
                .where(ENTITLEMENT_GRANT.ID.eq(id))
                .execute();
        if (updated == 0) {
            dsl.insertInto(ENTITLEMENT_GRANT)
                    .columns(ENTITLEMENT_GRANT.ID, ENTITLEMENT_GRANT.SUBJECT_TYPE, ENTITLEMENT_GRANT.SUBJECT_ID,
                            ENTITLEMENT_GRANT.BUNDLE_CODE, ENTITLEMENT_GRANT.QUOTA_PROFILE_CODE,
                            ENTITLEMENT_GRANT.SOURCE_TYPE, ENTITLEMENT_GRANT.SOURCE_REF,
                            ENTITLEMENT_GRANT.GRANT_STATUS, ENTITLEMENT_GRANT.EFFECTIVE_AT, ENTITLEMENT_GRANT.EXPIRES_AT)
                    .values(id, subjectType, subjectId, bundleCode, quotaProfileCode,
                            sourceType, sourceRef, grantStatus, effAt, expAt)
                    .execute();
        }
    }

    public List<EntitlementGrantRecord> findAllActive() {
        return dsl.select()
                .from(ENTITLEMENT_GRANT)
                .where(ENTITLEMENT_GRANT.GRANT_STATUS.eq("ACTIVE"))
                .orderBy(ENTITLEMENT_GRANT.EFFECTIVE_AT.desc())
                .fetch(this::mapRecord);
    }

    public List<EntitlementGrantRecord> findBySubjectId(String subjectId) {
        return dsl.select()
                .from(ENTITLEMENT_GRANT)
                .where(ENTITLEMENT_GRANT.SUBJECT_ID.eq(subjectId))
                .orderBy(ENTITLEMENT_GRANT.EFFECTIVE_AT.desc())
                .fetch(this::mapRecord);
    }

    public List<EntitlementGrantRecord> findActiveBySubjectId(String subjectId) {
        return dsl.select()
                .from(ENTITLEMENT_GRANT)
                .where(ENTITLEMENT_GRANT.SUBJECT_ID.eq(subjectId))
                .and(ENTITLEMENT_GRANT.GRANT_STATUS.eq("ACTIVE"))
                .and(ENTITLEMENT_GRANT.EXPIRES_AT.greaterThan(LocalDateTime.now())
                        .or(ENTITLEMENT_GRANT.EXPIRES_AT.isNull()))
                .orderBy(ENTITLEMENT_GRANT.EFFECTIVE_AT.desc())
                .fetch(this::mapRecord);
    }

    private EntitlementGrantRecord mapRecord(Record r) {
        LocalDateTime effAt = r.get(ENTITLEMENT_GRANT.EFFECTIVE_AT, LocalDateTime.class);
        LocalDateTime expAt = r.get(ENTITLEMENT_GRANT.EXPIRES_AT, LocalDateTime.class);
        return new EntitlementGrantRecord(
                r.get(ENTITLEMENT_GRANT.ID, String.class),
                r.get(ENTITLEMENT_GRANT.SUBJECT_TYPE, String.class),
                r.get(ENTITLEMENT_GRANT.SUBJECT_ID, String.class),
                r.get(ENTITLEMENT_GRANT.BUNDLE_CODE, String.class),
                r.get(ENTITLEMENT_GRANT.QUOTA_PROFILE_CODE, String.class),
                r.get(ENTITLEMENT_GRANT.SOURCE_TYPE, String.class),
                r.get(ENTITLEMENT_GRANT.SOURCE_REF, String.class),
                r.get(ENTITLEMENT_GRANT.GRANT_STATUS, String.class),
                effAt != null ? effAt.toInstant(java.time.ZoneOffset.UTC) : null,
                expAt != null ? expAt.toInstant(java.time.ZoneOffset.UTC) : null
        );
    }

    /**
     * Flat record for entitlement grant data from the database.
     */
    public record EntitlementGrantRecord(
            String id,
            String subjectType,
            String subjectId,
            String bundleCode,
            String quotaProfileCode,
            String sourceType,
            String sourceRef,
            String grantStatus,
            Instant effectiveAt,
            Instant expiresAt
    ) {}
}
