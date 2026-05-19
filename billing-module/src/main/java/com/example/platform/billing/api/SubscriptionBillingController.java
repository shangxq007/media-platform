package com.example.platform.billing.api;

import com.example.platform.billing.api.dto.*;
import com.example.platform.billing.app.SubscriptionBillingService;
import com.example.platform.billing.domain.SubscriptionContract;
import com.example.platform.billing.domain.SubscriptionPlan;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SubscriptionBillingController {

    private final SubscriptionBillingService subscriptionBillingService;

    public SubscriptionBillingController(SubscriptionBillingService subscriptionBillingService) {
        this.subscriptionBillingService = subscriptionBillingService;
    }

    @PostMapping("/admin/billing/plans")
    public SubscriptionPlanResponse createPlan(@RequestBody CreatePlanRequest request) {
        SubscriptionPlan plan = subscriptionBillingService.createPlan(
                request.planKey(), request.name(), request.description(),
                request.billingInterval(), request.basePriceMinor(),
                request.currencyCode(), request.includedQuota());
        return toPlanResponse(plan);
    }

    @GetMapping("/admin/billing/plans")
    public List<SubscriptionPlanResponse> listPlans() {
        return subscriptionBillingService.listPlans().stream()
                .map(this::toPlanResponse)
                .toList();
    }

    @PostMapping("/billing/subscriptions")
    public SubscriptionResponse createSubscription(@RequestBody CreateSubscriptionRequest request) {
        SubscriptionContract contract = subscriptionBillingService.createSubscription(
                request.tenantId(), request.userId(), request.planKey(), request.periodDays());
        return toSubscriptionResponse(contract);
    }

    @GetMapping("/billing/subscriptions/current")
    public SubscriptionResponse getCurrentSubscription(
            @RequestParam String tenantId, @RequestParam String userId) {
        SubscriptionContract contract = subscriptionBillingService.getCurrentSubscription(tenantId, userId);
        if (contract == null) {
            return null;
        }
        return toSubscriptionResponse(contract);
    }

    @PostMapping("/billing/subscriptions/change-plan")
    public SubscriptionResponse changePlan(@RequestBody ChangePlanRequest request) {
        SubscriptionContract contract = subscriptionBillingService.changePlan(
                request.contractId(), request.newPlanKey(), request.periodDays());
        return toSubscriptionResponse(contract);
    }

    @PostMapping("/billing/subscriptions/cancel")
    public SubscriptionResponse cancel(@RequestBody CancelSubscriptionRequest request) {
        SubscriptionContract contract = subscriptionBillingService.cancelAtPeriodEnd(request.contractId());
        return toSubscriptionResponse(contract);
    }

    private SubscriptionPlanResponse toPlanResponse(SubscriptionPlan plan) {
        return new SubscriptionPlanResponse(
                plan.planId(), plan.planKey(), plan.name(), plan.description(),
                plan.billingInterval(), plan.basePriceMinor(), plan.currencyCode(),
                plan.includedQuota(), plan.status());
    }

    private SubscriptionResponse toSubscriptionResponse(SubscriptionContract contract) {
        return new SubscriptionResponse(
                contract.contractId(), contract.tenantId(), contract.userId(),
                contract.planKey(), contract.periodStartAt(), contract.periodEndAt(),
                contract.lifecycleState(), contract.basePriceMinor(),
                contract.currencyCode());
    }
}
