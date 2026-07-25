package com.example.platform.payment.infrastructure;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.PaymentAttempt.PAYMENT_ATTEMPT;


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
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(PAYMENT_ATTEMPT)
                .columns(PAYMENT_ATTEMPT.ID, PAYMENT_ATTEMPT.PURCHASE_ORDER_ID, PAYMENT_ATTEMPT.PROVIDER_CODE,
                        PAYMENT_ATTEMPT.PROVIDER_REFERENCE, PAYMENT_ATTEMPT.ATTEMPT_STATUS,
                        PAYMENT_ATTEMPT.AMOUNT_MINOR, PAYMENT_ATTEMPT.CURRENCY_CODE,
                        PAYMENT_ATTEMPT.REQUEST_PAYLOAD, PAYMENT_ATTEMPT.RESPONSE_PAYLOAD,
                        PAYMENT_ATTEMPT.CREATED_AT)
                .values(id, purchaseOrderId, providerCode, providerReference, attemptStatus,
                        amountMinor, currencyCode, requestPayload, responsePayload, now)
                .execute();
    }

    public Optional<PaymentAttemptRecord> findById(String id) {
        Record record = dsl.select()
                .from(PAYMENT_ATTEMPT)
                .where(PAYMENT_ATTEMPT.ID.eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public Optional<PaymentAttemptRecord> findByProviderReference(String providerReference) {
        Record record = dsl.select()
                .from(PAYMENT_ATTEMPT)
                .where(PAYMENT_ATTEMPT.PROVIDER_REFERENCE.eq(providerReference))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    private PaymentAttemptRecord mapRecord(Record r) {
        return new PaymentAttemptRecord(
                r.get(PAYMENT_ATTEMPT.ID, String.class),
                r.get(PAYMENT_ATTEMPT.PURCHASE_ORDER_ID, String.class),
                r.get(PAYMENT_ATTEMPT.PROVIDER_CODE, String.class),
                r.get(PAYMENT_ATTEMPT.PROVIDER_REFERENCE, String.class),
                r.get(PAYMENT_ATTEMPT.ATTEMPT_STATUS, String.class),
                r.get(PAYMENT_ATTEMPT.AMOUNT_MINOR, Long.class),
                r.get(PAYMENT_ATTEMPT.CURRENCY_CODE, String.class)
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
