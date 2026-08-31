package com.example.platform.billing.api;

import com.example.platform.billing.app.BillingCycleService;
import com.example.platform.billing.app.SubscriptionBillingService;
import com.example.platform.shared.authorization.FailClosedAuthorization;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/billing/cycles")
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
        throw FailClosedAuthorization.unavailable("billing cycle execution");
    }

    @PostMapping("/process-due")
    public List<BillingCycleService.BillingCycleResult> processDueSubscriptions() {
        throw FailClosedAuthorization.unavailable("global due-subscription processing");
    }
}
