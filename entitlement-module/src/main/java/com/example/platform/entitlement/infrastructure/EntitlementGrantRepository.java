package com.example.platform.entitlement.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

/**
 * Persistence repository for entitlement grants.
 *
 * <p>Only created when a {@link DSLContext} bean is available.
 * Falls back to in-memory storage when not available.</p>
 */
@Repository
@ConditionalOnBean(DSLContext.class)
public class EntitlementGrantRepository {

    private final DSLContext dsl;

    public EntitlementGrantRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(String id, String subjectType, String subjectId, String bundleCode,
                     String quotaProfileCode, String sourceType, String sourceRef,
                     String grantStatus, Instant effectiveAt, Instant expiresAt) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime effAt = effectiveAt != null
                ? OffsetDateTime.ofInstant(effectiveAt, ZoneOffset.UTC) : now;
        OffsetDateTime expAt = expiresAt != null
                ? OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC) : null;
        dsl.insertInto(table("ENTITLEMENT_GRANT"))
                .columns(field("ID"), field("SUBJECT_TYPE"), field("SUBJECT_ID"),
                        field("BUNDLE_CODE"), field("QUOTA_PROFILE_CODE"),
                        field("SOURCE_TYPE"), field("SOURCE_REF"),
                        field("GRANT_STATUS"), field("EFFECTIVE_AT"), field("EXPIRES_AT"))
                .values(id, subjectType, subjectId, bundleCode, quotaProfileCode,
                        sourceType, sourceRef, grantStatus, effAt, expAt)
                .execute();
    }

    public List<EntitlementGrantRecord> findBySubjectId(String subjectId) {
        return dsl.select()
                .from(table("ENTITLEMENT_GRANT"))
                .where(field("SUBJECT_ID").eq(subjectId))
                .orderBy(field("EFFECTIVE_AT").desc())
                .fetch(this::mapRecord);
    }

    public List<EntitlementGrantRecord> findActiveBySubjectId(String subjectId) {
        return dsl.select()
                .from(table("ENTITLEMENT_GRANT"))
                .where(field("SUBJECT_ID").eq(subjectId))
                .and(field("GRANT_STATUS").eq("ACTIVE"))
                .and(field("EXPIRES_AT").greaterThan(OffsetDateTime.now())
                        .or(field("EXPIRES_AT").isNull()))
                .orderBy(field("EFFECTIVE_AT").desc())
                .fetch(this::mapRecord);
    }

    private EntitlementGrantRecord mapRecord(Record r) {
        OffsetDateTime effAt = r.get(field("EFFECTIVE_AT"), OffsetDateTime.class);
        OffsetDateTime expAt = r.get(field("EXPIRES_AT"), OffsetDateTime.class);
        return new EntitlementGrantRecord(
                r.get(field("ID"), String.class),
                r.get(field("SUBJECT_TYPE"), String.class),
                r.get(field("SUBJECT_ID"), String.class),
                r.get(field("BUNDLE_CODE"), String.class),
                r.get(field("QUOTA_PROFILE_CODE"), String.class),
                r.get(field("SOURCE_TYPE"), String.class),
                r.get(field("SOURCE_REF"), String.class),
                r.get(field("GRANT_STATUS"), String.class),
                effAt != null ? effAt.toInstant() : null,
                expAt != null ? expAt.toInstant() : null
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
