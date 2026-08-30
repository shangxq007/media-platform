package com.example.platform.payment.app;

import com.example.platform.payment.infrastructure.PaymentTransactionJdbcRepository;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

/** Dispatches settlement references only after claiming the durable Payment outbox row. */
@Service
public class PaymentOutboxDispatcher {
    private final PaymentTransactionJdbcRepository repository;
    private final PaymentSettlementProjectionPort projection;

    public PaymentOutboxDispatcher(PaymentTransactionJdbcRepository repository,
                                   PaymentSettlementProjectionPort projection) {
        this.repository = repository;
        this.projection = projection;
    }

    @Transactional
    public boolean dispatchNext() {
        return dispatchOne();
    }

    @Transactional
    @Scheduled(fixedDelayString = "${platform.payment.outbox.dispatch-delay-ms:1000}")
    public void dispatchScheduled() {
        dispatchOne();
    }

    private boolean dispatchOne() {
        PaymentTransactionJdbcRepository.PaymentOutboxEvent event =
                repository.claimNextOutbox().orElse(null);
        if (event == null) return false;
        projection.onPaymentSettled(new PaymentSettlementProjectionPort.PaymentSettledEvent(
                event.eventId(), event.transactionId(), event.tenantId(), event.providerCode(),
                event.providerReference(), event.checkoutSessionId(), event.traceId()));
        repository.markOutboxDispatched(event.eventId());
        return true;
    }
}
