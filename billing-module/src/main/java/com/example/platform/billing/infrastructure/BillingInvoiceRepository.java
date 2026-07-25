package com.example.platform.billing.infrastructure;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;

import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.BillingInvoice.BILLING_INVOICE;


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
        LocalDateTime now = LocalDateTime.now();
        dsl.insertInto(BILLING_INVOICE)
                .columns(BILLING_INVOICE.ID, BILLING_INVOICE.CONTRACT_ID, BILLING_INVOICE.PROVIDER_CODE,
                        BILLING_INVOICE.EXTERNAL_INVOICE_REF, BILLING_INVOICE.INVOICE_STATUS,
                        BILLING_INVOICE.AMOUNT_DUE_MINOR, BILLING_INVOICE.AMOUNT_PAID_MINOR,
                        BILLING_INVOICE.CURRENCY_CODE, BILLING_INVOICE.CREATED_AT)
                .values(invoiceId, contractId, providerCode, externalInvoiceRef, invoiceStatus,
                        amountDueMinor, amountPaidMinor, currencyCode, now)
                .execute();
    }

    public Optional<BillingInvoiceRecord> findById(String id) {
        Record record = dsl.select()
                .from(BILLING_INVOICE)
                .where(BILLING_INVOICE.ID.eq(id))
                .fetchOne();
        return Optional.ofNullable(record).map(this::mapRecord);
    }

    public List<BillingInvoiceRecord> findByContractId(String contractId) {
        return dsl.select()
                .from(BILLING_INVOICE)
                .where(BILLING_INVOICE.CONTRACT_ID.eq(contractId))
                .orderBy(BILLING_INVOICE.CREATED_AT.desc())
                .fetch(this::mapRecord);
    }

    private BillingInvoiceRecord mapRecord(Record r) {
        return new BillingInvoiceRecord(
                r.get(BILLING_INVOICE.ID, String.class),
                r.get(BILLING_INVOICE.CONTRACT_ID, String.class),
                r.get(BILLING_INVOICE.PROVIDER_CODE, String.class),
                r.get(BILLING_INVOICE.EXTERNAL_INVOICE_REF, String.class),
                r.get(BILLING_INVOICE.INVOICE_STATUS, String.class),
                r.get(BILLING_INVOICE.AMOUNT_DUE_MINOR, Long.class),
                r.get(BILLING_INVOICE.AMOUNT_PAID_MINOR, Long.class),
                r.get(BILLING_INVOICE.CURRENCY_CODE, String.class)
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
