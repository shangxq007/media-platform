package com.example.platform.payment.app;

import com.example.platform.payment.domain.*;
import com.example.platform.payment.infrastructure.PaymentAttemptRepository;
import com.example.platform.payment.infrastructure.ProviderWebhookEventRepository;
import com.example.platform.shared.Ids;
import com.example.platform.shared.payment.PaymentSucceededPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentGatewayService {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayService.class);

    private final Map<String, PaymentProvider> providers;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ProviderWebhookEventRepository webhookEventRepository;
    private final CheckoutPaymentBindingRegistry bindingRegistry;
    private final ObjectProvider<PaymentSucceededPort> paymentSucceededPort;

    public PaymentGatewayService(
            List<PaymentProvider> providers,
            @Autowired(required = false) PaymentAttemptRepository paymentAttemptRepository,
            @Autowired(required = false) ProviderWebhookEventRepository webhookEventRepository,
            CheckoutPaymentBindingRegistry bindingRegistry,
            ObjectProvider<PaymentSucceededPort> paymentSucceededPort) {
        this.providers = providers.stream()
                .collect(java.util.stream.Collectors.toMap(p -> p.code().value(), p -> p));
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.bindingRegistry = bindingRegistry;
        this.paymentSucceededPort = paymentSucceededPort;
    }

    public CheckoutResult createCheckout(CheckoutCommand command) {
        PaymentProvider provider = resolveProvider(command.canonicalProductCode());
        CheckoutResult result = provider.createCheckout(command);

        if (command.checkoutSessionId() != null) {
            bindingRegistry.register(result.providerReference(), new CheckoutPaymentBindingRegistry.Binding(
                    command.checkoutSessionId(),
                    command.tenantId(),
                    command.userId(),
                    command.canonicalProductCode(),
                    provider.code().value()));
        }

        if (paymentAttemptRepository != null) {
            try {
                String attemptId = Ids.newId("pay");
                paymentAttemptRepository.save(attemptId, null, provider.code().value(),
                        result.providerReference(), "INITIATED", command.amountMinor(), command.currencyCode(),
                        command.checkoutSessionId(), null);
            } catch (Exception e) {
                log.warn("Failed to persist payment attempt for checkout: {}", e.getMessage());
            }
        }

        return result;
    }

    public PaymentVerificationResult verifyPayment(VerifyPaymentCommand command) {
        PaymentProvider provider = resolveProviderByReference(command.providerReference());
        PaymentVerificationResult result = provider.verifyPayment(command);
        persistVerification(provider, command, result);
        return result;
    }

    public PaymentVerificationResult confirm(String providerCode, String providerReference, String payload) {
        PaymentProvider provider = providers.getOrDefault(providerCode,
                new com.example.platform.payment.infrastructure.NoopStripePaymentProvider());
        PaymentVerificationResult result = provider.verifyPayment(new VerifyPaymentCommand(providerReference, payload));
        persistVerification(provider, new VerifyPaymentCommand(providerReference, payload), result);
        return result;
    }

    public WebhookParseResult parseWebhook(String providerCode, Map<String, String> headers, String body) {
        PaymentProvider provider = providers.getOrDefault(providerCode,
                new com.example.platform.payment.infrastructure.NoopStripePaymentProvider());
        WebhookParseResult result = enrichFromBinding(provider.parseWebhook(headers, body));

        if (webhookEventRepository != null) {
            String webhookEventKey = providerCode + ":" + result.externalReference() + ":" + result.eventType();
            try {
                if (webhookEventRepository.existsByKey(webhookEventKey)) {
                    log.info("Webhook event already processed, skipping: {}", webhookEventKey);
                    return result;
                }
                webhookEventRepository.save(providerCode, webhookEventKey,
                        result.eventType(), result.eventVersion(),
                        result.validSignature(), body);
            } catch (Exception e) {
                log.warn("Failed to persist webhook event: {}", e.getMessage());
            }
        }

        dispatchPaymentSucceeded(providerCode, result);
        return result;
    }

    private WebhookParseResult enrichFromBinding(WebhookParseResult result) {
        if (result.checkoutSessionId() != null && !result.checkoutSessionId().isBlank()) {
            return result;
        }
        return bindingRegistry.findByProviderReference(result.externalReference())
                .map(binding -> new WebhookParseResult(
                        result.eventType(),
                        result.eventVersion(),
                        result.externalReference(),
                        result.validSignature(),
                        result.canonicalStatus() != null ? result.canonicalStatus() : "paid",
                        binding.checkoutSessionId(),
                        binding.tenantId(),
                        binding.userId()))
                .orElse(result);
    }

    private void dispatchPaymentSucceeded(String providerCode, WebhookParseResult result) {
        if (!result.paymentSucceeded()) {
            return;
        }
        paymentSucceededPort.ifAvailable(port -> {
            try {
                port.onPaymentSucceeded(new PaymentSucceededPort.PaymentSucceededEvent(
                        providerCode,
                        result.externalReference(),
                        result.checkoutSessionId(),
                        result.tenantId(),
                        result.userId(),
                        result.canonicalStatus() != null ? result.canonicalStatus() : "paid"));
            } catch (Exception e) {
                log.error("PaymentSucceededPort failed for session {}: {}",
                        result.checkoutSessionId(), e.getMessage(), e);
                throw new IllegalStateException("Payment fulfillment failed", e);
            }
        });
    }

    private void persistVerification(PaymentProvider provider, VerifyPaymentCommand command,
                                   PaymentVerificationResult result) {
        if (paymentAttemptRepository == null) {
            return;
        }
        try {
            String attemptId = Ids.newId("pay");
            paymentAttemptRepository.save(attemptId, null, provider.code().value(),
                    command.providerReference(), result.canonicalStatus(),
                    null, null, command.rawPayload(),
                    result.verified() + ":" + result.externalState());
        } catch (Exception e) {
            log.warn("Failed to persist payment verification: {}", e.getMessage());
        }
    }

    private PaymentProvider resolveProvider(String productCode) {
        if (productCode != null && productCode.contains("hs")) {
            return providers.getOrDefault("hyperswitch",
                    new com.example.platform.payment.infrastructure.NoopHyperswitchPaymentProvider());
        }
        return providers.getOrDefault("stripe",
                new com.example.platform.payment.infrastructure.NoopStripePaymentProvider());
    }

    private PaymentProvider resolveProviderByReference(String providerReference) {
        if (providerReference != null && providerReference.startsWith("hs")) {
            return providers.getOrDefault("hyperswitch",
                    new com.example.platform.payment.infrastructure.NoopHyperswitchPaymentProvider());
        }
        return providers.getOrDefault("stripe",
                new com.example.platform.payment.infrastructure.NoopStripePaymentProvider());
    }
}
