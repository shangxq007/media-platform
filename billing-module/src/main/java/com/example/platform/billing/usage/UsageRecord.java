package com.example.platform.billing.usage;

import com.example.platform.shared.Ids;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, append-only canonical usage fact.
 *
 * <p>Represents a typed, measurable consumption observation for an operation/attempt.
 * Corrections are expressed via correction/adjustment/superseding relations — never by
 * mutating this record. The legacy {@code usage_record.quantity double} column is NOT the
 * canonical authority; the typed {@link UsageQuantity} is.</p>
 */
public record UsageRecord(
        String recordId,
        String tenantId,
        String projectId,
        CanonicalActorRef actorRef,
        OperationRef operationRef,
        String executionRef,
        ProviderRef providerRef,
        String capability,
        UsageDimension dimension,
        UsageQuantity quantity,
        Instant occurredAt,
        Instant observedAt,
        Instant recordedAt,
        String idempotencyKey,
        String provenance,
        String source) {

    /** Allowed usage-observation provenance values. */
    public static final Set<String> VALID_PROVENANCE = Set.of("REPORTED", "ESTIMATED", "DERIVED");

    /**
     * Canonical factory. Generates the {@code recordId} and validates all required fields
     * and the dimension/unit pairing.
     *
     * @throws IllegalArgumentException if any required field is blank/null or the
     *                                  dimension/unit pairing is illegal
     */
    public static UsageRecord record(
            String tenantId,
            String projectId,
            CanonicalActorRef actorRef,
            OperationRef operationRef,
            String executionRef,
            ProviderRef providerRef,
            String capability,
            UsageDimension dimension,
            UsageQuantity quantity,
            Instant occurredAt,
            Instant observedAt,
            Instant recordedAt,
            String idempotencyKey,
            String provenance,
            String source) {

        Objects.requireNonNull(operationRef, "operationRef must not be null");
        Objects.requireNonNull(quantity, "quantity must not be null");
        Objects.requireNonNull(dimension, "dimension must not be null");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");

        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }
        if (provenance == null || provenance.isBlank() || !VALID_PROVENANCE.contains(provenance)) {
            throw new IllegalArgumentException("provenance must be one of " + VALID_PROVENANCE);
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }

        // Enforce the canonical dimension/unit pairing.
        UsageUnit.validate(dimension, quantity.unit());

        return new UsageRecord(
                Ids.newId("usg"),
                tenantId,
                projectId,
                actorRef,
                operationRef,
                executionRef,
                providerRef,
                capability,
                dimension,
                quantity,
                occurredAt,
                observedAt,
                recordedAt,
                idempotencyKey,
                provenance,
                source);
    }
}
