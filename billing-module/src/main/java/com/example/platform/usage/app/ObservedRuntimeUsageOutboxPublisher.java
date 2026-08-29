package com.example.platform.usage.app;

import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.shared.usage.ObservedRuntimeUsage;
import com.example.platform.usage.infrastructure.ObservedRuntimeUsageJdbcRepository;
import java.util.LinkedHashMap;
import java.util.Map;
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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("observedUsageId", saved.observedUsageId());
        payload.put("tenantId", saved.tenantId());
        payload.put("operationRef", saved.operationRef().operationId());
        payload.put("attemptRef", saved.operationRef().attemptId());
        payload.put("dimension", saved.dimension().name());
        outbox.appendEvent(
                "OBSERVED_RUNTIME_USAGE",
                saved.observedUsageId(),
                "RUNTIME_USAGE_OBSERVED",
                1,
                payload,
                "observed-usage:" + saved.tenantId() + ":" + saved.idempotencyKey());
        return saved;
    }
}
