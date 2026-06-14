package com.example.platform.billing.infrastructure;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;

/**
 * Persistence repository for billing invoices.
 *
 * <p>Only created when a {@link DSLContext} bean is available.
 * Falls back to in-memory storage when not available.</p>
 */
@Repository

public class BillingInvoiceRepository {

    private final DSLContext dsl;

    public BillingInvoiceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(String invoiceId, String contractId, String providerCode,
                     String externalInvoiceRef, String invoiceStatus,
                     Long amountDueMinor, Long amountPaidMinor, String currencyCode) {
        OffsetDateTime now = OffsetDateTime.now();
        dsl.insertInto(table("BILLING_INVOICE"))
                .columns(field("ID"), field("CONTRACT_ID"), field("PROVIDER_CODE"),
                        field("EXTERNAL_INVOICE_REF"), field("INVOICE_STATUS"),
                        field("AMOUNT_DUE_MINOR"), field("AMOUNT_PAID_MINOR"),
                        field("CURRENCY_CODE"), field("CREATED_AT"))
                .values(invoiceId, contractId, providerCode, externalInvoiceRef, invoiceStatus,
                        amountDueMinor, amountPaidMinor, currencyCode, now)
                .execute();
    }

    public Optional<BillingInvoiceRecord> findById(String id) {
        Record record = dsl.select()
                .from(table("BILLING_INVOICE"))
                .where(field("ID").eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<BillingInvoiceRecord> findByContractId(String contractId) {
        return dsl.select()
                .from(table("BILLING_INVOICE"))
                .where(field("CONTRACT_ID").eq(contractId))
                .orderBy(field("CREATED_AT").desc())
                .fetch(this::mapRecord);
    }

    private BillingInvoiceRecord mapRecord(Record r) {
        return new BillingInvoiceRecord(
                r.get(field("ID"), String.class),
                r.get(field("CONTRACT_ID"), String.class),
                r.get(field("PROVIDER_CODE"), String.class),
                r.get(field("EXTERNAL_INVOICE_REF"), String.class),
                r.get(field("INVOICE_STATUS"), String.class),
                r.get(field("AMOUNT_DUE_MINOR"), Long.class),
                r.get(field("AMOUNT_PAID_MINOR"), Long.class),
                r.get(field("CURRENCY_CODE"), String.class)
        );
    }

    /**
     * Flat record for billing invoice data from the database.
     */
    public record BillingInvoiceRecord(
            String id,
            String contractId,
            String providerCode,
            String externalInvoiceRef,
            String invoiceStatus,
            Long amountDueMinor,
            Long amountPaidMinor,
            String currencyCode
    ) {}
}
