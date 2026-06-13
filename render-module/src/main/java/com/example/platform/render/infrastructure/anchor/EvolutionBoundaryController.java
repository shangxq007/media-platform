package com.example.platform.render.infrastructure.anchor;

import com.example.platform.render.infrastructure.canonical.SystemCanonicalEvent;
import com.example.platform.render.infrastructure.canonical.SystemEventBus;
import com.example.platform.render.infrastructure.evolution.SystemEvolutionEngine;
import com.example.platform.render.infrastructure.evolution.SystemMutationGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Evolution Boundary Controller - enforces limits on SEL mutations.
 * 
 * <p>Prevents:
 * <ul>
 *   <li>Excessive objective drift</li>
 *   <li>Unstable provider oscillation</li>
 *   <li>Policy inversion</li>
 * </ul>
 * 
 * <p>Validates all mutations against SystemAnchorFunction.
 */
@Service
public class EvolutionBoundaryController {

    private static final Logger log = LoggerFactory.getLogger(EvolutionBoundaryController.class);

    private final SystemEventBus eventBus;
    private final SystemAnchorFunction anchorFunction;
    private final DriftLock driftLock;

    public EvolutionBoundaryController(
            SystemEventBus eventBus,
            SystemAnchorFunction anchorFunction,
            DriftLock driftLock) {
        this.eventBus = eventBus;
        this.anchorFunction = anchorFunction;
        this.driftLock = driftLock;
    }

    /**
     * Validate mutations against anchor boundaries.
     */
    public BoundaryValidationResult validateMutations(
            String tenantId,
            SystemMutationGraph mutationGraph,
            SystemAnchorFunction.SystemStateForAnchor systemState) {

        // Check if drift lock is active
        if (driftLock.isLocked(tenantId)) {
            log.warn("Evolution blocked for tenant {} due to drift lock", tenantId);
            emitBlockedEvent(tenantId, "Drift lock active");
            return BoundaryValidationResult.blocked("Drift lock active");
        }

        // Evaluate anchor score
        SystemAnchorFunction.AnchorScore anchorScore = anchorFunction.evaluate(systemState);

        // Check if system is stable
        if (!anchorScore.isStable()) {
            log.warn("System unstable for tenant {}: {}", tenantId, anchorScore.getSummary());
            driftLock.activate(tenantId, "System unstable: " + anchorScore.getSummary());
            emitDriftDetectedEvent(tenantId, anchorScore);
            return BoundaryValidationResult.blocked("System unstable");
        }

        // Validate each mutation
        for (SystemMutationGraph.MutationNode mutation : mutationGraph.mutations()) {
            SystemAnchorFunction.MutationProposal proposal = convertToProposal(mutation);

            if (!anchorFunction.isMutationAllowed(proposal)) {
                log.warn("Mutation {} blocked by anchor constraints", mutation.mutationId());
                emitConstraintViolationEvent(tenantId, mutation, proposal);
                return BoundaryValidationResult.blocked(
                        "Mutation blocked: " + mutation.description());
            }
        }

        // Emit anchor evaluated event
        emitAnchorEvaluatedEvent(tenantId, anchorScore);

        return BoundaryValidationResult.allowed(anchorScore);
    }

    /**
     * Check if evolution is allowed for a tenant.
     */
    public boolean isEvolutionAllowed(String tenantId) {
        return !driftLock.isLocked(tenantId);
    }

    /**
     * Get anchor score for current system state.
     */
    public SystemAnchorFunction.AnchorScore getAnchorScore(SystemAnchorFunction.SystemStateForAnchor state) {
        return anchorFunction.evaluate(state);
    }

    // ---------------------------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------------------------

    private SystemAnchorFunction.MutationProposal convertToProposal(
            SystemMutationGraph.MutationNode mutation) {
        Map<String, Object> after = mutation.afterState();
        double weightChange = 0;

        if (after.containsKey("costWeight") && after.containsKey("speedWeight")) {
            // Calculate weight change magnitude
            weightChange = 0.1; // Simplified
        }

        return new SystemAnchorFunction.MutationProposal(
                mutation.mutationId(),
                mutation.targetSystem(),
                mutation.mutationType().name(),
                weightChange,
                mutation.riskScore(),
                0.1, // behavior change
                mutation.riskScore() < 0.8,
                mutation.justification()
        );
    }

    private void emitAnchorEvaluatedEvent(String tenantId, SystemAnchorFunction.AnchorScore score) {
        SystemCanonicalEvent event = SystemCanonicalEvent.create(
                "ANCHOR_EVALUATED",
                tenantId, null, null,
                "EvolutionBoundaryController",
                Map.of(
                        "anchorScore", score.totalScore(),
                        "isStable", score.isStable(),
                        "constraintsSatisfied", score.constraintsSatisfied()
                )
        );
        eventBus.publish(event);
    }

    private void emitDriftDetectedEvent(String tenantId, SystemAnchorFunction.AnchorScore score) {
        SystemCanonicalEvent event = SystemCanonicalEvent.create(
                "DRIFT_DETECTED",
                tenantId, null, null,
                "EvolutionBoundaryController",
                Map.of(
                        "anchorScore", score.totalScore(),
                        "stabilityScore", score.stabilityScore(),
                        "costBoundednessScore", score.costBoundednessScore()
                )
        );
        eventBus.publish(event);
    }

    private void emitBlockedEvent(String tenantId, String reason) {
        SystemCanonicalEvent event = SystemCanonicalEvent.create(
                "EVOLUTION_BLOCKED",
                tenantId, null, null,
                "EvolutionBoundaryController",
                Map.of("reason", reason)
        );
        eventBus.publish(event);
    }

    private void emitConstraintViolationEvent(
            String tenantId,
            SystemMutationGraph.MutationNode mutation,
            SystemAnchorFunction.MutationProposal proposal) {
        SystemCanonicalEvent event = SystemCanonicalEvent.create(
                "IDENTITY_CONSTRAINT_VIOLATION",
                tenantId, null, null,
                "EvolutionBoundaryController",
                Map.of(
                        "mutationId", mutation.mutationId(),
                        "targetSystem", mutation.targetSystem(),
                        "riskScore", mutation.riskScore()
                )
        );
        eventBus.publish(event);
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record BoundaryValidationResult(
            boolean allowed,
            String reason,
            SystemAnchorFunction.AnchorScore anchorScore
    ) {
        public static BoundaryValidationResult allowed(SystemAnchorFunction.AnchorScore score) {
            return new BoundaryValidationResult(true, "Within boundaries", score);
        }

        public static BoundaryValidationResult blocked(String reason) {
            return new BoundaryValidationResult(false, reason, null);
        }
    }
}
