package com.example.platform.billing.usage;

/**
 * Port for emitting canonical usage records into the platform.
 *
 * <p>This is the ONLY entry point that producers (AI, Render) use to emit usage. It
 * deliberately hides the persistence and outbox-propagation mechanics — callers depend
 * on this port, never on the {@link UsageRecordJdbcRepository} directly.</p>
 */
public interface UsageRecordEmissionPort {

    /**
     * Emits a canonical usage record: persisted and durably propagated via the outbox
     * in a single transaction.
     *
     * @param record the canonical usage record to emit
     * @return the effective persisted record
     */
    UsageRecord emit(UsageRecord record);
}
