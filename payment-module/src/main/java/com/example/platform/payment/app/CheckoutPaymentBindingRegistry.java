package com.example.platform.payment.app;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory checkout session bindings for dev/no-JDBC flows and webhook correlation.
 */
@Component
public class CheckoutPaymentBindingRegistry {

    private final Map<String, Binding> byProviderReference = new ConcurrentHashMap<>();
    private final Map<String, Binding> byCheckoutSessionId = new ConcurrentHashMap<>();

    public void register(String providerReference, Binding binding) {
        byProviderReference.put(providerReference, binding);
        byCheckoutSessionId.put(binding.checkoutSessionId(), binding);
    }

    public Optional<Binding> findByProviderReference(String providerReference) {
        return Optional.ofNullable(byProviderReference.get(providerReference));
    }

    public Optional<Binding> findByCheckoutSessionId(String checkoutSessionId) {
        return Optional.ofNullable(byCheckoutSessionId.get(checkoutSessionId));
    }

    public record Binding(
            String checkoutSessionId,
            String tenantId,
            String userId,
            String productCode,
            String providerCode) {
    }
}
