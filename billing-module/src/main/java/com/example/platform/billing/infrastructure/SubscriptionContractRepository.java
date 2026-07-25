package com.example.platform.billing.infrastructure;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.SubscriptionContract.SUBSCRIPTION_CONTRACT;


/**
 * Persistence repository for subscription contracts.
 *
 * <p>Only created when a {@link DSLContext} bean is available.
 * Falls back to in-memory storage when not available.</p>
 */
@Repository

public class SubscriptionContractRepository {

    private final DSLContext dsl;

    public SubscriptionContractRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(String contractId, String subjectType, String subjectId,
                     String canonicalProductCode, String providerCode,
                     String externalContractRef, String contractState,
                     Instant periodStartAt, Instant periodEndAt) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startAt = periodStartAt != null
                ? LocalDateTime.ofInstant(periodStartAt, java.time.ZoneOffset.UTC) : null;
        LocalDateTime endAt = periodEndAt != null
                ? LocalDateTime.ofInstant(periodEndAt, java.time.ZoneOffset.UTC) : null;
        dsl.insertInto(SUBSCRIPTION_CONTRACT)
                .columns(SUBSCRIPTION_CONTRACT.ID, SUBSCRIPTION_CONTRACT.SUBJECT_TYPE, SUBSCRIPTION_CONTRACT.SUBJECT_ID,
                        SUBSCRIPTION_CONTRACT.CANONICAL_PRODUCT_CODE, SUBSCRIPTION_CONTRACT.PROVIDER_CODE,
                        SUBSCRIPTION_CONTRACT.EXTERNAL_CONTRACT_REF, SUBSCRIPTION_CONTRACT.CONTRACT_STATE,
                        SUBSCRIPTION_CONTRACT.PERIOD_START_AT, SUBSCRIPTION_CONTRACT.PERIOD_END_AT, SUBSCRIPTION_CONTRACT.CREATED_AT)
                .values(contractId, subjectType, subjectId, canonicalProductCode,
                        providerCode, externalContractRef, contractState,
                        startAt, endAt, now)
                .execute();
    }

    public Optional<SubscriptionContractRecord> findById(String id) {
        Record record = dsl.select()
                .from(SUBSCRIPTION_CONTRACT)
                .where(SUBSCRIPTION_CONTRACT.ID.eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<SubscriptionContractRecord> findBySubjectId(String subjectId) {
        return dsl.select()
                .from(SUBSCRIPTION_CONTRACT)
                .where(SUBSCRIPTION_CONTRACT.SUBJECT_ID.eq(subjectId))
                .orderBy(SUBSCRIPTION_CONTRACT.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    private SubscriptionContractRecord mapRecord(Record r) {
        LocalDateTime startAt = r.get(SUBSCRIPTION_CONTRACT.PERIOD_START_AT, LocalDateTime.class);
        LocalDateTime endAt = r.get(SUBSCRIPTION_CONTRACT.PERIOD_END_AT, LocalDateTime.class);
        return new SubscriptionContractRecord(
                r.get(SUBSCRIPTION_CONTRACT.ID, String.class),
                r.get(SUBSCRIPTION_CONTRACT.SUBJECT_TYPE, String.class),
                r.get(SUBSCRIPTION_CONTRACT.SUBJECT_ID, String.class),
                r.get(SUBSCRIPTION_CONTRACT.CANONICAL_PRODUCT_CODE, String.class),
                r.get(SUBSCRIPTION_CONTRACT.PROVIDER_CODE, String.class),
                r.get(SUBSCRIPTION_CONTRACT.EXTERNAL_CONTRACT_REF, String.class),
                r.get(SUBSCRIPTION_CONTRACT.CONTRACT_STATE, String.class),
                startAt != null ? startAt.toInstant(java.time.ZoneOffset.UTC) : null,
                endAt != null ? endAt.toInstant(java.time.ZoneOffset.UTC) : null
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
