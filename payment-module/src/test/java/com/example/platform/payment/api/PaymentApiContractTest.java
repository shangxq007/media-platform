package com.example.platform.payment.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.payment.api.dto.ConfirmPaymentRequest;
import com.example.platform.payment.api.dto.RefundPaymentRequest;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PaymentApiContractTest {
    @Test
    void confirmationIsReferenceOnlyAndCarriesScopedCommandMetadata() {
        var names = Arrays.stream(ConfirmPaymentRequest.class.getRecordComponents())
                .map(component -> component.getName()).toList();
        assertFalse(names.contains("payload"));
        assertFalse(names.contains("rawPayload"));
        assertTrue(names.containsAll(java.util.List.of("tenantId", "principalType", "principalId",
                "transactionId", "providerCode", "providerReference", "expectedVersion",
                "idempotencyKey", "reason", "traceId", "occurredAt")));
    }

    @Test
    void refundApiCarriesExactMoneyIdentityAndCas() {
        var names = Arrays.stream(RefundPaymentRequest.class.getRecordComponents())
                .map(component -> component.getName()).toList();
        assertTrue(names.containsAll(java.util.List.of("amountMinor", "currencyCode",
                "originalCaptureReference", "expectedVersion", "idempotencyKey")));
    }
}
