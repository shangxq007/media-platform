package com.example.platform.payment.domain;

import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import java.time.Instant;

public record PaymentTransaction(
        String transactionId, PrincipalRef principal, String orderId, String checkoutSessionId,
        String providerCode, String providerReference, String redirectUrl, Money amount,
        PaymentState state, Long providerEventCursor, Money capturedAmount, Money refundedAmount,
        long version, String source, String traceId, Instant createdAt, Instant updatedAt) {
}
