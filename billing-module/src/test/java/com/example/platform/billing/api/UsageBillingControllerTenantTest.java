package com.example.platform.billing.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.platform.billing.api.dto.*;
import com.example.platform.billing.app.*;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsageBillingControllerTenantTest {

    @Mock
    private UsageMeteringService usageMeteringService;

    @Mock
    private RatingEngine ratingEngine;

    @Mock
    private BillingLedgerService billingLedgerService;

    @Mock
    private BillingDecisionService billingDecisionService;

    @Mock
    private PricingRuleService pricingRuleService;

    private UsageBillingController controller;

    @BeforeEach
    void setUp() {
        controller = new UsageBillingController(usageMeteringService, ratingEngine,
                billingLedgerService, billingDecisionService, pricingRuleService);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listUsageUsesTenantContext() {
        TenantContext.set("tenant-a");
        when(usageMeteringService.getUsage("tenant-a", null))
                .thenReturn(List.of());

        controller.listUsage(null, null);

        verify(usageMeteringService).getUsage("tenant-a", null);
        verify(usageMeteringService, never()).getUsage("tenant-b", null);
    }

    @Test
    void listUsageRejectsMismatchedTenantId() {
        TenantContext.set("tenant-a");
        assertThrows(SecurityException.class,
                () -> listUsageWithTenantId("tenant-b"));
    }

    @Test
    void listUsageRejectsWithoutTenantContext() {
        TenantContext.clear();
        assertThrows(IllegalArgumentException.class,
                () -> controller.listUsage(null, null));
    }

    @Test
    void getLedgerUsesTenantContext() {
        TenantContext.set("tenant-a");
        when(billingLedgerService.getLedger("tenant-a"))
                .thenReturn(List.of());

        controller.getLedger(null);

        verify(billingLedgerService).getLedger("tenant-a");
        verify(billingLedgerService, never()).getLedger("tenant-b");
    }

    @Test
    void getLedgerRejectsMismatchedTenantId() {
        TenantContext.set("tenant-a");
        assertThrows(SecurityException.class,
                () -> controller.getLedger("tenant-b"));
    }

    @Test
    void fakeRequestParamTenantIdDoesNotChangeTenant() {
        TenantContext.set("tenant-a");
        assertThrows(SecurityException.class,
                () -> controller.listUsage("tenant-b", "render"));
    }

    private void listUsageWithTenantId(String tenantId) {
        controller.listUsage(tenantId, null);
    }
}
