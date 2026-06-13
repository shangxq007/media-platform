package com.example.platform.render.infrastructure.evolution;

import com.example.platform.render.infrastructure.canonical.SystemCanonicalEvent;
import com.example.platform.render.infrastructure.canonical.SystemCanonicalGraph;
import com.example.platform.render.infrastructure.canonical.SystemEventBus;
import com.example.platform.render.infrastructure.soul.GlobalObjectiveFunction;
import com.example.platform.render.infrastructure.soul.ObjectiveDrivenDecisionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * System Evolution Engine - enables the system to modify its own objective function,
 * execution strategies, and subsystem configurations based on long-term outcomes.
 * 
 * <p>Capabilities:
 * <ul>
 *   <li>Modify GlobalObjectiveFunction weights</li>
 *   <li>Evolve StrategyPlanner heuristics</li>
 *   <li>Adjust Provider scoring model</li>
 *   <li>Update PolicyEngine rules dynamically</li>
 *   <li>Evolve Billing thresholds</li>
 *   <li>Generate new execution strategies</li>
 * </ul>
 */
@Service
public class SystemEvolutionEngine {

    private static final Logger log = LoggerFactory.getLogger(SystemEvolutionEngine.class);

    private final SystemEventBus eventBus;
    private final MetaLearningLoop metaLearning;
    private final EvolutionSafetyController safetyController;
    private final Map<String, SystemMutationGraph> mutationHistory = new ConcurrentHashMap<>();
    private final Map<String, GlobalObjectiveFunction.ObjectiveWeights> evolvedWeights = new ConcurrentHashMap<>();

    public SystemEvolutionEngine(
            SystemEventBus eventBus,
            MetaLearningLoop metaLearning,
            EvolutionSafetyController safetyController) {
        this.eventBus = eventBus;
        this.metaLearning = metaLearning;
        this.safetyController = safetyController;
    }

    /**
     * Analyze system and propose mutations.
     */
    public SystemMutationGraph analyzeAndPropose(
            String tenantId,
            List<SystemCanonicalGraph> historicalGraphs,
            GlobalObjectiveFunction currentObjective) {

        log.info("Analyzing system evolution for tenant {} with {} historical graphs",
                tenantId, historicalGraphs.size());

        // Step 1: Detect drift
        DriftReport drift = metaLearning.detectDrift(historicalGraphs);

        // Step 2: Generate improvement suggestions
        List<MetaLearningLoop.ImprovementSuggestion> suggestions = 
                metaLearning.generateSuggestions(historicalGraphs, drift);

        // Step 3: Convert suggestions to mutations
        SystemMutationGraph mutationGraph = SystemMutationGraph.create();

        for (MetaLearningLoop.ImprovementSuggestion suggestion : suggestions) {
            SystemMutationGraph.MutationNode mutation = convertToMutation(suggestion);
            mutationGraph = mutationGraph.addMutation(mutation);
        }

        // Step 4: Validate mutations
        mutationGraph = safetyController.validateMutations(mutationGraph, historicalGraphs);

        // Step 5: Store mutation history
        mutationHistory.put(tenantId, mutationGraph);

        // Step 6: Emit evolution event
        emitEvolutionEvent(tenantId, mutationGraph);

        log.info("Proposed {} mutations for tenant {}", mutationGraph.size(), tenantId);
        return mutationGraph;
    }

    /**
     * Apply validated mutations.
     */
    public EvolutionResult applyMutations(
            String tenantId,
            SystemMutationGraph mutationGraph,
            GlobalObjectiveFunction currentObjective) {

        log.info("Applying {} mutations for tenant {}", mutationGraph.size(), tenantId);

        // Check safety
        if (!safetyController.isSafeToApply(mutationGraph)) {
            return EvolutionResult.rejected(tenantId, "Safety check failed");
        }

        // Apply mutations
        GlobalObjectiveFunction evolvedObjective = currentObjective;

        for (SystemMutationGraph.MutationNode mutation : mutationGraph.mutations()) {
            if (mutation.status() == SystemMutationGraph.MutationStatus.VALIDATED) {
                evolvedObjective = applyMutation(evolvedObjective, mutation);
            }
        }

        // Store evolved weights
        evolvedWeights.put(tenantId, evolvedObjective.weights());

        // Mark as applied
        SystemMutationGraph appliedGraph = mutationGraph.markApplied();
        mutationHistory.put(tenantId, appliedGraph);

        // Emit applied event
        emitAppliedEvent(tenantId, appliedGraph);

        log.info("Applied mutations for tenant {}: new weights={}",
                tenantId, evolvedObjective.weights());
        return EvolutionResult.applied(tenantId, evolvedObjective);
    }

