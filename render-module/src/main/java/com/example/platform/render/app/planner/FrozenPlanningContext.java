package com.example.platform.render.app.planner;

import java.util.List;
import java.util.Map;

/**
 * Frozen planning context (PRE-#21 C2 / C3).
 *
 * <p>An immutable snapshot of every fact the logical planner is allowed to
 * consume. It is built by the runtime/capability resolution layer BEFORE
 * logical planning; the planner never reads mutable runtime state
 * (ProductRuntimeService / ProducerRuntimeService / runtime registries).
 *
 * <p>Purity contract: the logical planner is pure computation over this
 * context. All values are frozen at construction time.
 *
 * @param targetProductId   the product to plan for
 * @param targetProductType semantic product type (not a capability)
 * @param tenantId          ownership context
 * @param projectId         ownership context
 * @param targetAlreadyReady true if the target product is already in READY
 *                           state (frozen fact supplied by the caller)
 * @param capabilityFacts   frozen capability resolution facts keyed by
 *                          product type (resolved by the resolution layer
 *                          BEFORE planning; never resolved by the planner)
 * @param dependencyFacts   frozen input-product status facts keyed by
 *                          product id
 */
public record FrozenPlanningContext(
        String targetProductId,
        String targetProductType,
        String tenantId,
        String projectId,
        boolean targetAlreadyReady,
        Map<String, CapabilityResolutionFact> capabilityFacts,
        Map<String, DependencyFact> dependencyFacts) {

    public static FrozenPlanningContext of(
            String targetProductId,
            String targetProductType,
            String tenantId,
            String projectId,
            boolean targetAlreadyReady,
            Map<String, CapabilityResolutionFact> capabilityFacts,
            Map<String, DependencyFact> dependencyFacts) {
        return new FrozenPlanningContext(targetProductId, targetProductType,
                tenantId, projectId, targetAlreadyReady,
                Map.copyOf(capabilityFacts), Map.copyOf(dependencyFacts));
    }

    /** Frozen capability resolution fact: which producer/backend was resolved
     *  for a given product type, decided before planning. */
    public record CapabilityResolutionFact(
            String productType,
            String capability,
            String producerId,
            String backendId,
            String backendType,
            String selectionReason,
            boolean resolved) {}

    /** Frozen input-product status fact: the status of an upstream product. */
    public record DependencyFact(
            String productId,
            String productType,
            String status) {}
}
