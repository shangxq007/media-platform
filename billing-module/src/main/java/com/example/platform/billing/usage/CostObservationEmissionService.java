package com.example.platform.billing.usage;

import com.example.platform.billing.infrastructure.ProviderCostObservationJdbcRepository;
import com.example.platform.outbox.app.OutboxEventService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;




/**
 * Durable propagation boundary for canonical provider cost observations.
 *
 * <p>Within a single transaction, persists the {@link ProviderCostObservation} and appends
 * a corresponding outbox event. Atomic with the insert: an outbox failure rolls the cost
 * insert back, leaving no half-state.</p>
 *
 * <p>The outbox payload is bounded and carries stable, non-secret data only:
 * costObservationId, tenantId, operationRef, costType, currencyCode, amountMinor.</p>
 */
@Service
public class CostObservationEmissionService {

    private final ProviderCostObservationJdbcRepository providerCostObservationJdbcRepository;
    private final OutboxEventService outboxEventService;

    public CostObservationEmissionService(
            ProviderCostObservationJdbcRepository providerCostObservationJdbcRepository,
            OutboxEventService outboxEventService) {
        this.providerCostObservationJdbcRepository = providerCostObservationJdbcRepository;
        this.outboxEventService = outboxEventService;
    }

    /**
     * Persists the provider cost observation and appends a durable outbox event in the
     * same transaction.
     *
     * @param observation the canonical provider cost observation to persist
     * @return the effective persisted observation (inserted, or the pre-existing one on idempotency conflict)
     */
    @Transactional
    public ProviderCostObservation persistCostWithOutbox(ProviderCostObservation observation) {
        ProviderCostObservation saved = providerCostObservationJdbcRepository.insert(observation);

        var payload = new BillingOutboxEvents.CostObserved(saved.observationId(), saved.tenantId(),
                saved.operationRef().operationId(), saved.costType(), saved.currencyCode(), saved.amountMinor());
        outboxEventService.append(BillingOutboxEvents.COST_OBSERVED.append(saved.tenantId(), payload, saved.idempotencyKey()));

        return saved;
    }
}
