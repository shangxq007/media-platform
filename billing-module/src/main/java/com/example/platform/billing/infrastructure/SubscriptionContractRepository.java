package com.example.platform.billing.infrastructure;

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
 * Persistence repository for subscription contracts.
 *
 * <p>Only created when a {@link DSLContext} bean is available.
 * Falls back to in-memory storage when not available.</p>
 */
@Repository
@ConditionalOnBean(DSLContext.class)
public class SubscriptionContractRepository {

    private final DSLContext dsl;

    public SubscriptionContractRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(String contractId, String subjectType, String subjectId,
                     String canonicalProductCode, String providerCode,
                     String externalContractRef, String contractState,
                     Instant periodStartAt, Instant periodEndAt) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startAt = periodStartAt != null
                ? OffsetDateTime.ofInstant(periodStartAt, ZoneOffset.UTC) : null;
        OffsetDateTime endAt = periodEndAt != null
                ? OffsetDateTime.ofInstant(periodEndAt, ZoneOffset.UTC) : null;
        dsl.insertInto(table("SUBSCRIPTION_CONTRACT"))
                .columns(field("ID"), field("SUBJECT_TYPE"), field("SUBJECT_ID"),
                        field("CANONICAL_PRODUCT_CODE"), field("PROVIDER_CODE"),
                        field("EXTERNAL_CONTRACT_REF"), field("CONTRACT_STATE"),
                        field("PERIOD_START_AT"), field("PERIOD_END_AT"), field("CREATED_AT"))
                .values(contractId, subjectType, subjectId, canonicalProductCode,
                        providerCode, externalContractRef, contractState,
                        startAt, endAt, now)
                .execute();
    }

    public Optional<SubscriptionContractRecord> findById(String id) {
        Record record = dsl.select()
                .from(table("SUBSCRIPTION_CONTRACT"))
                .where(field("ID").eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<SubscriptionContractRecord> findBySubjectId(String subjectId) {
        return dsl.select()
                .from(table("SUBSCRIPTION_CONTRACT"))
                .where(field("SUBJECT_ID").eq(subjectId))
                .orderBy(field("CREATED_AT").desc())
                .fetch(this::mapRecord);
    }

    private SubscriptionContractRecord mapRecord(Record r) {
        OffsetDateTime startAt = r.get(field("PERIOD_START_AT"), OffsetDateTime.class);
        OffsetDateTime endAt = r.get(field("PERIOD_END_AT"), OffsetDateTime.class);
        return new SubscriptionContractRecord(
                r.get(field("ID"), String.class),
                r.get(field("SUBJECT_TYPE"), String.class),
                r.get(field("SUBJECT_ID"), String.class),
                r.get(field("CANONICAL_PRODUCT_CODE"), String.class),
                r.get(field("PROVIDER_CODE"), String.class),
                r.get(field("EXTERNAL_CONTRACT_REF"), String.class),
                r.get(field("CONTRACT_STATE"), String.class),
                startAt != null ? startAt.toInstant() : null,
                endAt != null ? endAt.toInstant() : null
        );
    }

    /**
     * Flat record for subscription contract data from the database.
     */
    public record SubscriptionContractRecord(
            String id,
            String subjectType,
            String subjectId,
            String canonicalProductCode,
            String providerCode,
            String externalContractRef,
            String contractState,
            Instant periodStartAt,
            Instant periodEndAt
    ) {}
}
