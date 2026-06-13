package com.example.platform.render.infrastructure.soul;

import com.example.platform.render.infrastructure.canonical.SystemCanonicalEvent;
import com.example.platform.render.infrastructure.canonical.SystemCanonicalGraph;
import com.example.platform.render.infrastructure.canonical.SystemEventBus;
import com.example.platform.render.infrastructure.strategy.ExecutionStrategyGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Objective-Driven Decision Engine - the SINGLE unified decision authority.
 * 
 * <p>Replaces ALL independent decision systems:
 * <ul>
 *   <li>BillingDecisionEngine (advisory only)</li>
 *   <li>PolicyEngine (advisory only)</li>
 *   <li>StrategyPlannerEngine (advisory only)</li>
 *   <li>RuntimeStrategyAdjuster (advisory only)</li>
 * </ul>
 * 
 * <p>ONLY this engine can approve execution paths.
 * All other engines are advisory only.
 */
@Service
public class ObjectiveDrivenDecisionEngine {

    private static final Logger log = LoggerFactory.getLogger(ObjectiveDrivenDecisionEngine.class);

    private final SystemEventBus eventBus;
    private final GlobalObjectiveFunction objectiveFunction;
    private final ConflictResolver conflictResolver;
    private final PreferenceLearningLayer preferenceLearning;

    // Decision threshold
    private static final double APPROVAL_THRESHOLD = 0.5;
    private static final double HIGH_CONFIDENCE_THRESHOLD = 0.8;

    public ObjectiveDrivenDecisionEngine(
            SystemEventBus eventBus,
            ConflictResolver conflictResolver,
            PreferenceLearningLayer preferenceLearning) {
        this.eventBus = eventBus;
        this.objectiveFunction = GlobalObjectiveFunction.createDefault();
        this.conflictResolver = conflictResolver;
        this.preferenceLearning = preferenceLearning;
    }

    /**
     * Make a unified decision for a render job.
     * This is the SINGLE decision point for the entire system.
     */
    public UnifiedDecision makeDecision(
            String jobId,
            String tenantId,
            String workspaceId,
            ExecutionStrategyGraph strategy,
            List<SubsystemRecommendation> recommendations) {

        log.info("[{}] Making unified decision with {} recommendations", jobId, recommendations.size());

        // Step 1: Detect conflicts between subsystems
        List<ConflictResolver.Conflict> conflicts = conflictResolver.detectConflicts(recommendations);

        // Step 2: Resolve conflicts using objective function
        List<ConflictResolver.ResolvedConflict> resolvedConflicts = List.of();
        if (!conflicts.isEmpty()) {
            resolvedConflicts = conflictResolver.resolveConflicts(conflicts, objectiveFunction);
            log.info("[{}] Resolved {} conflicts", jobId, resolvedConflicts.size());
        }

        // Step 3: Generate execution paths from strategy
        List<GlobalObjectiveFunction.ExecutionPath> paths = generateExecutionPaths(strategy, recommendations);

        // Step 4: Evaluate all paths against objective function
        List<GlobalObjectiveFunction.ObjectiveScore> scores = paths.stream()
                .map(objectiveFunction::evaluate)
                .sorted((a, b) -> Double.compare(b.totalScore(), a.totalScore()))
                .toList();

        // Step 5: Select best path
        GlobalObjectiveFunction.ObjectiveScore bestScore = scores.isEmpty() ? null : scores.get(0);

        // Step 6: Make decision
        UnifiedDecision decision;
        if (bestScore == null) {
            decision = UnifiedDecision.deny(jobId, "No valid execution paths found");
        } else if (bestScore.isAcceptable(APPROVAL_THRESHOLD)) {
            decision = UnifiedDecision.approve(
                    jobId,
                    bestScore.providerKey(),
                    bestScore.preset(),
                    bestScore.totalScore(),
                    bestScore,
                    conflicts,
                    resolvedConflicts
            );
        } else {
            decision = UnifiedDecision.deny(jobId,
                    String.format("Best score %.3f below threshold %.3f", 
                            bestScore.totalScore(), APPROVAL_THRESHOLD));
        }

        // Step 7: Emit decision event
        emitDecisionEvent(jobId, tenantId, decision);

        log.info("[{}] Unified decision: {} (score={})", jobId, decision.type(), 
                bestScore != null ? bestScore.totalScore() : 0);
        return decision;
    }

