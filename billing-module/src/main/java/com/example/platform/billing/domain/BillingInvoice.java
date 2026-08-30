package com.example.platform.billing.domain;

import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import java.time.Instant;

public record BillingInvoice(
        String invoiceId, PrincipalRef principal, String contractId,
        String providerCode, String externalInvoiceRef, InvoiceStatus status,
        Money total, Money amountPaid, long version,
        Instant issuedAt, Instant paidAt, Instant createdAt, Instant updatedAt) {}
