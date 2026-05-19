package com.example.platform.billing.domain;

/**
 * Policy defining cost limits for a tenant or user.
 */
public record CostLimitPolicy(
        String policyId,
        String tenantId,
        String scope,
        double monthlyBudgetLimit,
        double dailyBudgetLimit,
        double softLimitPercent,
        double hardLimitPercent,
        boolean autoThrottle,
        boolean allowOverage,
        double overageTolerancePercent,
        String currency) {

    public boolean isSoftLimitExceeded(double currentSpend) {
        double limit = scope != null && scope.equals("DAILY") ? dailyBudgetLimit : monthlyBudgetLimit;
        return currentSpend >= limit * (softLimitPercent / 100.0);
    }

    public boolean isHardLimitExceeded(double currentSpend) {
        double limit = scope != null && scope.equals("DAILY") ? dailyBudgetLimit : monthlyBudgetLimit;
        return currentSpend >= limit * (hardLimitPercent / 100.0);
    }

    public boolean isOverageAllowed(double currentSpend) {
        if (allowOverage) {
            double limit = scope != null && scope.equals("DAILY") ? dailyBudgetLimit : monthlyBudgetLimit;
            double tolerance = limit * (1.0 + overageTolerancePercent / 100.0);
            return currentSpend <= tolerance;
        }
        return false;
    }
}
