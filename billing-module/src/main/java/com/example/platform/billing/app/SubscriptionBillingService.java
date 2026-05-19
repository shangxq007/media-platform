package com.example.platform.billing.app;

import com.example.platform.billing.domain.*;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SubscriptionBillingService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionBillingService.class);

    private final ConcurrentHashMap<String, SubscriptionPlan> plans = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SubscriptionContract> subscriptions = new ConcurrentHashMap<>();

    public SubscriptionPlan createPlan(String planKey, String name, String description,
                                        String billingInterval, long basePriceMinor,
                                        String currencyCode, Map<String, Long> includedQuota) {
        String planId = Ids.newId("plan");
        Instant now = Instant.now();
        SubscriptionPlan plan = new SubscriptionPlan(
                planId, planKey, name, description, billingInterval,
                basePriceMinor, currencyCode, includedQuota,
                "ACTIVE", now, now);
        plans.put(planKey, plan);
        log.info("SubscriptionBillingService: created plan {} key={}", planId, planKey);
        return plan;
    }

    public SubscriptionPlan getPlan(String planKey) {
        return plans.get(planKey);
    }

    public List<SubscriptionPlan> listPlans() {
        return plans.values().stream()
                .filter(p -> "ACTIVE".equals(p.status()))
                .toList();
    }

    public SubscriptionContract createSubscription(String tenantId, String userId,
                                                    String planKey, int periodDays) {
        SubscriptionPlan plan = plans.get(planKey);
        if (plan == null) {
            throw new IllegalArgumentException("Plan not found: " + planKey);
        }

        String contractId = Ids.newId("sub");
        Instant now = Instant.now();
        Map<String, Long> quotaUsed = plan.includedQuota() != null
                ? plan.includedQuota().keySet().stream().collect(
                        java.util.stream.Collectors.toMap(k -> k, k -> 0L))
                : Map.of();

        SubscriptionContract contract = new SubscriptionContract(
                contractId, tenantId, userId, planKey,
                now, now.plus(periodDays, ChronoUnit.DAYS),
                "ACTIVE", plan.basePriceMinor(), plan.currencyCode(),
                plan.includedQuota(), quotaUsed);
        subscriptions.put(contractId, contract);
        log.info("SubscriptionBillingService: created subscription {} plan={} user={}",
                contractId, planKey, userId);
        return contract;
    }

    public SubscriptionContract getCurrentSubscription(String tenantId, String userId) {
        return subscriptions.values().stream()
                .filter(s -> tenantId.equals(s.tenantId()) && userId.equals(s.userId()))
                .filter(s -> "ACTIVE".equals(s.lifecycleState()))
                .filter(s -> s.periodEndAt() != null && s.periodEndAt().isAfter(Instant.now()))
                .findFirst()
                .orElse(null);
    }

    public SubscriptionContract changePlan(String contractId, String newPlanKey, int periodDays) {
        SubscriptionContract existing = subscriptions.get(contractId);
        if (existing == null) {
            throw new IllegalArgumentException("Subscription not found: " + contractId);
        }
        SubscriptionPlan newPlan = plans.get(newPlanKey);
        if (newPlan == null) {
            throw new IllegalArgumentException("Plan not found: " + newPlanKey);
        }

        Instant now = Instant.now();
        Map<String, Long> quotaUsed = newPlan.includedQuota() != null
                ? newPlan.includedQuota().keySet().stream().collect(
                        java.util.stream.Collectors.toMap(k -> k, k -> 0L))
                : Map.of();

        SubscriptionContract updated = new SubscriptionContract(
                contractId, existing.tenantId(), existing.userId(), newPlanKey,
                now, now.plus(periodDays, ChronoUnit.DAYS),
                "ACTIVE", newPlan.basePriceMinor(), newPlan.currencyCode(),
                newPlan.includedQuota(), quotaUsed);
        subscriptions.put(contractId, updated);
        log.info("SubscriptionBillingService: changed plan for {} to {}", contractId, newPlanKey);
        return updated;
    }

    public SubscriptionContract cancelAtPeriodEnd(String contractId) {
        SubscriptionContract existing = subscriptions.get(contractId);
        if (existing == null) {
            throw new IllegalArgumentException("Subscription not found: " + contractId);
        }

        SubscriptionContract cancelled = new SubscriptionContract(
                existing.contractId(), existing.tenantId(), existing.userId(),
                existing.planKey(), existing.periodStartAt(), existing.periodEndAt(),
                "CANCELLED", existing.basePriceMinor(), existing.currencyCode(),
                existing.includedQuota(), existing.includedQuotaUsed());
        subscriptions.put(contractId, cancelled);
        log.info("SubscriptionBillingService: cancelled subscription {} at period end", contractId);
        return cancelled;
    }

    public void processBillingCycle() {
        Instant now = Instant.now();
        subscriptions.values().stream()
                .filter(s -> "ACTIVE".equals(s.lifecycleState()))
                .filter(s -> s.periodEndAt() != null && s.periodEndAt().isBefore(now))
                .forEach(s -> {
                    log.info("SubscriptionBillingService: processing billing cycle for subscription {}",
                            s.contractId());
                });
        log.info("SubscriptionBillingService: billing cycle processing complete");
    }
}
