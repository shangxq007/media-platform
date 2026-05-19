package com.example.platform.entitlement.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.entitlement.domain.EntitlementOverride;
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
public class EntitlementOverrideRepository {

    private final DSLContext dsl;

    public EntitlementOverrideRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(EntitlementOverride override) {
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(table("ENTITLEMENT_OVERRIDE"))
                .columns(field("ID"), field("SUBJECT_TYPE"), field("SUBJECT_ID"),
                        field("OVERRIDE_KIND"), field("OVERRIDE_PAYLOAD"),
                        field("EFFECTIVE_AT"), field("EXPIRES_AT"),
                        field("STATUS"), field("CREATED_AT"), field("UPDATED_AT"))
                .values(override.id(), override.subjectType(), override.subjectId(),
                        override.overrideKind(), override.overridePayload(),
                        toOffset(override.effectiveAt()), toOffset(override.expiresAt()),
                        override.status(), now, now)
                .execute();
    }

    public void update(EntitlementOverride override) {
        dsl.update(table("ENTITLEMENT_OVERRIDE"))
                .set(field("SUBJECT_TYPE"), override.subjectType())
                .set(field("SUBJECT_ID"), override.subjectId())
                .set(field("OVERRIDE_KIND"), override.overrideKind())
                .set(field("OVERRIDE_PAYLOAD"), override.overridePayload())
                .set(field("EFFECTIVE_AT"), toOffset(override.effectiveAt()))
                .set(field("EXPIRES_AT"), toOffset(override.expiresAt()))
                .set(field("STATUS"), override.status())
                .set(field("UPDATED_AT"), OffsetDateTime.now())
                .where(field("ID").eq(override.id()))
                .execute();
    }

    public Optional<EntitlementOverride> findById(String id) {
        return dsl.select()
                .from(table("ENTITLEMENT_OVERRIDE"))
                .where(field("ID").eq(id))
                .fetchOptional(this::mapRecord);
    }

    public List<EntitlementOverride> findBySubjectId(String subjectId) {
        return dsl.select()
                .from(table("ENTITLEMENT_OVERRIDE"))
                .where(field("SUBJECT_ID").eq(subjectId))
                .orderBy(field("EFFECTIVE_AT").desc())
                .fetch(this::mapRecord);
    }

    public List<EntitlementOverride> findActiveBySubjectId(String subjectId) {
        OffsetDateTime now = OffsetDateTime.now();
        return dsl.select()
                .from(table("ENTITLEMENT_OVERRIDE"))
                .where(field("SUBJECT_ID").eq(subjectId))
                .and(field("STATUS").eq("ACTIVE"))
                .and(field("EFFECTIVE_AT").lessOrEqual(now))
                .and(field("EXPIRES_AT").greaterThan(now).or(field("EXPIRES_AT").isNull()))
                .orderBy(field("EFFECTIVE_AT").desc())
                .fetch(this::mapRecord);
    }

    public List<EntitlementOverride> findAllActive() {
        OffsetDateTime now = OffsetDateTime.now();
        return dsl.select()
                .from(table("ENTITLEMENT_OVERRIDE"))
                .where(field("STATUS").eq("ACTIVE"))
                .and(field("EFFECTIVE_AT").lessOrEqual(now))
                .and(field("EXPIRES_AT").greaterThan(now).or(field("EXPIRES_AT").isNull()))
                .orderBy(field("EFFECTIVE_AT").desc())
                .fetch(this::mapRecord);
    }

    private EntitlementOverride mapRecord(Record r) {
        return new EntitlementOverride(
                r.get(field("ID"), String.class),
                r.get(field("SUBJECT_TYPE"), String.class),
                r.get(field("SUBJECT_ID"), String.class),
                r.get(field("OVERRIDE_KIND"), String.class),
                r.get(field("OVERRIDE_PAYLOAD"), String.class),
                toInstant(r.get(field("EFFECTIVE_AT"), OffsetDateTime.class)),
                toInstant(r.get(field("EXPIRES_AT"), OffsetDateTime.class)),
                r.get(field("STATUS"), String.class),
                toInstant(r.get(field("CREATED_AT"), OffsetDateTime.class)),
                toInstant(r.get(field("UPDATED_AT"), OffsetDateTime.class))
        );
    }

    private OffsetDateTime toOffset(Instant instant) {
        return instant != null ? OffsetDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }

    private Instant toInstant(OffsetDateTime odt) {
        return odt != null ? odt.toInstant() : null;
    }
}
