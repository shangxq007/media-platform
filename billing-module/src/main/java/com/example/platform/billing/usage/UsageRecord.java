package com.example.platform.billing.usage;

import com.example.platform.usage.api.UsageDimension;
import com.example.platform.usage.api.UsageQuantity;
import java.time.Instant;

/**
 * Deprecated read-only dashboard projection retained until the later surface cleanup phase.
 *
 * <p>It has no factory, writer, persistence table, or rating overload. The only production
 * implementation is {@link BillableUsage}; operational truth is {@code ObservedRuntimeUsage}.</p>
 */
@Deprecated
public interface UsageRecord {
    String recordId();
    String tenantId();
    UsageDimension dimension();
    UsageQuantity quantity();
    Instant recordedAt();
}
