package com.example.platform.billing.domain;

import java.time.Instant;

public record InvoiceLineItem(
        String lineItemId,
        String invoiceId,
        String lineType,
        String description,
        double quantity,
        long unitPriceMinor,
        long amountMinor,
        String currencyCode,
        Instant periodStart,
        Instant periodEnd,
        Instant createdAt) {
}
