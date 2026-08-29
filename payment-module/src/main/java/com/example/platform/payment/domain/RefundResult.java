package com.example.platform.payment.domain;

import com.example.platform.shared.commercial.Money;

public record RefundResult(
        String refundId, String transactionId, PaymentState transactionState,
        Money refundedAmount, long transactionVersion, String providerRefundReference) {
}
