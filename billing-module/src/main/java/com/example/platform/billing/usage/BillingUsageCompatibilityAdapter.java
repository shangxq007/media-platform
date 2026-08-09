package com.example.platform.billing.usage;

import com.example.platform.billing.domain.UsageRecord;
import org.springframework.stereotype.Service;

/**
 * NOT CANONICAL AUTHORITY — transitional compatibility projection for existing billing
 * consumers.
 *
 * <p>Projects a canonical {@code billing.usage.UsageRecord} (typed {@link UsageQuantity})
 * onto the legacy {@code billing.domain.UsageRecord} shape consumed by
 * {@code RatingEngine} / {@code BillingCycleService}. The {@code quantity} double produced
 * here is a legacy projection ONLY; canonical usage authority is
 * {@link UsageRecord} with its typed quantity, and the canonical path never depends on the
 * double.</p>
 *
 * <p>This adapter exists so existing billing consumers keep working while canonical usage
 * authority lives in {@code billing.usage}. It does NOT define or invent execution usage
 * facts.</p>
 */
@Service
public class BillingUsageCompatibilityAdapter {

    /**
     * Adapt a canonical usage record to the legacy {@code billing.domain.UsageRecord}
     * shape for existing rating/billing consumers.
     *
     * @param canonical the canonical usage record (authority)
     * @return a legacy-shaped record for transitional consumers
     */
    public UsageRecord adapt(com.example.platform.billing.usage.UsageRecord canonical) {
        return new UsageRecord(
                canonical.recordId(),
                canonical.tenantId(),
                null,
                null,
                canonical.dimension().name(),
                (double) canonical.quantity().baseUnits(),
                canonical.quantity().unit().name(),
                canonical.recordedAt(),
                canonical.idempotencyKey());
    }
}
