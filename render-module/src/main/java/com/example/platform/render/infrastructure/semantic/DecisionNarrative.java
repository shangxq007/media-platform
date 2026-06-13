package com.example.platform.render.infrastructure.semantic;

import java.util.List;

/**
 * Narrative explaining the decisions made during render job execution.
 */
public record DecisionNarrative(
        String summary,
        List<DecisionStep> steps,
        String reasoning
) {
    /**
     * Create a decision narrative.
     */
    public static DecisionNarrative create(List<DecisionStep> steps, String reasoning) {
        String summary = String.format("%d decisions made. %s", steps.size(), reasoning);
        return new DecisionNarrative(summary, steps, reasoning);
    }

    /**
     * Format as AI-consumable text.
     */
    public String toAiText() {
        StringBuilder sb = new StringBuilder();
        sb.append("**Summary:** ").append(summary).append("\n\n");
        sb.append("**Reasoning:** ").append(reasoning).append("\n\n");

        if (!steps.isEmpty()) {
            sb.append("**Decision Steps:**\n");
            for (int i = 0; i < steps.size(); i++) {
                DecisionStep step = steps.get(i);
                sb.append(i + 1).append(". ").append(step.description()).append("\n");
                if (step.reason() != null) {
                    sb.append("   Reason: ").append(step.reason()).append("\n");
                }
            }
        }

        return sb.toString();
    }

    /**
     * A single decision step.
     */
    public record DecisionStep(
            String description,
            String reason,
            String outcome
    ) {}
}
