package com.example.platform.payment.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentCommandContractTest {
    @Test
    void mutatingCommandsRequireExplicitPrincipalExactMoneyAndMetadata() {
        PrincipalRef principal = PrincipalRef.tenantScoped("tenant-1", PrincipalType.USER, "user-1");
        assertThrows(IllegalArgumentException.class, () -> new InitiateCheckoutCommand(
                "txn", principal, null, "checkout", "stripe", new Money(0, "USD"),
                "product", null, null, "key", "source", "reason", "trace", Instant.EPOCH));
        assertThrows(IllegalArgumentException.class, () -> new RefundPaymentCommand(
                principal, "txn", "capture", new Money(1, "USD"), 1,
                "", "source", "reason", "trace", Instant.EPOCH));
    }

    @Test
    void successfulProviderRefundRequiresDurableProviderIdentity() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProviderRefundResult(true, null, "succeeded"));
    }
}
