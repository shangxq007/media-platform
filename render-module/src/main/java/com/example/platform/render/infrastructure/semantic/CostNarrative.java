package com.example.platform.render.infrastructure.semantic;

import java.util.List;

/**
 * Narrative explaining the cost of a render job.
 */
public record CostNarrative(
        String summary,
        double estimatedCost,
        double actualCost,
        String currency,
        List<CostLineItem> lineItems,
        String explanation
) {
    /**
     * Create a cost narrative.
     */
    public static CostNarrative create(double estimatedCost, double actualCost, String currency,
                                         List<CostLineItem> lineItems, String explanation) {
        String summary = String.format("Estimated: $%.4f %s, Actual: $%.4f %s",
                estimatedCost, currency, actualCost, currency);
        return new CostNarrative(summary, estimatedCost, actualCost, currency, lineItems, explanation);
    }

    /**
     * Create a simple cost narrative.
     */
    public static CostNarrative simple(double cost, String currency) {
        return new CostNarrative(
                String.format("$%.4f %s", cost, currency),
                cost, cost, currency, List.of(),
                String.format("The total cost for this render was $%.4f %s.", cost, currency)
        );
    }

    /**
     * Format as AI-consumable text.
     */
    public String toAiText() {
        StringBuilder sb = new StringBuilder();
        sb.append("**Summary:** ").append(summary).append("\n\n");
        sb.append("**Explanation:** ").append(explanation).append("\n\n");

        if (!lineItems.isEmpty()) {
            sb.append("**Breakdown:**\n");
            for (CostLineItem item : lineItems) {
                sb.append("- ").append(item.description()).append(": $")
                        .append(String.format("%.4f", item.amount())).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * A line item in the cost breakdown.
     */
    public record CostLineItem(
            String description,
            double amount,
            String category
    ) {}
}
