package com.example.platform.render.infrastructure.strategy;

import com.example.platform.render.infrastructure.canonical.SystemCanonicalEvent;
import com.example.platform.render.infrastructure.canonical.SystemEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Runtime Strategy Adjuster - monitors execution and dynamically adjusts strategies.
 * 
 * <p>Monitors SystemCanonicalEvent stream during execution.
 * Detects deviation from expected performance and adjusts:
 * <ul>
 *   <li>Provider selection</li>
 *   <li>Retry strategy</li>
 *   <li>Fallback path</li>
 *   <li>Quality/cost tradeoff</li>
 * </ul>
 */
@Service
public class RuntimeStrategyAdjuster {

    private static final Logger log = LoggerFactory.getLogger(RuntimeStrategyAdjuster.class);

    private final SystemEventBus eventBus;

    // Thresholds for deviation detection
    private static final double COST_DEVIATION_THRESHOLD = 0.2; // 20%
    private static final long DURATION_DEVIATION_THRESHOLD = 60000; // 1 minute
    private static final double FAILURE_RATE_THRESHOLD = 0.3; // 30%

    public RuntimeStrategyAdjuster(SystemEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Monitor execution and adjust strategy if needed.
     */
    public AdjustmentResult monitorAndAdjust(
            String jobId,
            String tenantId,
            ExecutionStrategyGraph currentStrategy,
            SystemCanonicalEvent latestEvent) {

        // Check for deviation
        DeviationResult deviation = detectDeviation(currentStrategy, latestEvent);

        if (!deviation.hasDeviation()) {
            return AdjustmentResult.noAdjustment(jobId);
        }

        log.warn("[{}] Deviation detected: {}", jobId, deviation.description());

        // Generate adjustment
        ExecutionStrategyGraph adjustedStrategy = adjustStrategy(
                currentStrategy, deviation, latestEvent);

        // Emit strategy adjusted event
        emitAdjustmentEvent(jobId, tenantId, deviation, adjustedStrategy);

        return new AdjustmentResult(
                jobId,
                true,
                deviation,
                adjustedStrategy,
                deviation.description(),
                Instant.now()
        );
    }

    /**
     * Detect deviation from expected performance.
     */
    private DeviationResult detectDeviation(
            ExecutionStrategyGraph strategy,
            SystemCanonicalEvent event) {

        // Check for billing deviation
        if (event.eventType().equals(SystemCanonicalEvent.BILLING_DECISION)) {
            String decision = event.getStringPayload("decision", "ALLOW");
            if ("DENY".equals(decision) || "THROTTLE".equals(decision)) {
                return new DeviationResult(
                        true,
                        DeviationType.BILLING_FAILURE,
                        "Billing decision: " + decision,
                        0.9
                );
            }
        }

        // Check for provider failure
        if (event.eventType().equals(SystemCanonicalEvent.PROVIDER_DECISION)) {
            boolean fallback = event.getBooleanPayload("fallbackTriggered", false);
            if (fallback) {
                return new DeviationResult(
                        true,
                        DeviationType.PROVIDER_FALLBACK,
                        "Provider fallback triggered",
                        0.7
                );
            }
        }

        // Check for execution state deviation
        if (event.eventType().equals(SystemCanonicalEvent.EXECUTION_STATE_CHANGE)) {
            String toState = event.getStringPayload("toState", "");
            if ("FAILED".equals(toState)) {
                return new DeviationResult(
                        true,
                        DeviationType.EXECUTION_FAILURE,
                        "Execution failed",
                        1.0
                );
            }
        }

        return DeviationResult.noDeviation();
    }

    /**
     * Adjust strategy based on deviation.
     */
    private ExecutionStrategyGraph adjustStrategy(
            ExecutionStrategyGraph currentStrategy,
            DeviationResult deviation,
            SystemCanonicalEvent triggeringEvent) {

        ExecutionStrategyGraph adjusted = currentStrategy;

        switch (deviation.type()) {
            case BILLING_FAILURE -> {
                // Switch to cheaper provider or lower preset
                adjusted = adjustForBillingFailure(adjusted, triggeringEvent);
            }
            case PROVIDER_FALLBACK -> {
                // Update fallback path
                adjusted = adjustForProviderFallback(adjusted, triggeringEvent);
            }
            case EXECUTION_FAILURE -> {
                // Switch to more reliable provider
                adjusted = adjustForExecutionFailure(adjusted, triggeringEvent);
            }
            case COST_DEVIATION -> {
                // Optimize for cost
                adjusted = adjustForCostDeviation(adjusted, triggeringEvent);
            }
            case DURATION_DEVIATION -> {
                // Optimize for speed
                adjusted = adjustForDurationDeviation(adjusted, triggeringEvent);
            }
        }

        return adjusted;
    }

    private ExecutionStrategyGraph adjustForBillingFailure(
            ExecutionStrategyGraph strategy, SystemCanonicalEvent event) {
        // Add cost optimization node
        ExecutionStrategyGraph.StrategyNode costNode = new ExecutionStrategyGraph.StrategyNode(
                "node-cost-adjusted",
                ExecutionStrategyGraph.StepType.COST_OPTIMIZATION,
                List.of(),
                null,
                0.8,
                "Adjusted for billing failure: switching to cheaper options",
                Instant.now()
        );
        return strategy.addNode(costNode);
    }

    private ExecutionStrategyGraph adjustForProviderFallback(
            ExecutionStrategyGraph strategy, SystemCanonicalEvent event) {
        // Update fallback strategy
        String failedProvider = event.getStringPayload("selectedProvider", "unknown");
        ExecutionStrategyGraph.StrategyNode fallbackNode = new ExecutionStrategyGraph.StrategyNode(
                "node-fallback-adjusted",
                ExecutionStrategyGraph.StepType.FALLBACK_PATH,
                List.of(),
                null,
                0.7,
                "Adjusted fallback path due to " + failedProvider + " failure",
                Instant.now()
        );
        return strategy.addNode(fallbackNode);
    }

    private ExecutionStrategyGraph adjustForExecutionFailure(
            ExecutionStrategyGraph strategy, SystemCanonicalEvent event) {
        // Switch to more reliable provider
        ExecutionStrategyGraph.StrategyNode retryNode = new ExecutionStrategyGraph.StrategyNode(
                "node-retry-adjusted",
                ExecutionStrategyGraph.StepType.RETRY_STRATEGY,
                List.of(),
                null,
                0.75,
                "Adjusted retry strategy for execution failure",
                Instant.now()
        );
        return strategy.addNode(retryNode);
    }

    private ExecutionStrategyGraph adjustForCostDeviation(
            ExecutionStrategyGraph strategy, SystemCanonicalEvent event) {
        ExecutionStrategyGraph.StrategyNode costNode = new ExecutionStrategyGraph.StrategyNode(
                "node-cost-optimized",
                ExecutionStrategyGraph.StepType.COST_OPTIMIZATION,
                List.of(),
                null,
                0.85,
                "Cost optimization adjustment",
                Instant.now()
        );
        return strategy.addNode(costNode);
    }

    private ExecutionStrategyGraph adjustForDurationDeviation(
            ExecutionStrategyGraph strategy, SystemCanonicalEvent event) {
        ExecutionStrategyGraph.StrategyNode speedNode = new ExecutionStrategyGraph.StrategyNode(
                "node-speed-optimized",
                ExecutionStrategyGraph.StepType.PARALLEL_EXECUTION,
                List.of(),
                null,
                0.8,
                "Speed optimization adjustment",
                Instant.now()
        );
        return strategy.addNode(speedNode);
    }

    private void emitAdjustmentEvent(String jobId, String tenantId,
                                        DeviationResult deviation,
                                        ExecutionStrategyGraph adjustedStrategy) {
        SystemCanonicalEvent event = SystemCanonicalEvent.create(
                SystemCanonicalEvent.STRATEGY_ADJUSTED,
                tenantId, null, jobId,
                "RuntimeStrategyAdjuster",
                Map.of(
                        "deviationType", deviation.type().name(),
                        "deviationDescription", deviation.description(),
                        "confidence", deviation.confidence(),
                        "adjustedSteps", adjustedStrategy.size()
                )
        );
        eventBus.publish(event);
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record AdjustmentResult(
            String jobId,
            boolean adjusted,
            DeviationResult deviation,
            ExecutionStrategyGraph adjustedStrategy,
            String reason,
            Instant adjustedAt
    ) {
        public static AdjustmentResult noAdjustment(String jobId) {
            return new AdjustmentResult(jobId, false, null, null, "No deviation detected", Instant.now());
        }
    }

    public record DeviationResult(
            boolean hasDeviation,
            DeviationType type,
            String description,
            double confidence
    ) {
        public static DeviationResult noDeviation() {
            return new DeviationResult(false, null, null, 0);
        }
    }

    public enum DeviationType {
        BILLING_FAILURE,
        PROVIDER_FALLBACK,
        EXECUTION_FAILURE,
        COST_DEVIATION,
        DURATION_DEVIATION,
        QUALITY_DEVIATION
    }
}
