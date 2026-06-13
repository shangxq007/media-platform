package com.example.platform.render.infrastructure.strategy;

import com.example.platform.render.infrastructure.canonical.SystemCanonicalEvent;
import com.example.platform.render.infrastructure.canonical.SystemCanonicalGraph;
import com.example.platform.render.infrastructure.canonical.SystemEventBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Strategy Planner Engine - analyzes historical execution paths and generates
 * optimal execution plans BEFORE execution.
 * 
 * <p>Input: SystemCanonicalGraph (historical data)
 * <p>Output: ExecutionStrategyGraph (optimal plan)
 */
@Service
public class StrategyPlannerEngine {

    private static final Logger log = LoggerFactory.getLogger(StrategyPlannerEngine.class);

    private final SystemEventBus eventBus;
    private final ProviderScoringModel scoringModel;

    public StrategyPlannerEngine(SystemEventBus eventBus) {
        this.eventBus = eventBus;
        this.scoringModel = new ProviderScoringModel();
    }

    /**
     * Plan execution strategy for a new job.
     */
    public ExecutionStrategyGraph planExecution(
            String jobId,
            String tenantId,
            String providerCandidate,
            String preset,
            long estimatedDurationSeconds,
            boolean useGpu,
            List<ExecutionStrategyGraph.OptimizationGoal> goals) {

        log.info("Planning execution strategy for job {}", jobId);

        ExecutionStrategyGraph strategy = ExecutionStrategyGraph.create(jobId, tenantId, goals);

        // Step 1: Provider Selection Strategy
        ExecutionStrategyGraph.StrategyNode providerNode = planProviderSelection(
                providerCandidate, preset, estimatedDurationSeconds, useGpu, goals);
        strategy = strategy.addNode(providerNode);

        // Step 2: Cost Optimization Strategy
        ExecutionStrategyGraph.StrategyNode costNode = planCostOptimization(
                providerCandidate, preset, estimatedDurationSeconds, useGpu, goals);
        strategy = strategy.addNode(costNode);

        // Step 3: Retry Strategy
        ExecutionStrategyGraph.StrategyNode retryNode = planRetryStrategy(providerCandidate);
        strategy = strategy.addNode(retryNode);

        // Step 4: Fallback Strategy
        ExecutionStrategyGraph.StrategyNode fallbackNode = planFallbackStrategy(providerCandidate);
        strategy = strategy.addNode(fallbackNode);

        // Emit strategy planned event
        emitStrategyEvent(jobId, tenantId, SystemCanonicalEvent.STRATEGY_PLANNED, strategy);

        log.info("Strategy planned for job {} with {} steps, confidence: {}",
                jobId, strategy.size(), strategy.overallConfidence());
        return strategy;
    }

    /**
     * Plan provider selection strategy.
     */
    private ExecutionStrategyGraph.StrategyNode planProviderSelection(
            String providerCandidate, String preset, long durationSeconds,
            boolean useGpu, List<ExecutionStrategyGraph.OptimizationGoal> goals) {

        List<ExecutionStrategyGraph.ExecutionOption> options = new ArrayList<>();

        // Generate options for each provider
        String[] providers = {"ffmpeg", "gstreamer", "gpac", "mlt"};
        for (String provider : providers) {
            double cost = estimateCost(provider, preset, durationSeconds, useGpu);
            long duration = estimateDuration(provider, durationSeconds);
            double quality = scoringModel.getQualityScore(provider);
            double reliability = scoringModel.getReliabilityScore(provider);
            double confidence = calculateConfidence(cost, duration, quality, reliability, goals);

            options.add(new ExecutionStrategyGraph.ExecutionOption(
                    "opt-" + provider,
                    provider,
                    preset,
                    cost,
                    duration,
                    confidence,
                    quality,
                    reliability,
                    Map.of("useGpu", useGpu)
            ));
        }

        // Select best option
        ExecutionStrategyGraph.ExecutionOption bestOption = selectBestOption(options, goals);

        return new ExecutionStrategyGraph.StrategyNode(
                "node-provider",
                ExecutionStrategyGraph.StepType.PROVIDER_SELECTION,
                options,
                bestOption,
                bestOption.confidenceScore(),
                String.format("Selected %s based on %s optimization", bestOption.providerKey(), goals),
                Instant.now()
        );
    }

