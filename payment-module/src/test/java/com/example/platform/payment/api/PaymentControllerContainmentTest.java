package com.example.platform.payment.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.platform.payment.api.dto.ConfirmPaymentRequest;
import com.example.platform.payment.api.dto.RefundPaymentRequest;
import com.example.platform.payment.app.PaymentTransactionAuthority;
import com.example.platform.shared.authorization.AuthorizationDeniedException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentControllerContainmentTest {

    @Test
    void requestPrincipalCannotAuthorizeConfirmationOrRefund() {
        PaymentTransactionAuthority authority = mock(PaymentTransactionAuthority.class);
        PaymentController controller = new PaymentController(authority);
        Instant now = Instant.parse("2026-08-31T00:00:00Z");

        assertUnavailable(() -> controller.confirm(new ConfirmPaymentRequest(
                "request-tenant", "USER", "request-user", "workspace", "organization",
                "transaction", "provider", "provider-reference", 1,
                "idempotency", "reason", "trace", now)));
        assertUnavailable(() -> controller.refund(new RefundPaymentRequest(
                "request-tenant", "USER", "request-user", "workspace", "organization",
                "transaction", "capture", 100, "USD", 1,
                "idempotency", "reason", "trace", now)));

        verifyNoInteractions(authority);
    }

    private static void assertUnavailable(org.junit.jupiter.api.function.Executable invocation) {
        AuthorizationDeniedException failure = assertThrows(
                AuthorizationDeniedException.class, invocation);
        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
    }
}
