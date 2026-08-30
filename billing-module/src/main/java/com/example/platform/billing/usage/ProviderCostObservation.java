package com.example.platform.billing.usage;

import com.example.platform.shared.Ids;
import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.shared.usage.OperationRef;
import com.example.platform.shared.usage.ProviderRef;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable provider cost observation.
 *
 * <p>Cost is stored as a {@link BigDecimal} minor base unit at scale 0 (NOT a double).
 * Provenance ({@link CostType}) and source are required so cost authority is never
 * ambiguous.</p>
 */
public record ProviderCostObservation(
        String observationId,
        String tenantId,
        String projectId,
        CanonicalActorRef actorRef,
        OperationRef operationRef,
        String executionRef,
        ProviderRef providerRef,
        String capability,
        BigDecimal amountMinor,
        String currencyCode,
        CostType costType,
        String source,
        Instant observedAt,
        String usageRecordId,
        String idempotencyKey) {

    /**
     * Canonical factory. Generates the {@code observationId} and validates all required
     * fields; {@code amountMinor} must be at scale 0 (minor base unit).
     *
     * @throws IllegalArgumentException if any required field is blank/null or
     *                                  {@code amountMinor} is not scale 0
     */
    public static ProviderCostObservation record(
            String tenantId,
            String projectId,
            CanonicalActorRef actorRef,
            OperationRef operationRef,
            String executionRef,
            ProviderRef providerRef,
            String capability,
            BigDecimal amountMinor,
            String currencyCode,
            CostType costType,
            String source,
            Instant observedAt,
            String usageRecordId,
            String idempotencyKey) {

        Objects.requireNonNull(operationRef, "operationRef must not be null");
        Objects.requireNonNull(providerRef, "providerRef must not be null");
        Objects.requireNonNull(amountMinor, "amountMinor must not be null");
        Objects.requireNonNull(costType, "costType must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");

        if (amountMinor.scale() != 0) {
            throw new IllegalArgumentException(
                    "amountMinor must be at scale 0 (minor base unit), was scale " + amountMinor.scale());
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (currencyCode == null || currencyCode.isBlank()) {
            throw new IllegalArgumentException("currencyCode must not be blank");
        }
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("source must not be blank");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey must not be blank");
        }

        return new ProviderCostObservation(
                Ids.newId("pco"),
                tenantId,
                projectId,
                actorRef,
                operationRef,
                executionRef,
                providerRef,
                capability,
                amountMinor,
                currencyCode,
                costType,
                source,
                observedAt,
                usageRecordId,
                idempotencyKey);
    }
}
