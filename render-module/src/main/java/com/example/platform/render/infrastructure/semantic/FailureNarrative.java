package com.example.platform.render.infrastructure.semantic;

import java.util.List;

/**
 * Narrative explaining a failure in the render job.
 */
public record FailureNarrative(
        String summary,
        String rootCause,
        String failureStage,
        List<String> contributingFactors,
        String suggestedAction,
        String explanation
) {
    /**
     * Create a failure narrative.
     */
    public static FailureNarrative create(String rootCause, String failureStage,
                                            List<String> contributingFactors,
                                            String suggestedAction, String explanation) {
        String summary = String.format("Failed at %s: %s", failureStage, rootCause);
        return new FailureNarrative(summary, rootCause, failureStage, contributingFactors,
                suggestedAction, explanation);
    }

    /**
     * Create a billing failure narrative.
     */
    public static FailureNarrative billingFailure(String reasonCode, String reasonMessage) {
        return new FailureNarrative(
                "Billing check failed: " + reasonMessage,
                reasonMessage,
                "billing",
                List.of("Reason code: " + reasonCode),
                "Please check your subscription or add credits.",
                String.format("The render job was rejected because the billing check failed with code %s: %s",
                        reasonCode, reasonMessage)
        );
    }

    /**
     * Create a policy failure narrative.
     */
    public static FailureNarrative policyFailure(String policyName, String reason) {
        return new FailureNarrative(
                "Policy constraint violated: " + reason,
                reason,
                "policy",
                List.of("Policy: " + policyName),
                "Please upgrade your plan or adjust your request.",
                String.format("The render job was rejected because a policy constraint was violated: %s", reason)
        );
    }

    /**
     * Create a provider failure narrative.
     */
    public static FailureNarrative providerFailure(String provider, String error) {
        return new FailureNarrative(
                "Provider execution failed: " + error,
                error,
                "provider",
                List.of("Provider: " + provider),
                "The system will automatically retry with a different provider.",
                String.format("The render job failed during execution with provider %s: %s", provider, error)
        );
    }

    /**
     * Format as AI-consumable text.
     */
    public String toAiText() {
        StringBuilder sb = new StringBuilder();
        sb.append("**Summary:** ").append(summary).append("\n\n");
        sb.append("**Root Cause:** ").append(rootCause).append("\n");
        sb.append("**Failure Stage:** ").append(failureStage).append("\n\n");

        if (!contributingFactors.isEmpty()) {
            sb.append("**Contributing Factors:**\n");
            for (String factor : contributingFactors) {
                sb.append("- ").append(factor).append("\n");
            }
            sb.append("\n");
        }

        sb.append("**Suggested Action:** ").append(suggestedAction).append("\n\n");
        sb.append("**Explanation:** ").append(explanation).append("\n");

        return sb.toString();
    }
}
