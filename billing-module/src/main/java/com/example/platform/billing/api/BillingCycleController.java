package com.example.platform.billing.api;

import com.example.platform.billing.app.BillingCycleService;
import com.example.platform.billing.app.SubscriptionBillingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/billing/cycles")
public class BillingCycleController {

    private final BillingCycleService billingCycleService;
    private final SubscriptionBillingService subscriptionBillingService;

    public BillingCycleController(BillingCycleService billingCycleService,
                                  SubscriptionBillingService subscriptionBillingService) {
        this.billingCycleService = billingCycleService;
        this.subscriptionBillingService = subscriptionBillingService;
    }

    @PostMapping("/run")
    public BillingCycleService.BillingCycleResult runCycle(
            @RequestParam(required = false) String tenantId,
            @RequestParam String userId) {
        String effectiveTenant = resolveTenantId(tenantId);
        return billingCycleService.runCycle(effectiveTenant, userId);
    }

    @PostMapping("/process-due")
    public List<BillingCycleService.BillingCycleResult> processDueSubscriptions() {
        subscriptionBillingService.processBillingCycle();
        return subscriptionBillingService.listActiveSubscriptionsAllTenants().stream()
                .map(c -> billingCycleService.runCycle(c.tenantId(), c.userId()))
                .toList();
    }

    private static final String TENANT_CONTEXT_REQUIRED = "Tenant context is required";

    private String resolveTenantId(String requestedTenantId) {
        String contextTenant = com.example.platform.shared.web.TenantContext.get();
        if (contextTenant == null || contextTenant.isBlank()) {
            throw new IllegalArgumentException(TENANT_CONTEXT_REQUIRED);
        }
        if (requestedTenantId != null && !requestedTenantId.isBlank()
                && !requestedTenantId.equals(contextTenant)) {
            throw new SecurityException("Tenant ID does not match authenticated tenant");
        }
        return contextTenant;
    }
}
