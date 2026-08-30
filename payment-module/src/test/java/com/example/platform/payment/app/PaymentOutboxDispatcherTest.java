package com.example.platform.payment.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.payment.infrastructure.PaymentTransactionJdbcRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaymentOutboxDispatcherTest {
    @Test
    void dispatchesOnlyFromDurableOutboxAndMarksAfterConsumerSuccess() {
        PaymentTransactionJdbcRepository repository = mock(PaymentTransactionJdbcRepository.class);
        PaymentSettlementProjectionPort consumer = mock(PaymentSettlementProjectionPort.class);
        PaymentTransactionJdbcRepository.PaymentOutboxEvent event = event();
        when(repository.claimNextOutbox()).thenReturn(Optional.of(event));
        PaymentOutboxDispatcher dispatcher = new PaymentOutboxDispatcher(repository, consumer);

        assertTrue(dispatcher.dispatchNext());
        verify(consumer).onPaymentSettled(new PaymentSettlementProjectionPort.PaymentSettledEvent(
                event.eventId(), event.transactionId(), event.tenantId(), event.providerCode(),
                event.providerReference(), event.checkoutSessionId(), event.traceId()));
        verify(repository).markOutboxDispatched(event.eventId());
    }

    @Test
    void consumerFailureLeavesOutboxPendingForRetry() {
        PaymentTransactionJdbcRepository repository = mock(PaymentTransactionJdbcRepository.class);
        PaymentSettlementProjectionPort consumer = mock(PaymentSettlementProjectionPort.class);
        PaymentTransactionJdbcRepository.PaymentOutboxEvent event = event();
        when(repository.claimNextOutbox()).thenReturn(Optional.of(event));
        org.mockito.Mockito.doThrow(new IllegalStateException("consumer failed"))
                .when(consumer).onPaymentSettled(org.mockito.ArgumentMatchers.any());
        PaymentOutboxDispatcher dispatcher = new PaymentOutboxDispatcher(repository, consumer);

        assertThrows(IllegalStateException.class, dispatcher::dispatchNext);
        verify(repository, never()).markOutboxDispatched(event.eventId());
    }

    @Test
    void emptyOutboxDoesNothing() {
        PaymentTransactionJdbcRepository repository = mock(PaymentTransactionJdbcRepository.class);
        PaymentSettlementProjectionPort consumer = mock(PaymentSettlementProjectionPort.class);
        when(repository.claimNextOutbox()).thenReturn(Optional.empty());
        assertFalse(new PaymentOutboxDispatcher(repository, consumer).dispatchNext());
    }

    private static PaymentTransactionJdbcRepository.PaymentOutboxEvent event() {
        return new PaymentTransactionJdbcRepository.PaymentOutboxEvent(
                "event-1", "txn-1", "tenant-1", "stripe", "pi-1",
                "checkout-1", "trace-1", Instant.EPOCH);
    }
}