    /**
     * Plan cost optimization strategy.
     */
    private ExecutionStrategyGraph.StrategyNode planCostOptimization(
            String providerCandidate, String preset, long durationSeconds,
            boolean useGpu, List<ExecutionStrategyGraph.OptimizationGoal> goals) {

        List<ExecutionStrategyGraph.ExecutionOption> options = new ArrayList<>();

        // Option 1: Current configuration
        double currentCost = estimateCost(providerCandidate, preset, durationSeconds, useGpu);
        options.add(new ExecutionStrategyGraph.ExecutionOption(
                "opt-current", providerCandidate, preset, currentCost,
                durationSeconds * 1000, 0.9, 0.9, 0.9, Map.of("original", true)));

        // Option 2: Lower resolution
        String lowerPreset = getLowerPreset(preset);
        if (lowerPreset != null) {
            double lowerCost = estimateCost(providerCandidate, lowerPreset, durationSeconds, false);
            options.add(new ExecutionStrategyGraph.ExecutionOption(
                    "opt-lower", providerCandidate, lowerPreset, lowerCost,
                    durationSeconds * 1000, 0.7, 0.6, 0.9, Map.of("qualityTradeoff", true)));
        }

        // Option 3: Different provider
        String altProvider = getAlternativeProvider(providerCandidate);
        if (altProvider != null) {
            double altCost = estimateCost(altProvider, preset, durationSeconds, useGpu);
            options.add(new ExecutionStrategyGraph.ExecutionOption(
                    "opt-alt", altProvider, preset, altCost,
                    durationSeconds * 1000, 0.8, 0.85, 0.85, Map.of("providerSwitch", true)));
        }

        ExecutionStrategyGraph.ExecutionOption bestOption = selectBestOption(options, goals);

        return new ExecutionStrategyGraph.StrategyNode(
                "node-cost",
                ExecutionStrategyGraph.StepType.COST_OPTIMIZATION,
                options,
                bestOption,
                bestOption.confidenceScore(),
                String.format("Cost optimization: $%.4f with %s/%s", bestOption.estimatedCost(),
                        bestOption.providerKey(), bestOption.preset()),
                Instant.now()
        );
    }

    /**
     * Plan retry strategy.
     */
    private ExecutionStrategyGraph.StrategyNode planRetryStrategy(String providerCandidate) {
        List<ExecutionStrategyGraph.ExecutionOption> options = List.of(
                new ExecutionStrategyGraph.ExecutionOption(
                        "retry-immediate", providerCandidate, null, 0, 0,
                        0.9, 0.9, 0.9, Map.of("retryType", "immediate", "maxRetries", 3)),
                new ExecutionStrategyGraph.ExecutionOption(
                        "retry-backoff", providerCandidate, null, 0, 0,
                        0.85, 0.85, 0.95, Map.of("retryType", "exponential", "maxRetries", 5)),
                new ExecutionStrategyGraph.ExecutionOption(
                        "retry-fallback", providerCandidate, null, 0, 0,
                        0.8, 0.8, 0.9, Map.of("retryType", "fallback", "maxRetries", 2))
        );

        return new ExecutionStrategyGraph.StrategyNode(
                "node-retry",
                ExecutionStrategyGraph.StepType.RETRY_STRATEGY,
                options,
                options.get(0),
                0.9,
                "Retry with immediate fallback strategy",
                Instant.now()
        );
    }

    /**
     * Plan fallback strategy.
     */
    private ExecutionStrategyGraph.StrategyNode planFallbackStrategy(String providerCandidate) {
        List<ExecutionStrategyGraph.ExecutionOption> options = new ArrayList<>();

        String[] fallbackProviders = getFallbackChain(providerCandidate);
        for (int i = 0; i < fallbackProviders.length; i++) {
            String provider = fallbackProviders[i];
            options.add(new ExecutionStrategyGraph.ExecutionOption(
                    "fallback-" + i, provider, null, 0, 0,
                    0.8 - (i * 0.1), 0.85, 0.9 - (i * 0.05),
                    Map.of("priority", i)));
        }

        return new ExecutionStrategyGraph.StrategyNode(
                "node-fallback",
                ExecutionStrategyGraph.StepType.FALLBACK_PATH,
                options,
                options.isEmpty() ? null : options.get(0),
                options.isEmpty() ? 0.5 : 0.8,
                "Fallback chain: " + String.join(" -> ", fallbackProviders),
                Instant.now()
        );
    }

