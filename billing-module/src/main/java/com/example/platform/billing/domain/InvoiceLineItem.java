package com.example.platform.billing.domain;

import com.example.platform.shared.commercial.Money;
import java.time.Instant;

public record InvoiceLineItem(
        String lineItemId, String tenantId, String invoiceId, String ratedUsageId,
        String lineType, String description, long quantityBaseUnits,
        Money unitPrice, Money amount, Instant periodStart, Instant periodEnd,
        Instant createdAt) {}
