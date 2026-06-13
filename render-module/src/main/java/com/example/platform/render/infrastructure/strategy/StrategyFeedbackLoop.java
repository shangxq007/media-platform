package com.example.platform.render.infrastructure.strategy;

import com.example.platform.render.infrastructure.canonical.SystemCanonicalEvent;
import com.example.platform.render.infrastructure.canonical.SystemCanonicalGraph;
import com.example.platform.render.infrastructure.canonical.SystemEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Strategy Feedback Loop - learns from execution results to improve future strategies.
 * 
 * <p>After execution:
 * <ul>
 *   <li>Compare planned vs actual execution</li>
 *   <li>Update strategy weights</li>
 *   <li>Feed learning signal into planner</li>
 *   <li>Update provider scoring model</li>
 * </ul>
 */
@Service
public class StrategyFeedbackLoop {

    private static final Logger log = LoggerFactory.getLogger(StrategyFeedbackLoop.class);

    private final SystemEventBus eventBus;
    private final Map<String, ProviderPerformance> providerPerformance = new ConcurrentHashMap<>();
    private final Map<String, StrategyOutcome> strategyOutcomes = new ConcurrentHashMap<>();

    public StrategyFeedbackLoop(SystemEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Analyze execution results and generate learning feedback.
     */
    public FeedbackResult analyzeAndLearn(
            String jobId,
            String tenantId,
            ExecutionStrategyGraph plannedStrategy,
            SystemCanonicalGraph executionGraph) {

        log.info("Analyzing execution feedback for job {}", jobId);

        // Compare planned vs actual
        ComparisonResult comparison = comparePlannedVsActual(plannedStrategy, executionGraph);

        // Update provider performance
        updateProviderPerformance(plannedStrategy, executionGraph);

        // Generate learning signal
        LearningSignal signal = generateLearningSignal(comparison, plannedStrategy);

        // Emit strategy learned event
        emitLearnedEvent(jobId, tenantId, signal);

        // Store outcome
        StrategyOutcome outcome = new StrategyOutcome(
                jobId,
                plannedStrategy.strategyId(),
                comparison,
                signal,
                Instant.now()
        );
        strategyOutcomes.put(jobId, outcome);

        FeedbackResult result = new FeedbackResult(
                jobId,
                comparison,
                signal,
                getProviderPerformanceSummary(),
                Instant.now()
        );

        log.info("Feedback analyzed for job {}: accuracy={}, signal={}",
                jobId, comparison.accuracy(), signal.type());
        return result;
    }

    /**
     * Compare planned vs actual execution.
     */
    private ComparisonResult comparePlannedVsActual(
            ExecutionStrategyGraph planned,
            SystemCanonicalGraph actual) {

        // Get planned provider
        String plannedProvider = planned.getStepByType(ExecutionStrategyGraph.StepType.PROVIDER_SELECTION)
                .selectedOption()
                .providerKey();

        // Get actual provider
        String actualProvider = actual.getProviderDecisionEvents().stream()
                .findFirst()
                .map(e -> e.getStringPayload("selectedProvider", "unknown"))
                .orElse("unknown");

        // Get planned cost
        double plannedCost = planned.getStepByType(ExecutionStrategyGraph.StepType.COST_OPTIMIZATION)
                .selectedOption()
                .estimatedCost();

        // Get actual cost
        double actualCost = actual.getBillingDecisionEvents().stream()
                .findFirst()
                .map(e -> e.getDoublePayload("estimatedCost", 0))
                .orElse(0.0);

        // Calculate accuracy
        boolean providerMatch = plannedProvider.equals(actualProvider);
        double costAccuracy = plannedCost > 0 ? 1.0 - Math.abs(plannedCost - actualCost) / plannedCost : 1.0;
        double overallAccuracy = (providerMatch ? 0.5 : 0) + (costAccuracy * 0.5);

        return new ComparisonResult(
                providerMatch,
                plannedProvider,
                actualProvider,
                plannedCost,
                actualCost,
                costAccuracy,
                overallAccuracy
        );
    }

    /**
     * Update provider performance model.
     */
    private void updateProviderPerformance(
            ExecutionStrategyGraph planned,
            SystemCanonicalGraph actual) {

        // Get actual provider
        String provider = actual.getProviderDecisionEvents().stream()
                .findFirst()
                .map(e -> e.getStringPayload("selectedProvider", "unknown"))
                .orElse("unknown");

        // Check if successful
        boolean success = actual.getExecutionStateEvents().stream()
                .anyMatch(e -> "COMPLETED".equals(e.getStringPayload("toState", "")));

        // Update performance
        ProviderPerformance perf = providerPerformance.getOrDefault(
                provider, new ProviderPerformance(provider, 0, 0, 0, 0, 0));

        perf = perf.recordExecution(success);
        providerPerformance.put(provider, perf);
    }

    /**
     * Generate learning signal.
     */
    private LearningSignal generateLearningSignal(
            ComparisonResult comparison,
            ExecutionStrategyGraph planned) {

        if (comparison.accuracy() > 0.8) {
            return new LearningSignal(
                    SignalType.REINFORCE,
                    "Strategy was accurate, reinforcing weights",
                    comparison.accuracy(),
                    Map.of("provider", comparison.actualProvider())
            );
        }

        if (comparison.accuracy() < 0.5) {
            return new LearningSignal(
                    SignalType.ADJUST,
                    "Strategy was inaccurate, adjusting weights",
                    comparison.accuracy(),
                    Map.of(
                            "plannedProvider", comparison.plannedProvider(),
                            "actualProvider", comparison.actualProvider()
                    )
            );
        }

        return new LearningSignal(
                SignalType.MAINTAIN,
                "Strategy was moderately accurate",
                comparison.accuracy(),
                Map.of()
        );
    }

    private Map<String, ProviderPerformance> getProviderPerformanceSummary() {
        return Map.copyOf(providerPerformance);
    }

    private void emitLearnedEvent(String jobId, String tenantId, LearningSignal signal) {
        SystemCanonicalEvent event = SystemCanonicalEvent.create(
                SystemCanonicalEvent.STRATEGY_LEARNED,
                tenantId, null, jobId,
                "StrategyFeedbackLoop",
                Map.of(
                        "signalType", signal.type().name(),
                        "description", signal.description(),
                        "confidence", signal.confidence()
                )
        );
        eventBus.publish(event);
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record FeedbackResult(
            String jobId,
            ComparisonResult comparison,
            LearningSignal signal,
            Map<String, ProviderPerformance> providerPerformance,
            Instant analyzedAt
    ) {}

    public record ComparisonResult(
            boolean providerMatch,
            String plannedProvider,
            String actualProvider,
            double plannedCost,
            double actualCost,
            double costAccuracy,
            double accuracy
    ) {}

    public record LearningSignal(
            SignalType type,
            String description,
            double confidence,
            Map<String, Object> metadata
    ) {}

    public enum SignalType {
        REINFORCE,
        ADJUST,
        MAINTAIN
    }

    public record ProviderPerformance(
            String providerKey,
            int totalExecutions,
            int successfulExecutions,
            int failedExecutions,
            double averageCost,
            double averageDurationMs
    ) {
        public ProviderPerformance recordExecution(boolean success) {
            return new ProviderPerformance(
                    providerKey,
                    totalExecutions + 1,
                    success ? successfulExecutions + 1 : successfulExecutions,
                    success ? failedExecutions : failedExecutions + 1,
                    averageCost,
                    averageDurationMs
            );
        }

        public double successRate() {
            return totalExecutions > 0 ? (double) successfulExecutions / totalExecutions : 0;
        }
    }

    public record StrategyOutcome(
            String jobId,
            String strategyId,
            ComparisonResult comparison,
            LearningSignal signal,
            Instant completedAt
    ) {}
}
