package com.example.platform.payment.app;

import com.example.platform.payment.domain.ApplyWebhookCommand;
import com.example.platform.payment.domain.BindProviderResultCommand;
import com.example.platform.payment.domain.CheckoutResult;
import com.example.platform.payment.domain.InitiateCheckoutCommand;
import com.example.platform.payment.domain.PaymentProvider;
import com.example.platform.payment.domain.PaymentState;
import com.example.platform.payment.domain.PaymentTransaction;
import com.example.platform.payment.domain.PaymentVerificationResult;
import com.example.platform.payment.domain.ProviderRefundRequest;
import com.example.platform.payment.domain.ProviderRefundResult;
import com.example.platform.payment.domain.ProviderVerificationRequest;
import com.example.platform.payment.domain.RefundPaymentCommand;
import com.example.platform.payment.domain.RefundResult;
import com.example.platform.payment.domain.VerifyPaymentCommand;
import com.example.platform.payment.infrastructure.PaymentTransactionJdbcRepository;
import com.example.platform.shared.commercial.PrincipalRef;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Sole Payment-owned command authority. Provider calls are fenced by durable intents. */
@Service
public class PaymentTransactionAuthority {

    private final Map<String, PaymentProvider> providers;
    private final PaymentTransactionJdbcRepository repository;
    private final TransactionTemplate transactions;

