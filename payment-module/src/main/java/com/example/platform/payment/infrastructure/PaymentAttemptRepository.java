package com.example.platform.payment.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;

/**
 * Persistence repository for payment attempts.
 *
 * <p>Only created when a {@link DSLContext} bean is available.
 * Falls back to in-memory storage when not available.</p>
 */
@Repository

public class PaymentAttemptRepository {

    private final DSLContext dsl;

    public PaymentAttemptRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(String id, String purchaseOrderId, String providerCode,
                     String providerReference, String attemptStatus,
                     Long amountMinor, String currencyCode,
                     String requestPayload, String responsePayload) {
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(table("PAYMENT_ATTEMPT"))
                .columns(field("ID"), field("PURCHASE_ORDER_ID"), field("PROVIDER_CODE"),
                        field("PROVIDER_REFERENCE"), field("ATTEMPT_STATUS"),
                        field("AMOUNT_MINOR"), field("CURRENCY_CODE"),
                        field("REQUEST_PAYLOAD"), field("RESPONSE_PAYLOAD"),
                        field("CREATED_AT"))
                .values(id, purchaseOrderId, providerCode, providerReference, attemptStatus,
                        amountMinor, currencyCode, requestPayload, responsePayload, now)
                .execute();
    }

    public Optional<PaymentAttemptRecord> findById(String id) {
        Record record = dsl.select()
                .from(table("PAYMENT_ATTEMPT"))
                .where(field("ID").eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public Optional<PaymentAttemptRecord> findByProviderReference(String providerReference) {
        Record record = dsl.select()
                .from(table("PAYMENT_ATTEMPT"))
                .where(field("PROVIDER_REFERENCE").eq(providerReference))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    private PaymentAttemptRecord mapRecord(Record r) {
        return new PaymentAttemptRecord(
                r.get(field("ID"), String.class),
                r.get(field("PURCHASE_ORDER_ID"), String.class),
                r.get(field("PROVIDER_CODE"), String.class),
                r.get(field("PROVIDER_REFERENCE"), String.class),
                r.get(field("ATTEMPT_STATUS"), String.class),
                r.get(field("AMOUNT_MINOR"), Long.class),
                r.get(field("CURRENCY_CODE"), String.class)
        );
    }

    /**
     * Flat record for payment attempt data from the database.
     */
    public record PaymentAttemptRecord(
            String id,
            String purchaseOrderId,
            String providerCode,
            String providerReference,
            String attemptStatus,
            Long amountMinor,
            String currencyCode
    ) {}
}
