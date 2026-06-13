package com.example.platform.render.infrastructure.productization.adaptive;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Adaptive Intelligence Layer (AIL) for system optimization.
 * 
 * <p>Analyzes UEEG graphs, billing outcomes, provider performance,
 * and failure patterns to generate optimization suggestions.
 */
public class AdaptiveEngine {

    /**
     * Analyze execution patterns and generate optimization suggestions.
     */
    public OptimizationReport analyzeExecutionPatterns(List<ExecutionTrace> traces) {
        List<CostOptimization> costOpts = analyzeCostOptimizations(traces);
        List<ProviderOptimization> providerOpts = analyzeProviderOptimizations(traces);
        List<PolicyOptimization> policyOpts = analyzePolicyOptimizations(traces);
        List<PerformanceOptimization> perfOpts = analyzePerformanceOptimizations(traces);

        double potentialSavings = costOpts.stream()
                .mapToDouble(CostOptimization::estimatedSavings)
                .sum();

        return new OptimizationReport(
                "opt-" + Instant.now().toEpochMilli(),
                costOpts, providerOpts, policyOpts, perfOpts,
                potentialSavings,
                generateSummary(costOpts, providerOpts, policyOpts, perfOpts),
                Instant.now()
        );
    }

    /**
     * Generate cost optimization suggestions.
     */
    private List<CostOptimization> analyzeCostOptimizations(List<ExecutionTrace> traces) {
        List<CostOptimization> suggestions = new java.util.ArrayList<>();

        // Analyze average cost by provider
        Map<String, Double> avgCostByProvider = traces.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ExecutionTrace::providerKey,
                        java.util.stream.Collectors.averagingDouble(ExecutionTrace::actualCost)
                ));

        // Find cheapest provider
        String cheapestProvider = avgCostByProvider.entrySet().stream()
                .min(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        if (cheapestProvider != null && avgCostByProvider.size() > 1) {
            double currentAvg = traces.stream()
                    .mapToDouble(ExecutionTrace::actualCost)
                    .average()
                    .orElse(0);
            double cheapestAvg = avgCostByProvider.get(cheapestProvider);
            double savings = currentAvg - cheapestAvg;

            if (savings > 0) {
                suggestions.add(new CostOptimization(
                        "cost-" + Instant.now().toEpochMilli(),
                        "Switch to cheaper provider",
                        String.format("Provider '%s' has %.1f%% lower average cost", 
                                cheapestProvider, (savings / currentAvg) * 100),
                        savings,
                        cheapestProvider,
                        "PROVIDER_SWITCH"
                ));
            }
        }

        // Analyze GPU usage
        long gpuJobs = traces.stream()
                .filter(ExecutionTrace::useGpu)
                .count();
        double gpuRatio = (double) gpuJobs / traces.size();

        if (gpuRatio < 0.1 && gpuJobs > 0) {
            suggestions.add(new CostOptimization(
                    "cost-gpu-" + Instant.now().toEpochMilli(),
                    "Review GPU usage",
                    "Less than 10% of jobs use GPU. Consider disabling GPU for most jobs.",
                    0.05,
                    null,
                    "GPU_OPTIMIZATION"
                ));
        }

        return suggestions;
    }

    /**
     * Generate provider optimization suggestions.
     */
    private List<ProviderOptimization> analyzeProviderOptimizations(List<ExecutionTrace> traces) {
        List<ProviderOptimization> suggestions = new java.util.ArrayList<>();

        // Analyze failure rates by provider
        Map<String, Long> failureCounts = traces.stream()
                .filter(t -> !t.success())
                .collect(java.util.stream.Collectors.groupingBy(
                        ExecutionTrace::providerKey,
                        java.util.stream.Collectors.counting()
                ));

        Map<String, Long> totalCounts = traces.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ExecutionTrace::providerKey,
                        java.util.stream.Collectors.counting()
                ));

        for (Map.Entry<String, Long> entry : failureCounts.entrySet()) {
            String provider = entry.getKey();
            long failures = entry.getValue();
            long total = totalCounts.getOrDefault(provider, 1L);
            double failureRate = (double) failures / total;

            if (failureRate > 0.2) {
                suggestions.add(new ProviderOptimization(
                        "prov-" + provider,
                        "High failure rate for provider " + provider,
                        String.format("%.1f%% failure rate (%d/%d)", failureRate * 100, failures, total),
                        provider,
                        "REVIEW_PROVIDER"
                ));
            }
        }

        return suggestions;
    }

    /**
     * Generate policy optimization suggestions.
     */
    private List<PolicyOptimization> analyzePolicyOptimizations(List<ExecutionTrace> traces) {
        List<PolicyOptimization> suggestions = new java.util.ArrayList<>();

        // Analyze rejection rates
        long rejections = traces.stream()
                .filter(t -> "REJECTED".equals(t.status()))
                .count();
        double rejectionRate = (double) rejections / traces.size();

        if (rejectionRate > 0.1) {
            suggestions.add(new PolicyOptimization(
                    "pol-rej",
                    "High rejection rate",
                    String.format("%.1f%% of jobs are being rejected. Consider reviewing policies.", 
                            rejectionRate * 100),
                    "REVIEW_POLICIES"
            ));
        }

        return suggestions;
    }

    /**
     * Generate performance optimization suggestions.
     */
    private List<PerformanceOptimization> analyzePerformanceOptimizations(List<ExecutionTrace> traces) {
        List<PerformanceOptimization> suggestions = new java.util.ArrayList<>();

        // Analyze average execution time
        double avgDuration = traces.stream()
                .mapToLong(ExecutionTrace::durationMs)
                .average()
                .orElse(0);

        if (avgDuration > 300000) { // 5 minutes
            suggestions.add(new PerformanceOptimization(
                    "perf-dur",
                    "Long average execution time",
                    String.format("Average execution time is %.1f minutes. Consider optimizing pipeline.", 
                            avgDuration / 60000),
                    "OPTIMIZE_PIPELINE"
            ));
        }

        return suggestions;
    }

    private String generateSummary(
            List<CostOptimization> costOpts,
            List<ProviderOptimization> providerOpts,
            List<PolicyOptimization> policyOpts,
            List<PerformanceOptimization> perfOpts) {
        int total = costOpts.size() + providerOpts.size() + policyOpts.size() + perfOpts.size();
        if (total == 0) {
            return "No optimization opportunities detected. System is running efficiently.";
        }
        return String.format("Found %d optimization opportunities: %d cost, %d provider, %d policy, %d performance.",
                total, costOpts.size(), providerOpts.size(), policyOpts.size(), perfOpts.size());
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record ExecutionTrace(
            String traceId,
            String providerKey,
            double actualCost,
            long durationMs,
            boolean useGpu,
            String status,
            boolean success,
            Instant timestamp
    ) {}

    public record OptimizationReport(
            String reportId,
            List<CostOptimization> costOptimizations,
            List<ProviderOptimization> providerOptimizations,
            List<PolicyOptimization> policyOptimizations,
            List<PerformanceOptimization> performanceOptimizations,
            double potentialSavings,
            String summary,
            Instant generatedAt
    ) {}

    public record CostOptimization(
            String id,
            String title,
            String description,
            double estimatedSavings,
            String recommendedProvider,
            String optimizationType
    ) {}

    public record ProviderOptimization(
            String id,
            String title,
            String description,
            String providerKey,
            String actionType
    ) {}

    public record PolicyOptimization(
            String id,
            String title,
            String description,
            String actionType
    ) {}

    public record PerformanceOptimization(
            String id,
            String title,
            String description,
            String actionType
    ) {}
}