    public PaymentTransactionAuthority(List<PaymentProvider> providers,
                                       PaymentTransactionJdbcRepository repository,
                                       PlatformTransactionManager transactionManager) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                provider -> provider.code().value(), provider -> provider));
        this.repository = repository;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public PaymentTransaction initiateCheckout(InitiateCheckoutCommand command) {
        PaymentProvider provider = requireProvider(command.providerCode());
        PaymentTransaction intent = required(transactions.execute(status -> {
            repository.lockIdentity(command.principal().tenantId(), command.idempotencyKey());
            PaymentTransactionJdbcRepository.StoredCommand prior = repository
                    .findCommand(command.principal(), command.idempotencyKey()).orElse(null);
            if (prior != null) {
                requireFingerprint(prior.fingerprint(), command.fingerprint(), "checkout");
                return repository.find(command.principal(), prior.transactionId()).orElseThrow();
            }
            return repository.insertIntent(command);
        }));
        if (intent.state() != PaymentState.INITIATED) return intent;

        boolean claimed = required(transactions.execute(status -> repository.claimCheckoutProviderCall(
                command.principal(), command.transactionId(), intent.version(), command.occurredAt())));
        if (!claimed) {
            PaymentTransaction concurrent = repository.find(command.principal(), command.transactionId())
                    .orElseThrow(() -> new IllegalStateException("Checkout intent disappeared"));
            if (concurrent.state() != PaymentState.INITIATED) return concurrent;
            throw new IllegalStateException("Checkout provider call is already in progress");
        }

        CheckoutResult result;
        try {
            result = provider.createCheckout(command);
        } catch (RuntimeException providerFailure) {
            transactions.executeWithoutResult(status -> repository.projectProviderFailure(
                    command.principal(), command.transactionId(), intent.version(), command.occurredAt()));
            throw new IllegalStateException("Payment provider checkout failed after durable intent", providerFailure);
        }
        try {
            return bindProviderResult(new BindProviderResultCommand(
                    command.principal(), command.transactionId(), command.providerCode(),
                    result.providerReference(), result.redirectUrl(), PaymentState.PENDING,
                    intent.version(), "provider-result:" + command.idempotencyKey(),
                    "provider", "bind checkout provider result", command.traceId(), command.occurredAt()));
        } catch (RuntimeException bindingFailure) {
            transactions.executeWithoutResult(status -> repository.releaseCheckoutProviderCall(
                    command.principal(), command.transactionId(), intent.version(), command.occurredAt()));
            throw bindingFailure;
        }
    }

    public PaymentTransaction bindProviderResult(BindProviderResultCommand command) {
        return required(transactions.execute(status -> {
            repository.lockIdentity(command.principal().tenantId(), command.idempotencyKey());
            PaymentTransactionJdbcRepository.StoredCommand prior = repository
                    .findCommand(command.principal(), command.idempotencyKey()).orElse(null);
            if (prior != null) {
                requireFingerprint(prior.fingerprint(), command.fingerprint(), "provider result");
                return repository.find(command.principal(), prior.transactionId()).orElseThrow();
            }
            PaymentTransaction result = repository.bindProviderResult(command);
            repository.saveCommand(command.principal(), command.idempotencyKey(), "BIND_PROVIDER",
                    command.transactionId(), command.fingerprint(), command.providerReference(),
                    result.state(), result.version(), command.source(), command.reason(),
                    command.traceId(), command.occurredAt());
            return result;
        }));
    }

    public PaymentTransaction verifyPayment(VerifyPaymentCommand command) {
        PaymentTransactionJdbcRepository.StoredCommand prior = required(transactions.execute(status -> {
            repository.lockIdentity(command.principal().tenantId(), command.idempotencyKey());
            return repository.findCommand(command.principal(), command.idempotencyKey())
                    .orElse(new PaymentTransactionJdbcRepository.StoredCommand("", "", null,
                            PaymentState.INITIATED, -1));
        }));
        if (prior.resultVersion() >= 0) {
            requireFingerprint(prior.fingerprint(), command.fingerprint(), "verification");
            return repository.find(command.principal(), prior.transactionId()).orElseThrow();
        }
        PaymentProvider provider = requireProvider(command.providerCode());
        PaymentVerificationResult verification = provider.verifyPayment(new ProviderVerificationRequest(
                command.providerReference(), command.idempotencyKey(), command.traceId()));
        return required(transactions.execute(status -> {
            repository.lockIdentity(command.principal().tenantId(), command.idempotencyKey());
            PaymentTransactionJdbcRepository.StoredCommand concurrent = repository
                    .findCommand(command.principal(), command.idempotencyKey()).orElse(null);
            if (concurrent != null) {
                requireFingerprint(concurrent.fingerprint(), command.fingerprint(), "verification");
                return repository.find(command.principal(), concurrent.transactionId()).orElseThrow();
            }
            PaymentTransaction result = repository.projectVerification(command, verification);
            repository.saveCommand(command.principal(), command.idempotencyKey(), "VERIFY",
                    command.transactionId(), command.fingerprint(), verification.externalState(),
                    result.state(), result.version(), command.source(), command.reason(),
                    command.traceId(), command.occurredAt());
            return result;
        }));
    }

    public PaymentTransaction applyWebhook(ApplyWebhookCommand command) {
        return required(transactions.execute(status -> {
            repository.lockIdentity(command.providerCode(), command.eventId());
            PaymentTransactionJdbcRepository.WebhookReceipt prior =
                    repository.findWebhookReceipt(command.providerCode(), command.eventId());
            if (prior != null) {
                boolean exact = prior.payloadSha256().equals(command.payloadSha256())
                        && prior.eventType().equals(command.eventType())
                        && prior.eventCursor() == command.eventCursor()
                        && prior.providerReference().equals(command.providerReference())
                        && prior.state() == command.state()
                        && prior.transactionId().equals(command.transactionId());
                if (!exact) throw new IllegalStateException("Provider event ID reused with different payload or state");
                return repository.find(command.principal(), prior.transactionId()).orElseThrow();
            }
            return repository.applyWebhook(command);
        }));
    }

    public RefundResult refund(RefundPaymentCommand command) {
        RefundReservation reservation = required(transactions.execute(status -> {
            repository.lockIdentity(command.principal().tenantId(), command.idempotencyKey());
            PaymentTransactionJdbcRepository.StoredCommand prior = repository
                    .findCommand(command.principal(), command.idempotencyKey()).orElse(null);
            if (prior != null) {
                requireFingerprint(prior.fingerprint(), command.fingerprint(), "refund");
                PaymentTransactionJdbcRepository.RefundRecord refund = repository
                        .findRefund(command.principal(), command.idempotencyKey())
                        .orElseThrow(() -> new IllegalStateException("Refund replay record unavailable"));
                if ("SUCCEEDED".equals(refund.state())) {
                    RefundResult replay = repository.findRefundResult(command.principal(), command.idempotencyKey())
                            .orElseThrow(() -> new IllegalStateException("Refund replay result unavailable"));
                    return new RefundReservation(null, replay, null);
                }
                repository.resumeRefund(command.principal(), refund.refundId(), command.occurredAt());
                PaymentTransaction transaction = repository.find(command.principal(), refund.transactionId())
                        .orElseThrow(() -> new IllegalStateException("Refund transaction unavailable"));
                return new RefundReservation(
                        new PaymentTransactionJdbcRepository.RefundIntent(refund.refundId(), transaction),
                        null, requireProvider(transaction.providerCode()));
            }
            PaymentTransactionJdbcRepository.RefundIntent intent = repository.reserveRefund(command);
            return new RefundReservation(intent, null, requireProvider(intent.transaction().providerCode()));
        }));
        if (reservation.replay() != null) return reservation.replay();

        boolean claimed = required(transactions.execute(status ->
                repository.claimRefundProviderCall(command.principal(),
                        reservation.intent().refundId(), command.occurredAt())));
        if (!claimed) throw new IllegalStateException("Refund provider call is already in progress");

        ProviderRefundResult providerResult;
        try {
            providerResult = reservation.provider().refund(new ProviderRefundRequest(
                    reservation.intent().transaction().providerReference(), command.originalCaptureReference(),
                    command.amount(), command.idempotencyKey(), command.traceId()));
        } catch (RuntimeException failure) {
            transactions.executeWithoutResult(status -> repository.failRefund(command, reservation.intent().refundId()));
            throw new IllegalStateException("Payment provider refund failed", failure);
        }
        if (!providerResult.succeeded()) {
            transactions.executeWithoutResult(status -> repository.failRefund(command, reservation.intent().refundId()));
            throw new IllegalStateException("Payment provider rejected refund: " + providerResult.externalState());
        }
        try {
            return required(transactions.execute(status -> repository.completeRefund(
                    command, reservation.intent().refundId(), providerResult.providerRefundReference())));
        } catch (RuntimeException projectionFailure) {
            transactions.executeWithoutResult(status -> repository.failRefund(
                    command, reservation.intent().refundId()));
            throw projectionFailure;
        }
    }

    public Optional<PaymentTransaction> find(PrincipalRef principal, String transactionId) {
        return repository.find(principal, transactionId);
    }

    public Optional<PaymentTransaction> findByCheckout(PrincipalRef principal, String checkoutSessionId) {
        return repository.findByCheckout(principal, checkoutSessionId);
    }

    public Optional<PaymentTransaction> findByProviderReference(String providerCode, String providerReference) {
        return repository.findByProviderReference(providerCode, providerReference);
    }

    private PaymentProvider requireProvider(String providerCode) {
        PaymentProvider provider = providers.get(providerCode);
        if (provider == null) throw new IllegalArgumentException("Unknown payment provider: " + providerCode);
        return provider;
    }

    private static void requireFingerprint(String stored, String actual, String commandName) {
        if (!stored.equals(actual)) {
            throw new IllegalStateException("Idempotency key reused with different " + commandName + " payload");
        }
    }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalStateException("Payment transaction unexpectedly returned no result");
        return value;
    }

    private record RefundReservation(PaymentTransactionJdbcRepository.RefundIntent intent,
                                     RefundResult replay, PaymentProvider provider) {}
}
