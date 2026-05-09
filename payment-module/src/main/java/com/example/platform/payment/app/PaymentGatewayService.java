package com.example.platform.payment.app;

import com.example.platform.payment.domain.*;
import com.example.platform.payment.infrastructure.PaymentAttemptRepository;
import com.example.platform.payment.infrastructure.ProviderWebhookEventRepository;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PaymentGatewayService {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayService.class);

    private final Map<String, PaymentProvider> providers;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ProviderWebhookEventRepository webhookEventRepository;

    public PaymentGatewayService(List<PaymentProvider> providers,
                                 @Autowired(required = false) PaymentAttemptRepository paymentAttemptRepository,
                                 @Autowired(required = false) ProviderWebhookEventRepository webhookEventRepository) {
        this.providers = providers.stream()
                .collect(java.util.stream.Collectors.toMap(p -> p.code().value(), p -> p));
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.webhookEventRepository = webhookEventRepository;
    }

    public CheckoutResult createCheckout(CheckoutCommand command) {
        PaymentProvider provider = resolveProvider(command.canonicalProductCode());
        CheckoutResult result = provider.createCheckout(command);

        if (paymentAttemptRepository != null) {
            try {
                String attemptId = Ids.newId("pay");
                paymentAttemptRepository.save(attemptId, null, provider.code().value(),
                        result.providerReference(), "INITIATED", null, null,
                        null, null);
                log.debug("Persisted payment attempt: {} for provider: {}", attemptId, provider.code().value());
            } catch (Exception e) {
                log.warn("Failed to persist payment attempt for checkout: {}", e.getMessage());
            }
        }

        return result;
    }

    public PaymentVerificationResult verifyPayment(VerifyPaymentCommand command) {
        PaymentProvider provider = resolveProviderByReference(command.providerReference());
        PaymentVerificationResult result = provider.verifyPayment(command);

        if (paymentAttemptRepository != null) {
            try {
                String attemptId = Ids.newId("pay");
                paymentAttemptRepository.save(attemptId, null, provider.code().value(),
                        command.providerReference(), result.canonicalStatus(),
                        null, null, command.rawPayload(),
                        result.verified() + ":" + result.externalState());
                log.debug("Persisted payment verification: {} status: {}", attemptId, result.canonicalStatus());
            } catch (Exception e) {
                log.warn("Failed to persist payment verification: {}", e.getMessage());
            }
        }

        return result;
    }

    public PaymentVerificationResult confirm(String providerCode, String providerReference, String payload) {
        PaymentProvider provider = providers.getOrDefault(providerCode,
                new com.example.platform.payment.infrastructure.NoopStripePaymentProvider());
        PaymentVerificationResult result = provider.verifyPayment(new VerifyPaymentCommand(providerReference, payload));

        if (paymentAttemptRepository != null) {
            try {
                String attemptId = Ids.newId("pay");
                paymentAttemptRepository.save(attemptId, null, providerCode,
                        providerReference, result.canonicalStatus(),
                        null, null, payload,
                        result.verified() + ":" + result.externalState());
            } catch (Exception e) {
                log.warn("Failed to persist payment confirmation: {}", e.getMessage());
            }
        }

        return result;
    }

    public WebhookParseResult parseWebhook(String providerCode, Map<String, String> headers, String body) {
        PaymentProvider provider = providers.getOrDefault(providerCode,
                new com.example.platform.payment.infrastructure.NoopStripePaymentProvider());
        WebhookParseResult result = provider.parseWebhook(headers, body);

        if (webhookEventRepository != null) {
            // Idempotency check: skip if already processed
            String webhookEventKey = providerCode + ":" + result.externalReference() + ":" + result.eventType();
            try {
                if (webhookEventRepository.existsByKey(webhookEventKey)) {
                    log.info("Webhook event already processed, skipping: {}", webhookEventKey);
                    return result;
                }
            } catch (Exception e) {
                log.warn("Failed to check webhook idempotency: {}", e.getMessage());
            }

            // Persist webhook event for idempotency
            try {
                webhookEventRepository.save(providerCode, webhookEventKey,
                        result.eventType(), result.eventVersion(),
                        result.validSignature(), body);
                log.debug("Persisted webhook event: {}", webhookEventKey);
            } catch (Exception e) {
                log.warn("Failed to persist webhook event: {}", e.getMessage());
            }
        }

        return result;
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
