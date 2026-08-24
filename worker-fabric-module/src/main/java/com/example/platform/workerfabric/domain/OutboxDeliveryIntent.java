package com.example.platform.workerfabric.domain;

import java.util.Objects;

/**
 * Durable delivery intent for existing outbox mechanics, never executable-task authority.
 *
 * <p>The payload is referenced by a versioned schema instead of embedding an integration product
 * or transport choice in the worker-fabric domain.
 */
public record OutboxDeliveryIntent(
        DeliveryIntentId deliveryIntentId,
        ExecutionAttemptId executionAttemptId,
        ExecutionOwnershipGeneration ownershipGeneration,
        Purpose purpose,
        String payloadSchemaReference) {

    public OutboxDeliveryIntent {
        Objects.requireNonNull(deliveryIntentId, "deliveryIntentId");
        Objects.requireNonNull(executionAttemptId, "executionAttemptId");
        Objects.requireNonNull(ownershipGeneration, "ownershipGeneration");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(payloadSchemaReference, "payloadSchemaReference");
        if (payloadSchemaReference.isBlank()) {
            throw new IllegalArgumentException("payloadSchemaReference must not be blank");
        }
    }

    public enum Purpose {
        BACKEND_SUBMISSION,
        BACKEND_CANCELLATION,
        OBSERVATION_DELIVERY
    }
}
