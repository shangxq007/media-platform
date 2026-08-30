package com.example.platform.billing.domain;

import com.example.platform.shared.commercial.Money;
import java.time.OffsetDateTime;

public record ThirdPartyInvoiceImport(
        String importId, String providerCode, String invoiceId, String tenantId,
        Money amount, String lineItemDescription,
        OffsetDateTime servicePeriodStart, OffsetDateTime servicePeriodEnd,
        String rawData, OffsetDateTime importedAt, String status) {

    public static ThirdPartyInvoiceImport create(String providerCode, String invoiceId,
            String tenantId, Money amount, String description,
            OffsetDateTime periodStart, OffsetDateTime periodEnd, String rawData) {
        return new ThirdPartyInvoiceImport(java.util.UUID.randomUUID().toString(),
                providerCode, invoiceId, tenantId, amount, description, periodStart,
                periodEnd, rawData, OffsetDateTime.now(), "IMPORTED");
    }

    public String currency() { return amount.currency(); }
}
