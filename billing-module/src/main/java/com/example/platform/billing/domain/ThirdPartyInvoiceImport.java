package com.example.platform.billing.domain;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Third-party invoice import record.
 */
public record ThirdPartyInvoiceImport(
        String importId,
        String providerCode,
        String invoiceId,
        String tenantId,
        double amount,
        String currency,
        String lineItemDescription,
        OffsetDateTime servicePeriodStart,
        OffsetDateTime servicePeriodEnd,
        String rawData,
        OffsetDateTime importedAt,
        String status) {

    public static ThirdPartyInvoiceImport create(String providerCode, String invoiceId,
            String tenantId, double amount, String currency, String description,
            OffsetDateTime periodStart, OffsetDateTime periodEnd, String rawData) {
        return new ThirdPartyInvoiceImport(
                java.util.UUID.randomUUID().toString(),
                providerCode, invoiceId, tenantId, amount, currency,
                description, periodStart, periodEnd, rawData,
                OffsetDateTime.now(), "IMPORTED");
    }
}
