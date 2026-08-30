package com.example.platform.payment.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.payment.domain.InitiateCheckoutCommand;
import com.example.platform.payment.domain.PaymentProviderUnavailableException;
import com.example.platform.payment.infrastructure.PaymentTransactionJdbcRepository;
import com.example.platform.shared.commercial.Money;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class UnavailablePaymentProviderTest {

    @Test
    void disabledProviderFailsTypedBeforeAnyPaymentPersistence() {
        PaymentTransactionJdbcRepository repository = mock(PaymentTransactionJdbcRepository.class);
        PlatformTransactionManager transactionManager = new TestTransactionManager();
        when(repository.findCommand(commandPrincipal(), "idempotency-1")).thenReturn(Optional.empty());
        PaymentTransactionAuthority authority =
                new PaymentTransactionAuthority(List.of(), repository, transactionManager);
        InitiateCheckoutCommand command = new InitiateCheckoutCommand(
                "txn-1",
                commandPrincipal(),
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
        verify(repository).lockIdentity("tenant-1", "idempotency-1");
        verify(repository).findCommand(command.principal(), "idempotency-1");
        verify(repository, never()).insertIntent(command);
        verify(repository, never()).claimCheckoutProviderCall(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
    }

    private static PrincipalRef commandPrincipal() {
        return PrincipalRef.tenantScoped("tenant-1", PrincipalType.USER, "user-1");
    }

    private static final class TestTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {}

        @Override
        public void rollback(TransactionStatus status) {}
    }
}
