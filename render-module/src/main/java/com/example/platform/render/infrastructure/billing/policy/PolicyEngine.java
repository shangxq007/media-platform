package com.example.platform.render.infrastructure.billing.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Engine for evaluating declarative policies.
 * 
 * <p>Policies are data-driven and can be added without code changes.
 * The engine evaluates all active policies and merges results by priority.
 */
@Service
public class PolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(PolicyEngine.class);

    private final Map<String, Policy> policies = new ConcurrentHashMap<>();

    /**
     * Register a policy.
     */
    public void registerPolicy(Policy policy) {
        policies.put(policy.id(), policy);
        log.info("Registered policy: {} ({})", policy.name(), policy.id());
    }

    /**
     * Remove a policy.
     */
    public void removePolicy(String policyId) {
        policies.remove(policyId);
        log.info("Removed policy: {}", policyId);
    }

    /**
     * Get a policy by ID.
     */
    public Policy getPolicy(String policyId) {
        return policies.get(policyId);
    }

    /**
     * List all active policies.
     */
    public List<Policy> listActivePolicies() {
        return policies.values().stream()
                .filter(Policy::isActive)
                .toList();
    }

    /**
     * Evaluate policies against a billing context.
     * Returns the merged policy decision.
     */
    public PolicyEvaluationResult evaluate(PolicyEvaluationContext context) {
        Instant startTime = Instant.now();
        List<PolicyEvaluation> evaluations = new ArrayList<>();

        // Get all active policies that apply to this context
        List<Policy> applicablePolicies = policies.values().stream()
                .filter(Policy::isActive)
                .filter(p -> p.appliesTo(context.scope(), context.scopeId()))
                .sorted(Comparator.comparingInt(Policy::priority).reversed())
                .toList();

        log.debug("Evaluating {} applicable policies for tenant {}", 
                applicablePolicies.size(), context.tenantId());

        // Evaluate each policy
        for (Policy policy : applicablePolicies) {
            PolicyEvaluation evaluation = evaluatePolicy(policy, context);
            evaluations.add(evaluation);

            // Stop at first DENY (short-circuit)
            if (evaluation.matched() && evaluation.actions().stream()
                    .anyMatch(a -> a.type() == PolicyAction.ActionType.DENY)) {
                log.info("Policy {} denied operation for tenant {}", policy.id(), context.tenantId());
                break;
            }
        }

        // Merge results
        PolicyEvaluationResult result = mergeEvaluations(evaluations, context);

        long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();
        log.debug("Policy evaluation completed in {}ms: {}", durationMs, result.summary());

        return result;
    }

    /**
     * Evaluate a single policy against a context.
     */
    private PolicyEvaluation evaluatePolicy(Policy policy, PolicyEvaluationContext context) {
        // Check all conditions
        boolean allConditionsMet = true;
        for (PolicyCondition condition : policy.conditions()) {
            if (!evaluateCondition(condition, context)) {
                allConditionsMet = false;
                break;
            }
        }

        return new PolicyEvaluation(
                policy.id(),
                policy.name(),
                policy.priority(),
                allConditionsMet,
                allConditionsMet ? policy.actions() : List.of()
        );
    }

    /**
     * Evaluate a single condition against a context.
     */
    private boolean evaluateCondition(PolicyCondition condition, PolicyEvaluationContext context) {
        Object contextValue = getContextValue(condition.type(), condition.field(), context);
        return condition.evaluate(contextValue);
    }

    /**
     * Get a value from the context based on condition type.
     */
    private Object getContextValue(PolicyCondition.ConditionType type, String field,
                                     PolicyEvaluationContext context) {
        return switch (type) {
            case TENANT_ID -> context.tenantId();
            case WORKSPACE_ID -> context.workspaceId();
            case USER_ID -> context.userId();
            case ACTION_TYPE -> context.actionType();
            case PROVIDER -> context.provider();
            case PRESET -> context.preset();
            case TIER -> context.tier();
            case USAGE_AMOUNT -> context.currentUsage().getOrDefault(field, 0L);
            case COST_AMOUNT -> context.estimatedCost();
            case DURATION_SECONDS -> context.durationSeconds();
            case OUTPUT_SIZE -> context.outputSizeBytes();
            case GPU_ENABLED -> context.useGpu();
            case FEATURE_KEY -> field;
            case CUSTOM -> context.customFields().get(field);
        };
    }

    /**
     * Merge multiple policy evaluations into a final result.
     */
    private PolicyEvaluationResult mergeEvaluations(List<PolicyEvaluation> evaluations,
                                                      PolicyEvaluationContext context) {
        List<PolicyAction> mergedActions = new ArrayList<>();
        List<String> appliedPolicyIds = new ArrayList<>();
        double totalDiscount = 0;
        double totalMultiplier = 1.0;
        boolean denied = false;
        String denyReason = null;

        for (PolicyEvaluation evaluation : evaluations) {
            if (!evaluation.matched()) continue;

            appliedPolicyIds.add(evaluation.policyId());

            for (PolicyAction action : evaluation.actions()) {
                mergedActions.add(action);

                switch (action.type()) {
                    case DENY -> {
                        denied = true;
                        denyReason = action.reason();
                    }
                    case APPLY_DISCOUNT -> {
                        totalDiscount += action.getDoubleParam("discountPercent", 0);
                    }
                    case APPLY_MULTIPLIER -> {
                        totalMultiplier *= action.getDoubleParam("multiplier", 1.0);
                    }
                    default -> {}
                }
            }
        }

        return new PolicyEvaluationResult(
                denied,
                denyReason,
                mergedActions,
                appliedPolicyIds,
                Math.min(totalDiscount, 100), // Cap at 100%
                totalMultiplier,
                Instant.now()
        );
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    /**
     * Context for policy evaluation.
     */
    public record PolicyEvaluationContext(
            String tenantId,
            String workspaceId,
            String userId,
            String actionType,
            String provider,
            String preset,
            String tier,
            Map<String, Long> currentUsage,
            double estimatedCost,
            long durationSeconds,
            long outputSizeBytes,
            boolean useGpu,
            PolicyScope scope,
            String scopeId,
            Map<String, Object> customFields
    ) {}

    /**
     * Result of evaluating a single policy.
     */
    public record PolicyEvaluation(
            String policyId,
            String policyName,
            int priority,
            boolean matched,
            List<PolicyAction> actions
    ) {}

    /**
     * Result of evaluating all policies.
     */
    public record PolicyEvaluationResult(
            boolean denied,
            String denyReason,
            List<PolicyAction> appliedActions,
            List<String> appliedPolicyIds,
            double totalDiscountPercent,
            double totalMultiplier,
            Instant evaluatedAt
    ) {
        /**
         * Check if operation is allowed.
         */
        public boolean isAllowed() {
            return !denied;
        }

        /**
         * Get a summary of the evaluation.
         */
        public String summary() {
            if (denied) {
                return "DENIED: " + denyReason;
            }
            return String.format("ALLOWED (policies: %s, discount: %.1f%%, multiplier: %.2f)",
                    appliedPolicyIds, totalDiscountPercent, totalMultiplier);
        }
    }
}