    // ---------------------------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------------------------

    private ExecutionStrategyGraph.ExecutionOption selectBestOption(
            List<ExecutionStrategyGraph.ExecutionOption> options,
            List<ExecutionStrategyGraph.OptimizationGoal> goals) {
        if (options.isEmpty()) return null;

        return options.stream()
                .max(Comparator.comparingDouble(opt -> calculateScore(opt, goals)))
                .orElse(options.get(0));
    }

    private double calculateScore(ExecutionStrategyGraph.ExecutionOption option,
                                    List<ExecutionStrategyGraph.OptimizationGoal> goals) {
        return goals.stream()
                .mapToDouble(goal -> option.getScore(goal))
                .average()
                .orElse(0.0);
    }

    private double calculateConfidence(double cost, long duration, double quality,
                                         double reliability, List<ExecutionStrategyGraph.OptimizationGoal> goals) {
        // Weighted average based on goals
        double costScore = 1.0 - Math.min(cost / 10.0, 1.0);
        double speedScore = 1.0 - Math.min(duration / 600000.0, 1.0);

        double total = 0;
        double weight = 0;

        for (ExecutionStrategyGraph.OptimizationGoal goal : goals) {
            switch (goal) {
                case COST -> { total += costScore; weight += 1; }
                case SPEED -> { total += speedScore; weight += 1; }
                case QUALITY -> { total += quality; weight += 1; }
                case RELIABILITY -> { total += reliability; weight += 1; }
            }
        }

        return weight > 0 ? total / weight : 0.5;
    }

    private double estimateCost(String provider, String preset, long durationSeconds, boolean useGpu) {
        // Simplified cost estimation
        double baseCost = 0.05 * (durationSeconds / 3600.0);
        if (useGpu) baseCost *= 2.0;
        if (preset != null && preset.contains("4k")) baseCost *= 3.5;
        return baseCost;
    }

    private long estimateDuration(String provider, long baseDurationSeconds) {
        // Simplified duration estimation
        return baseDurationSeconds * 1000;
    }

    private String getLowerPreset(String preset) {
        if (preset == null) return null;
        if (preset.contains("4k")) return "default_1080p";
        if (preset.contains("1080p")) return "default_720p";
        return null;
    }

    private String getAlternativeProvider(String current) {
        if ("ffmpeg".equals(current)) return "gstreamer";
        if ("gstreamer".equals(current)) return "ffmpeg";
        return null;
    }

    private String[] getFallbackChain(String provider) {
        return switch (provider) {
            case "ffmpeg" -> new String[]{"gstreamer", "gpac", "mlt"};
            case "gstreamer" -> new String[]{"ffmpeg", "gpac"};
            default -> new String[]{"ffmpeg"};
        };
    }

    private void emitStrategyEvent(String jobId, String tenantId, String eventType,
                                     ExecutionStrategyGraph strategy) {
        SystemCanonicalEvent event = SystemCanonicalEvent.create(
                eventType, tenantId, null, jobId,
                "StrategyPlannerEngine",
                Map.of("strategyId", strategy.strategyId(),
                        "stepCount", strategy.size(),
                        "confidence", strategy.overallConfidence())
        );
        eventBus.publish(event);
    }

    // ---------------------------------------------------------------------------
    // Provider Scoring Model
    // ---------------------------------------------------------------------------

    private static class ProviderScoringModel {
        private final Map<String, Double> qualityScores = Map.of(
                "ffmpeg", 0.9,
                "gstreamer", 0.85,
                "gpac", 0.8,
                "mlt", 0.75
        );

        private final Map<String, Double> reliabilityScores = Map.of(
                "ffmpeg", 0.95,
                "gstreamer", 0.85,
                "gpac", 0.8,
                "mlt", 0.75
        );

        double getQualityScore(String provider) {
            return qualityScores.getOrDefault(provider, 0.7);
        }

        double getReliabilityScore(String provider) {
            return reliabilityScores.getOrDefault(provider, 0.7);
        }
    }
}
