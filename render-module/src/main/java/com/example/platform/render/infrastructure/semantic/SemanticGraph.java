package com.example.platform.render.infrastructure.semantic;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Product Semantic Layer - transforms UEEG execution graphs into
 * human and AI consumable product meaning.
 * 
 * <p>This is a READ-ONLY transformation layer on top of the UnifiedRequestGraph.
 * It does NOT modify the UEEG.
 */
public record SemanticGraph(
        String requestId,
        String jobId,
        UserIntent userIntent,
        ProductOutcome productOutcome,
        String explanation,
        CostNarrative costNarrative,
        DecisionNarrative decisionNarrative,
        FailureNarrative failureNarrative,
        List<SemanticNode> nodes,
        List<SemanticEdge> edges,
        Instant generatedAt
) {
    /**
     * Create a semantic graph from a UEEG.
     */
    public static SemanticGraph fromUeeg(
            String requestId,
            String jobId,
            UserIntent userIntent,
            ProductOutcome productOutcome,
            String explanation,
            CostNarrative costNarrative,
            DecisionNarrative decisionNarrative,
            FailureNarrative failureNarrative,
            List<SemanticNode> nodes,
            List<SemanticEdge> edges) {
        return new SemanticGraph(
                requestId, jobId, userIntent, productOutcome,
                explanation, costNarrative, decisionNarrative,
                failureNarrative, nodes, edges, Instant.now()
        );
    }

    /**
     * Get a simplified summary for display.
     */
    public String getSummary() {
        return String.format("%s - %s", productOutcome.label(), explanation);
    }

    /**
     * Check if the request was successful.
     */
    public boolean isSuccess() {
        return productOutcome == ProductOutcome.COMPLETED;
    }

    /**
     * Check if the request failed.
     */
    public boolean isFailure() {
        return productOutcome == ProductOutcome.FAILED || productOutcome == ProductOutcome.REJECTED;
    }

    /**
     * Get all narrative texts for AI consumption.
     */
    public String toAiPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Render Job Analysis\n\n");
        sb.append("**Intent:** ").append(userIntent.label()).append("\n");
        sb.append("**Outcome:** ").append(productOutcome.label()).append("\n\n");
        sb.append("**Explanation:** ").append(explanation).append("\n\n");

        if (costNarrative != null) {
            sb.append("### Cost Analysis\n");
            sb.append(costNarrative.toAiText()).append("\n\n");
        }

        if (decisionNarrative != null) {
            sb.append("### Decision Reasoning\n");
            sb.append(decisionNarrative.toAiText()).append("\n\n");
        }

        if (failureNarrative != null) {
            sb.append("### Failure Analysis\n");
            sb.append(failureNarrative.toAiText()).append("\n\n");
        }

        return sb.toString();
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    /**
     * User intent behind the render job.
     */
    public enum UserIntent {
        RENDER_VIDEO("Render a video"),
        PREVIEW_VIDEO("Preview a video"),
        TEMPLATE_RENDER("Render from template"),
        BATCH_RENDER("Batch render"),
        EXPORT_PROJECT("Export project");

        private final String label;

        UserIntent(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * Product outcome of the render job.
     */
    public enum ProductOutcome {
        COMPLETED("Completed successfully", "success"),
        FAILED("Failed", "error"),
        REJECTED("Rejected", "warning"),
        IN_PROGRESS("In progress", "info"),
        QUEUED("Queued", "info"),
        CANCELLED("Cancelled", "warning");

        private final String label;
        private final String severity;

        ProductOutcome(String label, String severity) {
            this.label = label;
            this.severity = severity;
        }

        public String label() {
            return label;
        }

        public String severity() {
            return severity;
        }
    }
}
