package com.example.platform.render.infrastructure.semantic;

import java.util.List;
import java.util.Map;

/**
 * Product ViewModel for frontend consumption.
 * Provides a simplified, user-friendly view of the render job.
 */
public record ProductViewModel(
        String jobId,
        String statusLabel,
        String statusSeverity,
        CostSummary costSummary,
        ExecutionSummary executionSummary,
        ProviderSummary providerSummary,
        ArtifactSummary artifactSummary,
        List<UserActionSuggestion> userActionSuggestions,
        Map<String, Object> metadata
) {
    /**
     * Create a ProductViewModel from a SemanticGraph.
     */
    public static ProductViewModel fromSemanticGraph(SemanticGraph graph) {
        CostSummary costSummary = graph.costNarrative() != null
                ? new CostSummary(
                graph.costNarrative().summary(),
                graph.costNarrative().estimatedCost(),
                graph.costNarrative().actualCost(),
                graph.costNarrative().currency(),
                graph.costNarrative().explanation()
        )
                : new CostSummary("N/A", 0, 0, "USD", "No cost data available");

        ExecutionSummary executionSummary = new ExecutionSummary(
                graph.productOutcome().label(),
                graph.explanation(),
                graph.nodes().size()
        );

        ProviderSummary providerSummary = graph.nodes().stream()
                .filter(n -> n.type() == SemanticNode.SemanticNodeType.PROVIDER)
                .findFirst()
                .map(n -> new ProviderSummary(
                        n.getStringData("selectedProvider", "Unknown"),
                        n.getBooleanData("fallbackTriggered", false),
                        n.description()
                ))
                .orElse(new ProviderSummary("Unknown", false, "No provider data"));

        ArtifactSummary artifactSummary = graph.nodes().stream()
                .filter(n -> n.type() == SemanticNode.SemanticNodeType.ARTIFACT)
                .findFirst()
                .map(n -> new ArtifactSummary(
                        n.getStringData("artifactType", "Unknown"),
                        n.getStringData("uri", ""),
                        n.description()
                ))
                .orElse(null);

        List<UserActionSuggestion> suggestions = generateSuggestions(graph);

        return new ProductViewModel(
                graph.jobId(),
                graph.productOutcome().label(),
                graph.productOutcome().severity(),
                costSummary,
                executionSummary,
                providerSummary,
                artifactSummary,
                suggestions,
                Map.of()
        );
    }

    private static List<UserActionSuggestion> generateSuggestions(SemanticGraph graph) {
        List<UserActionSuggestion> suggestions = new java.util.ArrayList<>();

        if (graph.productOutcome() == SemanticGraph.ProductOutcome.FAILED) {
            suggestions.add(new UserActionSuggestion(
                    "retry",
                    "Retry the render job",
                    "The system will retry with the same or different provider.",
                    "primary"
            ));
        }

        if (graph.costNarrative() != null && graph.costNarrative().estimatedCost() > 1.0) {
            suggestions.add(new UserActionSuggestion(
                    "optimize",
                    "Optimize for lower cost",
                    "Consider using a lower resolution or different provider.",
                    "secondary"
            ));
        }

        return suggestions;
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record CostSummary(
            String summary,
            double estimatedCost,
            double actualCost,
            String currency,
            String explanation
    ) {}

    public record ExecutionSummary(
            String status,
            String explanation,
            int stepCount
    ) {}

    public record ProviderSummary(
            String providerName,
            boolean fallbackUsed,
            String explanation
    ) {}

    public record ArtifactSummary(
            String type,
            String uri,
            String description
    ) {}

    public record UserActionSuggestion(
            String action,
            String title,
            String description,
            String priority
    ) {}
}
