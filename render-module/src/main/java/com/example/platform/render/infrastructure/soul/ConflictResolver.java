package com.example.platform.render.infrastructure.soul;

import com.example.platform.render.infrastructure.canonical.SystemCanonicalEvent;
import com.example.platform.render.infrastructure.canonical.SystemEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Conflict Resolver - detects and resolves conflicting decisions across subsystems.
 * 
 * <p>Uses GlobalObjectiveFunction to resolve conflicts optimally.
 */
@Service
public class ConflictResolver {

    private static final Logger log = LoggerFactory.getLogger(ConflictResolver.class);

    private final SystemEventBus eventBus;

    public ConflictResolver(SystemEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Detect conflicts between subsystem recommendations.
     */
    public List<Conflict> detectConflicts(List<ObjectiveDrivenDecisionEngine.SubsystemRecommendation> recommendations) {
        List<Conflict> conflicts = new ArrayList<>();

        // Group recommendations by type
        Map<String, List<ObjectiveDrivenDecisionEngine.SubsystemRecommendation>> byType = 
                recommendations.stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                ObjectiveDrivenDecisionEngine.SubsystemRecommendation::sourceSystem));

        // Check for billing vs policy conflicts
        var billingRecs = byType.getOrDefault("BillingDecisionEngine", List.of());
        var policyRecs = byType.getOrDefault("PolicyEngine", List.of());

        for (var billing : billingRecs) {
            for (var policy : policyRecs) {
                if (billing.confidence() > 0.7 && policy.confidence() > 0.7) {
                    // Both have high confidence - check for conflict
                    if (hasConflict(billing, policy)) {
                        conflicts.add(new Conflict(
                                "conflict-" + Instant.now().toEpochMilli(),
                                ConflictType.BILLING_VS_POLICY,
                                billing.sourceSystem(),
                                policy.sourceSystem(),
                                billing.recommendation(),
                                policy.recommendation(),
                                billing.confidence(),
                                policy.confidence(),
                                "Billing and policy recommendations conflict"
                        ));
                    }
                }
            }
        }

        // Check for strategy vs provider conflicts
        var strategyRecs = byType.getOrDefault("StrategyPlannerEngine", List.of());
        var providerRecs = byType.getOrDefault("ProviderRuntimeEngine", List.of());

        for (var strategy : strategyRecs) {
            for (var provider : providerRecs) {
                if (strategy.confidence() > 0.7 && provider.confidence() > 0.7) {
                    if (hasProviderConflict(strategy, provider)) {
                        conflicts.add(new Conflict(
                                "conflict-" + Instant.now().toEpochMilli(),
                                ConflictType.STRATEGY_VS_PROVIDER,
                                strategy.sourceSystem(),
                                provider.sourceSystem(),
                                strategy.recommendation(),
                                provider.recommendation(),
                                strategy.confidence(),
                                provider.confidence(),
                                "Strategy and provider recommendations conflict"
                        ));
                    }
                }
            }
        }

        return conflicts;
    }

    /**
     * Resolve conflicts using GlobalObjectiveFunction.
     */
    public List<ResolvedConflict> resolveConflicts(
            List<Conflict> conflicts,
            GlobalObjectiveFunction objectiveFunction) {

        List<ResolvedConflict> resolved = new ArrayList<>();

        for (Conflict conflict : conflicts) {
            ResolvedConflict resolution = resolveConflict(conflict, objectiveFunction);
            resolved.add(resolution);

            // Emit conflict resolved event
            emitConflictResolvedEvent(conflict, resolution);
        }

        return resolved;
    }

    /**
     * Resolve a single conflict.
     */
    private ResolvedConflict resolveConflict(Conflict conflict, GlobalObjectiveFunction objectiveFunction) {
        // Evaluate both sides of the conflict
        double scoreA = evaluateConflictSide(conflict.sourceSystemA(), objectiveFunction);
        double scoreB = evaluateConflictSide(conflict.sourceSystemB(), objectiveFunction);

        String winner;
        String resolutionReason;
        double confidence;

        if (scoreA > scoreB) {
            winner = conflict.sourceSystemA();
            resolutionReason = String.format("%s wins with score %.3f vs %.3f", winner, scoreA, scoreB);
            confidence = scoreA;
        } else {
            winner = conflict.sourceSystemB();
            resolutionReason = String.format("%s wins with score %.3f vs %.3f", winner, scoreB, scoreA);
            confidence = scoreB;
        }

        return new ResolvedConflict(
                conflict.conflictId(),
                conflict.type(),
                winner,
                resolutionReason,
                confidence,
                scoreA,
                scoreB,
                Instant.now()
        );
    }

    private double evaluateConflictSide(String sourceSystem, GlobalObjectiveFunction objectiveFunction) {
        // Simplified evaluation based on system priority
        return switch (sourceSystem) {
            case "BillingDecisionEngine" -> 0.8;
            case "PolicyEngine" -> 0.9; // Policy has higher priority
            case "StrategyPlannerEngine" -> 0.7;
            case "ProviderRuntimeEngine" -> 0.75;
            default -> 0.5;
        };
    }

    private boolean hasConflict(
            ObjectiveDrivenDecisionEngine.SubsystemRecommendation a,
            ObjectiveDrivenDecisionEngine.SubsystemRecommendation b) {
        // Simplified conflict detection
        if (a.executionPath() != null && b.executionPath() != null) {
            return !a.executionPath().providerKey().equals(b.executionPath().providerKey());
        }
        return false;
    }

    private boolean hasProviderConflict(
            ObjectiveDrivenDecisionEngine.SubsystemRecommendation strategy,
            ObjectiveDrivenDecisionEngine.SubsystemRecommendation provider) {
        if (strategy.executionPath() != null && provider.executionPath() != null) {
            return !strategy.executionPath().providerKey().equals(provider.executionPath().providerKey());
        }
        return false;
    }

    private void emitConflictResolvedEvent(Conflict conflict, ResolvedConflict resolution) {
        SystemCanonicalEvent event = SystemCanonicalEvent.create(
                SystemCanonicalEvent.STRATEGY_ADJUSTED,
                null, null, null,
                "ConflictResolver",
                Map.of(
                        "conflictId", conflict.conflictId(),
                        "conflictType", conflict.type().name(),
                        "winner", resolution.winner(),
                        "reason", resolution.reason()
                )
        );
        eventBus.publish(event);
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record Conflict(
            String conflictId,
            ConflictType type,
            String sourceSystemA,
            String sourceSystemB,
            String recommendationA,
            String recommendationB,
            double confidenceA,
            double confidenceB,
            String description
    ) {}

    public enum ConflictType {
        BILLING_VS_POLICY,
        STRATEGY_VS_PROVIDER,
        COST_VS_QUALITY,
        SPEED_VS_RELIABILITY
    }

    public record ResolvedConflict(
            String conflictId,
            ConflictType type,
            String winner,
            String reason,
            double confidence,
            double scoreA,
            double scoreB,
            Instant resolvedAt
    ) {}
}
