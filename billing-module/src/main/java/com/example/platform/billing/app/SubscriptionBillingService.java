package com.example.platform.billing.app;

import com.example.platform.billing.domain.SubscriptionCommand;
import com.example.platform.billing.domain.SubscriptionCommandResult;
import com.example.platform.billing.domain.SubscriptionContract;
import com.example.platform.billing.domain.SubscriptionContractRole;
import com.example.platform.billing.domain.SubscriptionPlan;
import com.example.platform.billing.infrastructure.SubscriptionJdbcRepository;
import com.example.platform.shared.Ids;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sole logical Subscription command authority. */
@Service
public class SubscriptionBillingService {

    private final ConcurrentHashMap<String, SubscriptionPlan> planProjection = new ConcurrentHashMap<>();
    private final SubscriptionJdbcRepository repository;

    public SubscriptionBillingService(SubscriptionJdbcRepository repository) { this.repository = repository; }

    @Transactional
    public SubscriptionPlan createPlan(String planKey, String name, String description,
            String billingInterval, long basePriceMinor, String currencyCode,
            Map<String, Long> includedQuota) {
        Instant now = Instant.now();
        SubscriptionPlan plan = new SubscriptionPlan(Ids.newId("plan"), planKey, name, description,
                billingInterval, basePriceMinor, currencyCode, includedQuota, "ACTIVE", now, now);
        repository.savePlan(plan);
        planProjection.put(planKey, plan);
        return plan;
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan getPlan(String planKey) {
        SubscriptionPlan plan = repository.findPlanByKey(planKey).orElse(null);
        if (plan != null) planProjection.put(planKey, plan);
        return plan;
    }

    @Transactional(readOnly = true)
    public List<SubscriptionPlan> listPlans() {
        List<SubscriptionPlan> plans = repository.loadAllPlans().stream()
                .filter(plan -> "ACTIVE".equals(plan.status())).toList();
        plans.forEach(plan -> planProjection.put(plan.planKey(), plan));
        return plans;
    }

    /** Claim, mutation, replacement cancellation, and audit completion share one transaction. */
    @Transactional
    public SubscriptionCommandResult execute(SubscriptionCommand command) {
        String commandId = Ids.newId("sub_cmd");
        if (!repository.claim(commandId, command, Instant.now())) return repository.replay(command);
        SubscriptionContract result = switch (command.commandType()) {
            case CREATE -> create(command);
            case CHANGE -> change(command);
            case CANCEL -> cancel(command);
        };
        repository.complete(commandId, result, Instant.now());
        return new SubscriptionCommandResult(commandId, result);
    }

    private SubscriptionContract create(SubscriptionCommand command) {
        SubscriptionPlan plan = requirePlan(command.planKey());
        if (command.contractRole() == SubscriptionContractRole.BASE) {
            repository.cancelActiveBase(command.principal(), command.effectiveAt());
        }
        SubscriptionContract contract = contract(command, plan, "ACTIVE", 0L);
        repository.insertContract(contract, command.principal(), Instant.now());
        return contract;
    }

    private SubscriptionContract change(SubscriptionCommand command) {
        SubscriptionContract existing = requireContract(command.principal(), command.contractId());
        if (!"ACTIVE".equals(existing.lifecycleState())) {
            throw new IllegalStateException("Only ACTIVE subscriptions may change plan");
        }
        SubscriptionContract replacement = contract(
                command, requirePlan(command.planKey()), "ACTIVE", existing.version() + 1);
        return repository.transition(command.principal(), command.contractId(), command.expectedVersion(),
                replacement, "ACTIVE");
    }

    private SubscriptionContract cancel(SubscriptionCommand command) {
        SubscriptionContract existing = requireContract(command.principal(), command.contractId());
        if (!"ACTIVE".equals(existing.lifecycleState())) {
            throw new IllegalStateException("Only ACTIVE subscriptions may be cancelled");
        }
        SubscriptionContract replacement = new SubscriptionContract(existing.contractId(),
                existing.tenantId(), existing.userId(), existing.planKey(), existing.periodStartAt(),
                existing.periodEndAt(), "CANCELLED", existing.basePriceMinor(), existing.currencyCode(),
                existing.includedQuota(), existing.includedQuotaUsed(), existing.contractRole(),
                existing.productCode(), existing.version() + 1);
        return repository.transition(command.principal(), command.contractId(), command.expectedVersion(),
                replacement, "ACTIVE");
    }

    private SubscriptionContract contract(
            SubscriptionCommand command, SubscriptionPlan plan, String state, long version) {
        Map<String, Long> used = plan.includedQuota() == null ? Map.of()
                : plan.includedQuota().keySet().stream().collect(Collectors.toMap(key -> key, key -> 0L));
        return new SubscriptionContract(command.contractId(), command.principal().tenantId(),
                command.principal().principalId(), plan.planKey(), command.effectiveAt(),
                command.effectiveAt().plus(command.periodDays(), ChronoUnit.DAYS), state,
                plan.basePriceMinor(), plan.currencyCode(), plan.includedQuota(), used,
                command.contractRole(), command.productCode(), version);
    }

    @Transactional(readOnly = true)
    public SubscriptionContract getContract(PrincipalRef principal, String contractId) {
        return repository.findByPrincipalAndId(principal, contractId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionContract> listActiveSubscriptions(PrincipalRef principal) {
        return repository.findActive(principal, Instant.now());
    }

    @Transactional(readOnly = true)
    public SubscriptionContract getCurrentSubscription(PrincipalRef principal) {
        return listActiveSubscriptions(principal).stream()
                .filter(contract -> contract.contractRole() == SubscriptionContractRole.BASE)
                .findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getEffectiveIncludedQuota(PrincipalRef principal) {
        Map<String, Long> merged = new HashMap<>();
        for (SubscriptionContract contract : listActiveSubscriptions(principal)) {
            if (contract.includedQuota() != null) {
                contract.includedQuota().forEach((key, value) -> merged.merge(key, value, Long::sum));
            }
        }
        return Map.copyOf(merged);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getEffectiveIncludedQuota(String tenantId, String userId) {
        return getEffectiveIncludedQuota(
                PrincipalRef.tenantScoped(tenantId, PrincipalType.USER, userId));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionContract> listActiveSubscriptionsAllTenants() {
        Instant now = Instant.now();
        return repository.loadAllContracts().stream()
                .filter(contract -> contract.isActiveAt(now)).toList();
    }

    @Transactional(readOnly = true)
    public void processBillingCycle() {
        repository.loadAllContracts().stream()
                .filter(contract -> contract.isActiveAt(Instant.now())).toList();
    }

    private SubscriptionPlan requirePlan(String planKey) {
        return repository.findPlanByKey(planKey)
                .orElseThrow(() -> new IllegalArgumentException("Plan not found: " + planKey));
    }

    private SubscriptionContract requireContract(PrincipalRef principal, String contractId) {
        return repository.findByPrincipalAndId(principal, contractId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Subscription not found for principal: " + contractId));
    }
}
