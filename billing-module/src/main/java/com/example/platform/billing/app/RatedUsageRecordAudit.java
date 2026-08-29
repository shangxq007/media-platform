package com.example.platform.billing.app;

import com.example.platform.billing.domain.RatedUsageRecord;
import org.springframework.stereotype.Component;

/**
 * The immutable rated_usage_record row is the canonical rating audit. This hook exists so
 * deployments can add a transactional outbox without introducing another rating writer.
 */
@Component
public final class RatedUsageRecordAudit implements RatedUsageAuditPort {
    @Override
    public void record(RatedUsageRecord record) {
        // The enclosing transaction has already appended the complete immutable audit record.
    }
}
