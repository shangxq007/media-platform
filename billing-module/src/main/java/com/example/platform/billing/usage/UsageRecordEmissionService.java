package com.example.platform.billing.usage;

import org.springframework.stereotype.Service;

/**
 * Implementation of {@link UsageRecordEmissionPort} that delegates to the outbox-backed
 * publisher, ensuring every emitted usage record is persisted and durably propagated in
 * one transaction.
 */
@Service
public class UsageRecordEmissionService implements UsageRecordEmissionPort {

    private final UsageOutboxEventPublisher publisher;

    public UsageRecordEmissionService(UsageOutboxEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public UsageRecord emit(UsageRecord record) {
        return publisher.persistUsageWithOutbox(record);
    }
}
