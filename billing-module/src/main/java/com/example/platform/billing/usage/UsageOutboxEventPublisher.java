package com.example.platform.billing.usage;

import com.example.platform.billing.infrastructure.UsageRecordJdbcRepository;
import com.example.platform.outbox.app.OutboxEventService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Durable propagation boundary for canonical usage records.
 *
 * <p>Within a single transaction, persists the canonical {@link UsageRecord} and appends
 * a corresponding outbox event. The two writes are atomic: if the outbox append fails,
 * the usage insert is rolled back — no half-state. The outbox event is the durable
 * propagation guarantee that downstream consumers (billing, quota) rely on.</p>
 *
 * <p>The outbox payload is intentionally bounded and contains stable, non-secret data
 * only (usageRecordId, tenantId, operationRef, dimension). It never carries secrets,
 * provider raw payloads, or authorization material.</p>
 */
@Service
public class UsageOutboxEventPublisher {

    private final UsageRecordJdbcRepository usageRecordJdbcRepository;
    private final OutboxEventService outboxEventService;

    public UsageOutboxEventPublisher(UsageRecordJdbcRepository usageRecordJdbcRepository,
            OutboxEventService outboxEventService) {
        this.usageRecordJdbcRepository = usageRecordJdbcRepository;
        this.outboxEventService = outboxEventService;
    }

    /**
     * Persists the usage record and appends a durable outbox event in the same transaction.
     *
     * @param record the canonical usage record to persist
     * @return the effective persisted record (inserted, or the pre-existing one on idempotency conflict)
     */
    @Transactional
    public UsageRecord persistUsageWithOutbox(UsageRecord record) {
        UsageRecord saved = usageRecordJdbcRepository.insert(record);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("usageRecordId", record.recordId());
        payload.put("tenantId", record.tenantId());
        payload.put("operationRef", record.operationRef().operationId());
        payload.put("dimension", record.dimension().name());

        outboxEventService.appendEvent(
                "USAGE_RECORD",
                record.recordId(),
                "USAGE_RECORDED",
                1,
                payload,
                record.idempotencyKey());

        return saved;
    }
}