    /**
     * Rollback mutations.
     */
    public EvolutionResult rollbackMutations(String tenantId) {
        SystemMutationGraph mutationGraph = mutationHistory.get(tenantId);
        if (mutationGraph == null) {
            return EvolutionResult.rejected(tenantId, "No mutations to rollback");
        }

        SystemMutationGraph rolledBack = mutationGraph.markRolledBack();
        mutationHistory.put(tenantId, rolledBack);

        // Reset evolved weights
        evolvedWeights.remove(tenantId);

        emitRollbackEvent(tenantId, rolledBack);

        log.info("Rolled back mutations for tenant {}", tenantId);
        return EvolutionResult.rolledBack(tenantId);
    }

    /**
     * Get evolved weights for a tenant.
     */
    public GlobalObjectiveFunction.ObjectiveWeights getEvolvedWeights(String tenantId) {
        return evolvedWeights.get(tenantId);
    }

    /**
     * Get mutation history for a tenant.
     */
    public SystemMutationGraph getMutationHistory(String tenantId) {
        return mutationHistory.get(tenantId);
    }

    // ---------------------------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------------------------

    private SystemMutationGraph.MutationNode convertToMutation(
            MetaLearningLoop.ImprovementSuggestion suggestion) {
        return new SystemMutationGraph.MutationNode(
                "mut-" + Instant.now().toEpochMilli(),
                suggestion.targetSystem(),
                suggestion.mutationType(),
                suggestion.description(),
                Map.of("before", "current"),
                Map.of("after", suggestion.proposedChange()),
                suggestion.justification(),
                suggestion.riskScore(),
                Instant.now(),
                null,
                SystemMutationGraph.MutationStatus.PROPOSED
        );
    }

    private GlobalObjectiveFunction applyMutation(
            GlobalObjectiveFunction objective,
            SystemMutationGraph.MutationNode mutation) {
        return switch (mutation.mutationType()) {
            case WEIGHT_ADJUSTMENT -> {
                Map<String, Object> after = mutation.afterState();
                double costWeight = after.containsKey("costWeight") ?
                        ((Number) after.get("costWeight")).doubleValue() : objective.weights().costWeight();
                double speedWeight = after.containsKey("speedWeight") ?
                        ((Number) after.get("speedWeight")).doubleValue() : objective.weights().speedWeight();
                double qualityWeight = after.containsKey("qualityWeight") ?
                        ((Number) after.get("qualityWeight")).doubleValue() : objective.weights().qualityWeight();
                double reliabilityWeight = after.containsKey("reliabilityWeight") ?
                        ((Number) after.get("reliabilityWeight")).doubleValue() : objective.weights().reliabilityWeight();

                yield objective.withWeights(new GlobalObjectiveFunction.ObjectiveWeights(
                        costWeight, speedWeight, qualityWeight, reliabilityWeight,
                        objective.weights().complianceWeight(),
                        objective.weights().userPreferenceWeight()
                ));
            }
            default -> objective;
        };
    }

    private void emitEvolutionEvent(String tenantId, SystemMutationGraph graph) {
        SystemCanonicalEvent event = SystemCanonicalEvent.create(
                "SYSTEM_EVOLUTION_PROPOSED",
                tenantId, null, null,
                "SystemEvolutionEngine",
                Map.of("mutationCount", graph.size(), "status", graph.status().name())
        );
        eventBus.publish(event);
    }

    private void emitAppliedEvent(String tenantId, SystemMutationGraph graph) {
        SystemCanonicalEvent event = SystemCanonicalEvent.create(
                "SYSTEM_EVOLUTION_APPLIED",
                tenantId, null, null,
                "SystemEvolutionEngine",
                Map.of("mutationCount", graph.size())
        );
        eventBus.publish(event);
    }

    private void emitRollbackEvent(String tenantId, SystemMutationGraph graph) {
        SystemCanonicalEvent event = SystemCanonicalEvent.create(
                "SYSTEM_EVOLUTION_ROLLED_BACK",
                tenantId, null, null,
                "SystemEvolutionEngine",
                Map.of("mutationCount", graph.size())
        );
        eventBus.publish(event);
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record EvolutionResult(
            String tenantId,
            EvolutionStatus status,
            GlobalObjectiveFunction evolvedObjective,
            String reason,
            Instant timestamp
    ) {
        public static EvolutionResult applied(String tenantId, GlobalObjectiveFunction objective) {
            return new EvolutionResult(tenantId, EvolutionStatus.APPLIED, objective, "Mutations applied", Instant.now());
        }

        public static EvolutionResult rejected(String tenantId, String reason) {
            return new EvolutionResult(tenantId, EvolutionStatus.REJECTED, null, reason, Instant.now());
        }

        public static EvolutionResult rolledBack(String tenantId) {
            return new EvolutionResult(tenantId, EvolutionStatus.ROLLED_BACK, null, "Mutations rolled back", Instant.now());
        }
    }

    public enum EvolutionStatus {
        PROPOSED,
        VALIDATED,
        APPLIED,
        REJECTED,
        ROLLED_BACK
    }

    public record DriftReport(
            boolean costDriftDetected,
            boolean providerDriftDetected,
            boolean policyDriftDetected,
            boolean preferenceDriftDetected,
            double costDriftMagnitude,
            double providerDriftMagnitude,
            String summary
    ) {}
}
