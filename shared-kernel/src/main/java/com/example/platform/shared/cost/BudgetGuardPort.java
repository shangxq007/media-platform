package com.example.platform.shared.cost;

/**
 * Port interface for budget guard, implemented by billing module.
 */
public interface BudgetGuardPort {
    BudgetCheckResult checkBudget(String tenantId, double estimatedCost);

    record BudgetCheckResult(
            boolean allowed,
            boolean warning,
            double currentSpend,
            double budgetLimit,
            double remainingBudget,
            String message) {}
}
