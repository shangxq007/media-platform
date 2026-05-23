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
            @RequestParam String tenantId,
            @RequestParam String userId) {
        return billingCycleService.runCycle(tenantId, userId);
    }

    @PostMapping("/process-due")
    public List<BillingCycleService.BillingCycleResult> processDueSubscriptions() {
        subscriptionBillingService.processBillingCycle();
        return subscriptionBillingService.listActiveSubscriptionsAllTenants().stream()
                .map(c -> billingCycleService.runCycle(c.tenantId(), c.userId()))
                .toList();
    }
}
