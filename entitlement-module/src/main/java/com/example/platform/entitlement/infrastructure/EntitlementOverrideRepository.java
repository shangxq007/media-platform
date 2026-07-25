package com.example.platform.entitlement.infrastructure;

import com.example.platform.entitlement.domain.EntitlementOverride;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.EntitlementOverride.ENTITLEMENT_OVERRIDE;


@Repository

public class EntitlementOverrideRepository {

    private final DSLContext dsl;

    public EntitlementOverrideRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(EntitlementOverride override) {
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(ENTITLEMENT_OVERRIDE)
                .columns(ENTITLEMENT_OVERRIDE.ID, ENTITLEMENT_OVERRIDE.SUBJECT_TYPE, ENTITLEMENT_OVERRIDE.SUBJECT_ID,
                        ENTITLEMENT_OVERRIDE.OVERRIDE_KIND, ENTITLEMENT_OVERRIDE.OVERRIDE_PAYLOAD,
                        ENTITLEMENT_OVERRIDE.EFFECTIVE_AT, ENTITLEMENT_OVERRIDE.EXPIRES_AT,
                        ENTITLEMENT_OVERRIDE.STATUS, ENTITLEMENT_OVERRIDE.CREATED_AT, ENTITLEMENT_OVERRIDE.UPDATED_AT)
                .values(override.id(), override.subjectType(), override.subjectId(),
                        override.overrideKind(), override.overridePayload(),
                        toLocal(override.effectiveAt()), toLocal(override.expiresAt()),
                        override.status(), now, now)
                .execute();
    }

    public void update(EntitlementOverride override) {
        dsl.update(ENTITLEMENT_OVERRIDE)
                .set(ENTITLEMENT_OVERRIDE.SUBJECT_TYPE, override.subjectType())
                .set(ENTITLEMENT_OVERRIDE.SUBJECT_ID, override.subjectId())
                .set(ENTITLEMENT_OVERRIDE.OVERRIDE_KIND, override.overrideKind())
                .set(ENTITLEMENT_OVERRIDE.OVERRIDE_PAYLOAD, override.overridePayload())
                .set(ENTITLEMENT_OVERRIDE.EFFECTIVE_AT, toLocal(override.effectiveAt()))
                .set(ENTITLEMENT_OVERRIDE.EXPIRES_AT, toLocal(override.expiresAt()))
                .set(ENTITLEMENT_OVERRIDE.STATUS, override.status())
                .set(ENTITLEMENT_OVERRIDE.UPDATED_AT, LocalDateTime.now())
                .where(ENTITLEMENT_OVERRIDE.ID.eq(override.id()))
                .execute();
    }

    public Optional<EntitlementOverride> findById(String id) {
        return dsl.select()
                .from(ENTITLEMENT_OVERRIDE)
                .where(ENTITLEMENT_OVERRIDE.ID.eq(id))
                .fetchOptional(this::mapRecord);
    }

    public List<EntitlementOverride> findBySubjectId(String subjectId) {
        return dsl.select()
                .from(ENTITLEMENT_OVERRIDE)
                .where(ENTITLEMENT_OVERRIDE.SUBJECT_ID.eq(subjectId))
                .orderBy(ENTITLEMENT_OVERRIDE.EFFECTIVE_AT.desc())
                .fetch(this::mapRecord);
    }

    public List<EntitlementOverride> findActiveBySubjectId(String subjectId) {
        LocalDateTime now = LocalDateTime.now();
        return dsl.select()
                .from(ENTITLEMENT_OVERRIDE)
                .where(ENTITLEMENT_OVERRIDE.SUBJECT_ID.eq(subjectId))
                .and(ENTITLEMENT_OVERRIDE.STATUS.eq("ACTIVE"))
                .and(ENTITLEMENT_OVERRIDE.EFFECTIVE_AT.lessOrEqual(now))
                .and(ENTITLEMENT_OVERRIDE.EXPIRES_AT.greaterThan(now).or(ENTITLEMENT_OVERRIDE.EXPIRES_AT.isNull()))
                .orderBy(ENTITLEMENT_OVERRIDE.EFFECTIVE_AT.desc())
                .fetch(this::mapRecord);
    }

    public List<EntitlementOverride> findAllActive() {
        LocalDateTime now = LocalDateTime.now();
        return dsl.select()
                .from(ENTITLEMENT_OVERRIDE)
                .where(ENTITLEMENT_OVERRIDE.STATUS.eq("ACTIVE"))
                .and(ENTITLEMENT_OVERRIDE.EFFECTIVE_AT.lessOrEqual(now))
                .and(ENTITLEMENT_OVERRIDE.EXPIRES_AT.greaterThan(now).or(ENTITLEMENT_OVERRIDE.EXPIRES_AT.isNull()))
                .orderBy(ENTITLEMENT_OVERRIDE.EFFECTIVE_AT.desc())
                .fetch(this::mapRecord);
    }

    private EntitlementOverride mapRecord(Record r) {
        return new EntitlementOverride(
                r.get(ENTITLEMENT_OVERRIDE.ID, String.class),
                r.get(ENTITLEMENT_OVERRIDE.SUBJECT_TYPE, String.class),
                r.get(ENTITLEMENT_OVERRIDE.SUBJECT_ID, String.class),
                r.get(ENTITLEMENT_OVERRIDE.OVERRIDE_KIND, String.class),
                r.get(ENTITLEMENT_OVERRIDE.OVERRIDE_PAYLOAD, String.class),
                toInstant(r.get(ENTITLEMENT_OVERRIDE.EFFECTIVE_AT, LocalDateTime.class)),
                toInstant(r.get(ENTITLEMENT_OVERRIDE.EXPIRES_AT, LocalDateTime.class)),
                r.get(ENTITLEMENT_OVERRIDE.STATUS, String.class),
                toInstant(r.get(ENTITLEMENT_OVERRIDE.CREATED_AT, LocalDateTime.class)),
                toInstant(r.get(ENTITLEMENT_OVERRIDE.UPDATED_AT, LocalDateTime.class))
        );
    }

    private LocalDateTime toLocal(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    private Instant toInstant(LocalDateTime ldt) {
        return ldt != null ? ldt.toInstant(java.time.ZoneOffset.UTC) : null;
    }
}
