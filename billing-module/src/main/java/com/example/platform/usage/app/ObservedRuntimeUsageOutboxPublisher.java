package com.example.platform.usage.app;

import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.shared.usage.ObservedRuntimeUsage;
import com.example.platform.usage.infrastructure.ObservedRuntimeUsageJdbcRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomically appends an observation and its durable propagation event. */
@Service
public class ObservedRuntimeUsageOutboxPublisher {

    private final ObservedRuntimeUsageJdbcRepository repository;
    private final OutboxEventService outbox;

    public ObservedRuntimeUsageOutboxPublisher(
            ObservedRuntimeUsageJdbcRepository repository, OutboxEventService outbox) {
        this.repository = repository;
        this.outbox = outbox;
    }

    @Transactional
    public ObservedRuntimeUsage appendWithOutbox(ObservedRuntimeUsage observation) {
        ObservedRuntimeUsage saved = repository.append(observation);
        var payload = new com.example.platform.usage.api.ObservedUsageEvents.RuntimeUsageObserved(saved.observedUsageId(), saved.tenantId(),
                saved.operationRef().operationId(), saved.operationRef().attemptId(), saved.dimension());
        outbox.append(com.example.platform.usage.api.ObservedUsageEvents.RUNTIME_USAGE_OBSERVED.append(saved.tenantId(), payload,
                "observed-usage:" + saved.tenantId() + ":" + saved.idempotencyKey()));
        return saved;
    }
}
