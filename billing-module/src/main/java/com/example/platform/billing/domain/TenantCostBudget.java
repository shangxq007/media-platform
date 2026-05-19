package com.example.platform.billing.domain;

import java.time.OffsetDateTime;
import java.time.YearMonth;

/**
 * Tenant-level cost budget configuration.
 */
public record TenantCostBudget(
        String tenantId,
        YearMonth budgetMonth,
        double budgetLimit,
        double currentSpend,
        double softLimitPercent,
        String currency,
        boolean autoThrottleEnabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public double remainingBudget() {
        return Math.max(0, budgetLimit - currentSpend);
    }

    public double spendRatio() {
        return budgetLimit > 0 ? currentSpend / budgetLimit : 0.0;
    }

    public boolean isSoftLimitExceeded() {
        return spendRatio() >= (softLimitPercent / 100.0);
    }

    public boolean isHardLimitExceeded() {
        return currentSpend >= budgetLimit;
    }

    public boolean isOverageAllowed(double projectedSpend) {
        return projectedSpend <= budgetLimit * 1.1;
    }

    public TenantCostBudget withSpend(double newSpend) {
        return new TenantCostBudget(tenantId, budgetMonth, budgetLimit, newSpend,
                softLimitPercent, currency, autoThrottleEnabled, createdAt, OffsetDateTime.now());
    }
}
