package com.example.platform.payment.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.platform.payment.domain.InitiateCheckoutCommand;
import com.example.platform.payment.domain.PaymentProviderUnavailableException;
import com.example.platform.payment.infrastructure.PaymentTransactionJdbcRepository;
import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class UnavailablePaymentProviderTest {

    @Test
    void disabledProviderFailsTypedBeforeAnyPaymentPersistence() {
        PaymentTransactionJdbcRepository repository = mock(PaymentTransactionJdbcRepository.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        PaymentTransactionAuthority authority =
                new PaymentTransactionAuthority(List.of(), repository, transactionManager);
        InitiateCheckoutCommand command = new InitiateCheckoutCommand(
                "txn-1",
                PrincipalRef.tenantScoped("tenant-1", PrincipalType.USER, "user-1"),
                "order-1",
                "checkout-1",
                "stripe",
                new Money(1000, "USD"),
                "product-1",
                "https://example.test/success",
                "https://example.test/cancel",
                "idempotency-1",
                "test",
                "checkout",
                "trace-1",
                Instant.parse("2026-08-31T00:00:00Z"));

        PaymentProviderUnavailableException failure = assertThrows(
                PaymentProviderUnavailableException.class,
                () -> authority.initiateCheckout(command));

        assertEquals("stripe", failure.providerCode());
        assertEquals(503, failure.getErrorCode().status());
        verifyNoInteractions(repository, transactionManager);
    }
}
