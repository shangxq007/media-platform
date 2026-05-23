package com.example.platform.billing.app;

import com.example.platform.billing.domain.*;
import com.example.platform.billing.infrastructure.SubscriptionJdbcRepository;
import com.example.platform.shared.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SubscriptionBillingService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionBillingService.class);

    private final ConcurrentHashMap<String, SubscriptionPlan> plans = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SubscriptionContract> subscriptions = new ConcurrentHashMap<>();
    private final Optional<SubscriptionJdbcRepository> jdbcRepository;

    public SubscriptionBillingService() {
        this(Optional.empty());
    }

    @Autowired
    public SubscriptionBillingService(Optional<SubscriptionJdbcRepository> jdbcRepository) {
        this.jdbcRepository = jdbcRepository != null ? jdbcRepository : Optional.empty();
    }

    public void hydratePlan(SubscriptionPlan plan) {
        plans.put(plan.planKey(), plan);
    }

    public void hydrateContract(SubscriptionContract contract) {
        subscriptions.put(contract.contractId(), contract);
    }

    public SubscriptionPlan createPlan(String planKey, String name, String description,
                                        String billingInterval, long basePriceMinor,
                                        String currencyCode, Map<String, Long> includedQuota) {
        String planId = Ids.newId("plan");
        Instant now = Instant.now();
        SubscriptionPlan plan = new SubscriptionPlan(
                planId, planKey, name, description, billingInterval,
                basePriceMinor, currencyCode, includedQuota,
                "ACTIVE", now, now);
        persistPlan(plan);
        log.info("SubscriptionBillingService: created plan {} key={}", planId, planKey);
        return plan;
    }

    public SubscriptionPlan getPlan(String planKey) {
        SubscriptionPlan cached = plans.get(planKey);
        if (cached != null) {
            return cached;
        }
        return jdbcRepository.flatMap(r -> r.findPlanByKey(planKey))
                .map(p -> {
                    hydratePlan(p);
                    return p;
                })
                .orElse(null);
    }

    public List<SubscriptionPlan> listPlans() {
        return plans.values().stream()
                .filter(p -> "ACTIVE".equals(p.status()))
                .toList();
    }

    public SubscriptionContract createSubscription(String tenantId, String userId,
                                                    String planKey, int periodDays) {
        return createSubscription(tenantId, userId, planKey, planKey, periodDays,
                SubscriptionContractRole.BASE);
    }

    public SubscriptionContract createSubscription(String tenantId, String userId,
                                                    String planKey, String productCode,
                                                    int periodDays, SubscriptionContractRole role) {
        SubscriptionPlan plan = requirePlan(planKey);
        if (role == SubscriptionContractRole.BASE) {
            cancelActiveContracts(tenantId, userId, SubscriptionContractRole.BASE);
        }

        String contractId = Ids.newId("sub");
        Instant now = Instant.now();
        Map<String, Long> quotaUsed = emptyQuotaUsed(plan.includedQuota());

        SubscriptionContract contract = new SubscriptionContract(
                contractId, tenantId, userId, planKey,
                now, now.plus(periodDays, ChronoUnit.DAYS),
                "ACTIVE", plan.basePriceMinor(), plan.currencyCode(),
                plan.includedQuota(), quotaUsed, role, productCode);
        persistContract(contract);
        log.info("SubscriptionBillingService: created {} subscription {} plan={} user={}",
                role, contractId, planKey, userId);
        return contract;
    }

    public SubscriptionContract createAddonSubscription(String tenantId, String userId,
                                                         String planKey, String productCode,
                                                         int periodDays) {
        return createSubscription(tenantId, userId, planKey, productCode, periodDays,
                SubscriptionContractRole.ADD_ON);
    }

    /** Primary base plan subscription for tenant + user. */
    public SubscriptionContract getCurrentSubscription(String tenantId, String userId) {
        return listActiveSubscriptions(tenantId, userId).stream()
                .filter(c -> c.contractRole() == SubscriptionContractRole.BASE)
                .findFirst()
                .orElse(null);
    }

    public List<SubscriptionContract> listActiveSubscriptions(String tenantId, String userId) {
        Instant now = Instant.now();
        return subscriptions.values().stream()
                .filter(s -> tenantId.equals(s.tenantId()) && userId.equals(s.userId()))
                .filter(s -> s.isActiveAt(now))
                .toList();
    }

    /**
     * Merges {@code includedQuota} from all active contracts for the subject (additive per meter key).
     */
    public Map<String, Long> getEffectiveIncludedQuota(String tenantId, String userId) {
        Map<String, Long> merged = new HashMap<>();
        for (SubscriptionContract contract : listActiveSubscriptions(tenantId, userId)) {
            if (contract.includedQuota() == null) {
                continue;
            }
            contract.includedQuota().forEach((key, value) ->
                    merged.merge(key, value, Long::sum));
        }
        return Map.copyOf(merged);
    }

    public SubscriptionContract changePlan(String contractId, String newPlanKey, int periodDays) {
        SubscriptionContract existing = subscriptions.get(contractId);
        if (existing == null) {
            throw new IllegalArgumentException("Subscription not found: " + contractId);
        }
        SubscriptionPlan newPlan = requirePlan(newPlanKey);
        Instant now = Instant.now();
        Map<String, Long> quotaUsed = emptyQuotaUsed(newPlan.includedQuota());

        SubscriptionContract updated = new SubscriptionContract(
                contractId, existing.tenantId(), existing.userId(), newPlanKey,
                now, now.plus(periodDays, ChronoUnit.DAYS),
                "ACTIVE", newPlan.basePriceMinor(), newPlan.currencyCode(),
                newPlan.includedQuota(), quotaUsed,
                existing.contractRole(), existing.productCode());
        persistContract(updated);
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
                existing.includedQuota(), existing.includedQuotaUsed(),
                existing.contractRole(), existing.productCode());
        persistContract(cancelled);
        log.info("SubscriptionBillingService: cancelled subscription {} at period end", contractId);
        return cancelled;
    }

    private void cancelActiveContracts(String tenantId, String userId, SubscriptionContractRole role) {
        listActiveSubscriptions(tenantId, userId).stream()
                .filter(c -> c.contractRole() == role)
                .forEach(c -> cancelAtPeriodEnd(c.contractId()));
    }

    private SubscriptionPlan requirePlan(String planKey) {
        SubscriptionPlan plan = plans.get(planKey);
        if (plan == null) {
            plan = getPlan(planKey);
        }
        if (plan == null) {
            throw new IllegalArgumentException("Plan not found: " + planKey);
        }
        return plan;
    }

    private static Map<String, Long> emptyQuotaUsed(Map<String, Long> includedQuota) {
        if (includedQuota == null || includedQuota.isEmpty()) {
            return Map.of();
        }
        return includedQuota.keySet().stream()
                .collect(Collectors.toMap(k -> k, k -> 0L));
    }

    private void persistPlan(SubscriptionPlan plan) {
        plans.put(plan.planKey(), plan);
        jdbcRepository.ifPresent(r -> r.savePlan(plan));
    }

    private void persistContract(SubscriptionContract contract) {
        subscriptions.put(contract.contractId(), contract);
        jdbcRepository.ifPresent(r -> r.saveContract(contract));
    }

    public void processBillingCycle() {
        Instant now = Instant.now();
        subscriptions.values().stream()
                .filter(s -> "ACTIVE".equals(s.lifecycleState()))
                .filter(s -> s.periodEndAt() != null && s.periodEndAt().isBefore(now))
                .forEach(s -> log.info("SubscriptionBillingService: subscription {} period ended at {}",
                        s.contractId(), s.periodEndAt()));
        log.info("SubscriptionBillingService: billing cycle scan complete ({} active contracts)",
                listActiveSubscriptionsAllTenants().size());
    }

    public List<SubscriptionContract> listActiveSubscriptionsAllTenants() {
        Instant now = Instant.now();
        return subscriptions.values().stream()
                .filter(s -> s.isActiveAt(now))
                .toList();
    }
}
