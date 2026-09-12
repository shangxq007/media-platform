package com.example.platform.billing.usage;

import com.example.platform.outbox.api.event.*;
import com.example.platform.shared.usage.UsageDimension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Billing/usage-owned propagation contracts; no new metering or cost authority. */
@Component
public final class BillingOutboxEvents implements OutboxEventCatalog {
    public record UsageMetered(String billableUsageId, String tenantId, String observedUsageId, String meteringRuleId, String meteringRuleVersion) {
        public UsageMetered { Objects.requireNonNull(billableUsageId); Objects.requireNonNull(tenantId); Objects.requireNonNull(observedUsageId); Objects.requireNonNull(meteringRuleId); Objects.requireNonNull(meteringRuleVersion); }
    }
    public record CostObserved(String costObservationId, String tenantId, String operationRef, CostType costType, String currencyCode, BigDecimal amountMinor) {
        public CostObserved { Objects.requireNonNull(costObservationId); Objects.requireNonNull(tenantId); Objects.requireNonNull(operationRef); Objects.requireNonNull(costType); Objects.requireNonNull(currencyCode); Objects.requireNonNull(amountMinor); }
    }
    public static final OutboxEventType<UsageMetered> USAGE_METERED = new OutboxEventType<>(
            "USAGE_METERED", 1, "BILLABLE_USAGE", UsageMetered.class, UsageMetered::billableUsageId, UsageMetered::tenantId);
    public static final OutboxEventType<CostObserved> COST_OBSERVED = new OutboxEventType<>(
            "COST_OBSERVED", 1, "PROVIDER_COST", CostObserved.class, CostObserved::costObservationId, CostObserved::tenantId);
    @Override public List<OutboxEventType<?>> types() { return List.of(USAGE_METERED, COST_OBSERVED); }
}
