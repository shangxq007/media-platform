package com.example.platform.billing.app;

import com.example.platform.billing.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

class BudgetGuardServiceTest {

    private BudgetGuardService service;

    @BeforeEach
    void setUp() {
        service = new BudgetGuardService();
    }

    @Test
    void shouldAllowWhenNoBudgetConfigured() {
        BudgetGuardService.BudgetCheckResult result = service.checkBudget("unknown-tenant", 10.0);
        assertTrue(result.allowed());
        assertFalse(result.warning());
    }

    @Test
    void shouldCreateDefaultBudget() {
        TenantCostBudget budget = service.getOrCreateBudget("tenant-1", 100.0, "USD");
        assertNotNull(budget);
        assertEquals("tenant-1", budget.tenantId());
        assertEquals(100.0, budget.budgetLimit());
        assertEquals(0.0, budget.currentSpend());
    }

    @Test
    void shouldAllowWhenUnderBudget() {
        service.getOrCreateBudget("tenant-1", 100.0, "USD");
        BudgetGuardService.BudgetCheckResult result = service.checkBudget("tenant-1", 10.0);
        assertTrue(result.allowed());
        assertFalse(result.warning());
    }

    @Test
    void shouldWarnAtSoftLimit() {
        TenantCostBudget budget = new TenantCostBudget(
                "tenant-1", YearMonth.now(), 100.0, 85.0, 80.0,
                "USD", true, java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now());
        service.setTenantBudget("tenant-1", budget);
        BudgetGuardService.BudgetCheckResult result = service.checkBudget("tenant-1", 5.0);
        assertTrue(result.allowed());
        assertTrue(result.warning());
    }

    @Test
    void shouldDenyWhenBudgetExceeded() {
        TenantCostBudget budget = new TenantCostBudget(
                "tenant-1", YearMonth.now(), 100.0, 100.0, 80.0,
                "USD", true, java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now());
        service.setTenantBudget("tenant-1", budget);
        BudgetGuardService.BudgetCheckResult result = service.checkBudget("tenant-1", 10.0);
        assertFalse(result.allowed());
    }

    @Test
    void shouldRecordSpend() {
        service.getOrCreateBudget("tenant-1", 100.0, "USD");
        service.recordSpend("tenant-1", 25.0);
        BudgetGuardService.BudgetCheckResult result = service.checkBudget("tenant-1", 10.0);
        assertEquals(25.0, result.currentSpend());
    }

    @Test
    void shouldUpdateBudgetLimit() {
        service.getOrCreateBudget("tenant-1", 100.0, "USD");
        TenantCostBudget updated = service.updateBudget("tenant-1", 200.0);
        assertEquals(200.0, updated.budgetLimit());
    }

    @Test
    void shouldTrackAccumulator() {
        CostUsageAccumulator acc = service.getAccumulator("tenant-1");
        assertNotNull(acc);
        assertEquals("tenant-1", acc.getTenantId());
        assertEquals(0.0, acc.getTotalActualCost());
    }
}
