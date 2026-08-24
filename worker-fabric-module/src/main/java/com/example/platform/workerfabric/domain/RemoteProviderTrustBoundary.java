package com.example.platform.workerfabric.domain;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import java.time.Instant;
import java.util.Objects;

/**
 * Provider-neutral ingress trust hooks. Implementations belong to later concrete integrations.
 */
public interface RemoteProviderTrustBoundary {

    TrustDecision authenticate(IngressContext context);

    TrustDecision validateSignature(IngressContext context);

    TrustDecision protectAgainstReplay(IngressContext context);

    TrustDecision validateTimestamp(IngressContext context);

    TrustDecision validateIdempotency(IngressContext context);

    TrustDecision validatePayloadSchema(IngressContext context);

    TrustDecision enforceRateLimit(IngressContext context);

    record IngressContext(
            ProviderBindingPin providerBindingPin,
            String providerMessageId,
            String payloadSchemaReference,
            Instant providerTimestamp,
            Instant receivedAt) {

        public IngressContext {
            Objects.requireNonNull(providerBindingPin, "providerBindingPin");
            Objects.requireNonNull(providerMessageId, "providerMessageId");
            Objects.requireNonNull(payloadSchemaReference, "payloadSchemaReference");
            Objects.requireNonNull(providerTimestamp, "providerTimestamp");
            Objects.requireNonNull(receivedAt, "receivedAt");
            if (providerMessageId.isBlank() || payloadSchemaReference.isBlank()) {
                throw new IllegalArgumentException(
                        "provider message and payload schema references must not be blank");
            }
        }
    }

    enum TrustDecision {
        ACCEPTED,
        REJECTED
    }
}
