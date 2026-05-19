package com.example.platform.billing.app;

import com.example.platform.shared.cost.BudgetGuardPort;
import org.springframework.stereotype.Component;

/**
 * Adapter that exposes BudgetGuardService as BudgetGuardPort.
 */
@Component
public class BudgetGuardPortAdapter implements BudgetGuardPort {

    private final BudgetGuardService budgetGuardService;

    public BudgetGuardPortAdapter(BudgetGuardService budgetGuardService) {
        this.budgetGuardService = budgetGuardService;
    }

    @Override
    public BudgetCheckResult checkBudget(String tenantId, double estimatedCost) {
        BudgetGuardService.BudgetCheckResult result = budgetGuardService.checkBudget(tenantId, estimatedCost);
        return new BudgetCheckResult(result.allowed(), result.warning(),
                result.currentSpend(), result.budgetLimit(), result.remainingBudget(), result.message());
    }
}