    /**
     * Evaluate a single execution path (for dry-run).
     */
    public GlobalObjectiveFunction.ObjectiveScore evaluatePath(GlobalObjectiveFunction.ExecutionPath path) {
        return objectiveFunction.evaluate(path);
    }

    /**
     * Update objective function weights based on user preferences.
     */
    public void updateWeights(String tenantId) {
        GlobalObjectiveFunction.ObjectiveWeights userWeights = 
                preferenceLearning.learnWeights(tenantId);
        // In production, would update the objective function
        log.info("Learned weights for tenant {}: cost={}, speed={}, quality={}",
                tenantId, userWeights.costWeight(), userWeights.speedWeight(), userWeights.qualityWeight());
    }

    // ---------------------------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------------------------

    private List<GlobalObjectiveFunction.ExecutionPath> generateExecutionPaths(
            ExecutionStrategyGraph strategy,
            List<SubsystemRecommendation> recommendations) {

        List<GlobalObjectiveFunction.ExecutionPath> paths = new java.util.ArrayList<>();

        // Generate paths from strategy options
        if (strategy != null) {
            ExecutionStrategyGraph.StrategyNode providerNode = 
                    strategy.getStepByType(ExecutionStrategyGraph.StepType.PROVIDER_SELECTION);
            if (providerNode != null) {
                for (ExecutionStrategyGraph.ExecutionOption option : providerNode.options()) {
                    paths.add(new GlobalObjectiveFunction.ExecutionPath(
                            option.providerKey(),
                            option.preset(),
                            option.estimatedCost(),
                            option.estimatedDurationMs(),
                            option.qualityScore(),
                            option.reliabilityScore(),
                            true, // policy compliant (checked separately)
                            0.5,  // default preference
                            Map.of("source", "strategy")
                    ));
                }
            }
        }

        // Generate paths from recommendations
        for (SubsystemRecommendation rec : recommendations) {
            if (rec.executionPath() != null) {
                paths.add(rec.executionPath());
            }
        }

        return paths;
    }

    private void emitDecisionEvent(String jobId, String tenantId, UnifiedDecision decision) {
        SystemCanonicalEvent event = SystemCanonicalEvent.create(
                "UNIFIED_DECISION",
                tenantId, null, jobId,
                "ObjectiveDrivenDecisionEngine",
                Map.of(
                        "decisionType", decision.type().name(),
                        "provider", decision.provider() != null ? decision.provider() : "none",
                        "score", decision.score(),
                        "conflictCount", decision.conflicts().size()
                )
        );
        eventBus.publish(event);
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record UnifiedDecision(
            String jobId,
            DecisionType type,
            String provider,
            String preset,
            double score,
            GlobalObjectiveFunction.ObjectiveScore objectiveScore,
            List<ConflictResolver.Conflict> conflicts,
            List<ConflictResolver.ResolvedConflict> resolvedConflicts,
            String reason,
            Instant decidedAt
    ) {
        public static UnifiedDecision approve(
                String jobId, String provider, String preset, double score,
                GlobalObjectiveFunction.ObjectiveScore objectiveScore,
                List<ConflictResolver.Conflict> conflicts,
                List<ConflictResolver.ResolvedConflict> resolvedConflicts) {
            return new UnifiedDecision(
                    jobId, DecisionType.APPROVE, provider, preset, score,
                    objectiveScore, conflicts, resolvedConflicts,
                    String.format("Approved with score %.3f", score),
                    Instant.now()
            );
        }

        public static UnifiedDecision deny(String jobId, String reason) {
            return new UnifiedDecision(
                    jobId, DecisionType.DENY, null, null, 0,
                    null, List.of(), List.of(), reason, Instant.now()
            );
        }

        public boolean isApproved() {
            return type == DecisionType.APPROVE;
        }
    }

    public enum DecisionType {
        APPROVE,
        DENY,
        THROTTLE,
        DEFER
    }

    public record SubsystemRecommendation(
            String sourceSystem,
            String recommendation,
            double confidence,
            GlobalObjectiveFunction.ExecutionPath executionPath,
            Map<String, Object> metadata
    ) {}
}
