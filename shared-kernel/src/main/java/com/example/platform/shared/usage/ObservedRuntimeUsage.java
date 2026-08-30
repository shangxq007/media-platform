package com.example.platform.shared.usage;

import com.example.platform.shared.Ids;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable operational truth emitted by runtime producers.
 *
 * <p>This neutral fact contains no price, billability, entitlement, subscription, quota,
 * credit, or commercial-decision field. Corrections are new observations; existing facts are
 * append-only.</p>
 */
public record ObservedRuntimeUsage(
        String observedUsageId,
        String tenantId,
        String projectId,
        CanonicalActorRef principalRef,
        OperationRef operationRef,
        String executionRef,
        ProviderRef providerRef,
        String capability,
        UsageDimension dimension,
        UsageQuantity quantity,
        RuntimeOutcome outcome,
        Instant occurredAt,
        Instant observedAt,
        Instant recordedAt,
        UsageProvenance provenance,
        String source,
        String sourceReference,
        String traceId,
        String idempotencyKey) {

    public ObservedRuntimeUsage {
        observedUsageId = requireNonBlank(observedUsageId, "observedUsageId");
        tenantId = requireNonBlank(tenantId, "tenantId");
        projectId = optionalNonBlank(projectId, "projectId");
        Objects.requireNonNull(principalRef, "principalRef must not be null");
        Objects.requireNonNull(operationRef, "operationRef must not be null");
        requireNonBlank(operationRef.attemptId(), "operationRef.attemptId");
        executionRef = optionalNonBlank(executionRef, "executionRef");
        Objects.requireNonNull(providerRef, "providerRef must not be null");
        capability = requireNonBlank(capability, "capability");
        Objects.requireNonNull(dimension, "dimension must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        UsageUnit.validate(dimension, quantity.unit());
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");
        Objects.requireNonNull(provenance, "provenance must not be null");
        source = requireNonBlank(source, "source");
        sourceReference = requireNonBlank(sourceReference, "sourceReference");
        traceId = requireNonBlank(traceId, "traceId");
        idempotencyKey = requireNonBlank(idempotencyKey, "idempotencyKey");
    }

    public static ObservedRuntimeUsage observe(
            String tenantId,
            String projectId,
            CanonicalActorRef principalRef,
            OperationRef operationRef,
            String executionRef,
            ProviderRef providerRef,
            String capability,
            UsageDimension dimension,
            UsageQuantity quantity,
            RuntimeOutcome outcome,
            Instant occurredAt,
            Instant observedAt,
            Instant recordedAt,
            UsageProvenance provenance,
            String source,
            String sourceReference,
            String traceId,
            String idempotencyKey) {
        return new ObservedRuntimeUsage(
                Ids.newId("oru"), tenantId, projectId, principalRef, operationRef,
                executionRef, providerRef, capability, dimension, quantity, outcome,
                occurredAt, observedAt, recordedAt, provenance, source, sourceReference,
                traceId, idempotencyKey);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be null/blank");
        }
        return value;
    }

    private static String optionalNonBlank(String value, String field) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank when present");
        }
        return value;
    }
}
