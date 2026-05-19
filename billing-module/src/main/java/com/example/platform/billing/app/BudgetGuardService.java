package com.example.platform.billing.app;

import com.example.platform.billing.domain.*;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guards tenant and user budgets against cost overruns.
 */
@Service
public class BudgetGuardService {

    private static final Logger log = LoggerFactory.getLogger(BudgetGuardService.class);

    private final ConcurrentHashMap<String, TenantCostBudget> tenantBudgets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CostUsageAccumulator> tenantAccumulators = new ConcurrentHashMap<>();

    /**
     * Check if a tenant has sufficient budget for the estimated cost.
     */
    public BudgetCheckResult checkBudget(String tenantId, double estimatedCost) {
        TenantCostBudget budget = tenantBudgets.get(tenantId);
        if (budget == null) {
            // No budget configured, allow but warn
            return BudgetCheckResult.allowed(0, 0, 0);
        }

        double projectedSpend = budget.currentSpend() + estimatedCost;
        double remaining = budget.budgetLimit() - projectedSpend;

        if (budget.isHardLimitExceeded()) {
            return BudgetCheckResult.denied(budget.currentSpend(), budget.budgetLimit(), remaining,
                    "Budget limit exceeded. Current spend: " + budget.currentSpend()
                            + ", limit: " + budget.budgetLimit());
        }

        if (budget.isSoftLimitExceeded()) {
            return BudgetCheckResult.warn(budget.currentSpend(), budget.budgetLimit(), remaining,
                    "Approaching budget limit. Current spend: " + budget.currentSpend()
                            + ", limit: " + budget.budgetLimit());
        }

        return BudgetCheckResult.allowed(budget.currentSpend(), budget.budgetLimit(), remaining);
    }

    /**
     * Record a finalized cost against the tenant budget.
     */
    public void recordSpend(String tenantId, double actualCost) {
        TenantCostBudget budget = tenantBudgets.get(tenantId);
        if (budget != null) {
            tenantBudgets.put(tenantId, budget.withSpend(budget.currentSpend() + actualCost));
            log.info("BudgetGuardService: recorded spend {} for tenant={}, new total={}",
                    actualCost, tenantId, budget.currentSpend() + actualCost);
        }
    }

    /**
     * Get or create a tenant budget.
     */
    public TenantCostBudget getOrCreateBudget(String tenantId, double budgetLimit, String currency) {
        return tenantBudgets.computeIfAbsent(tenantId, id ->
                new TenantCostBudget(id, YearMonth.now(), budgetLimit, 0.0, 80.0,
                        currency, true, OffsetDateTime.now(), OffsetDateTime.now()));
    }

    /**
     * Update a tenant budget limit.
     */
    public TenantCostBudget updateBudget(String tenantId, double newLimit) {
        TenantCostBudget existing = tenantBudgets.get(tenantId);
        if (existing != null) {
            TenantCostBudget updated = new TenantCostBudget(
                    tenantId, existing.budgetMonth(), newLimit, existing.currentSpend(),
                    existing.softLimitPercent(), existing.currency(), existing.autoThrottleEnabled(),
                    existing.createdAt(), OffsetDateTime.now());
            tenantBudgets.put(tenantId, updated);
            return updated;
        }
        return getOrCreateBudget(tenantId, newLimit, "USD");
    }

    /**
     * Get usage accumulator for a tenant.
     */
    public CostUsageAccumulator getAccumulator(String tenantId) {
        return tenantAccumulators.computeIfAbsent(tenantId, CostUsageAccumulator::new);
    }

    public void setTenantBudget(String tenantId, TenantCostBudget budget) {
        tenantBudgets.put(tenantId, budget);
    }

    public record BudgetCheckResult(
            boolean allowed,
            boolean warning,
            double currentSpend,
            double budgetLimit,
            double remainingBudget,
            String message) {

        public static BudgetCheckResult allowed(double currentSpend, double budgetLimit, double remaining) {
            return new BudgetCheckResult(true, false, currentSpend, budgetLimit, remaining, null);
        }

        public static BudgetCheckResult warn(double currentSpend, double budgetLimit, double remaining, String message) {
            return new BudgetCheckResult(true, true, currentSpend, budgetLimit, remaining, message);
        }

        public static BudgetCheckResult denied(double currentSpend, double budgetLimit, double remaining, String message) {
            return new BudgetCheckResult(false, false, currentSpend, budgetLimit, remaining, message);
        }
    }
}
