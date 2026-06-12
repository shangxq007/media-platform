package com.example.platform.render.infrastructure;

import java.util.List;

/**
 * Determines whether a provider is eligible for dispatch based on its status,
 * capabilities, and the job requirements.
 *
 * <h3>Dispatch Rules</h3>
 * <ul>
 *   <li>PRODUCTION — always eligible (subject to capability match)</li>
 *   <li>POC — eligible only when explicitly allowed via configuration or experiment mode</li>
 *   <li>OPTIONAL — eligible when explicitly enabled</li>
 *   <li>STUB — never eligible (no real implementation)</li>
 *   <li>SKELETON — never eligible (not wired/production-tested)</li>
 *   <li>DEPRECATED — never eligible (superseded)</li>
 *   <li>MOCK — never eligible in production (test/dev only)</li>
 *   <li>HOLD — only in experiment/manual mode</li>
 *   <li>SPIKE — only in manual mode</li>
 * </ul>
 *
 * @see ProviderStatus for status definitions
 * @see ProviderMetadata for provider metadata
 */
public class ProviderEligibility {

    private ProviderEligibility() {}

    /**
     * Check if a provider is eligible for dispatch for the given job.
     *
     * @param metadata the provider metadata
     * @param job      the render job context
     * @return true if the provider can be dispatched
     */
    public static boolean isEligible(ProviderMetadata metadata, RenderJob job) {
        // Status-based filtering: never dispatch stub/skeleton/deprecated/mock
        if (!metadata.status().canBeConfiguredForDispatch()) {
            return false;
        }

        // PRODUCTION status: eligible if autoDispatch is true
        if (metadata.isProduction()) {
            if (!metadata.autoDispatch() && !"manual".equals(job.mode())) {
                return false;
            }
            return checkCapabilities(metadata, job);
        }

        // POC status: needs explicit allow
        if (metadata.isPoc()) {
            if (!isExplicitlyAllowed(metadata, job)) {
                return false;
            }
            return checkCapabilities(metadata, job);
        }

        // OPTIONAL status: needs explicit enable
        if (metadata.isOptional()) {
            if (!isExplicitlyAllowed(metadata, job)) {
                return false;
            }
            return checkCapabilities(metadata, job);
        }

        // HOLD status: only experiment/manual mode
        if (metadata.isHold()) {
            if (!"experiment".equals(job.mode()) && !"manual".equals(job.mode())) {
                return false;
            }
            return checkCapabilities(metadata, job);
        }

        // SPIKE: only manual mode
        if (metadata.isSpike()) {
            if (!"manual".equals(job.mode())) {
                return false;
            }
            return checkCapabilities(metadata, job);
        }

        return false;
    }

    /**
     * Score a provider for selection ordering. Lower score = preferred.
     */
    public static int scoreProvider(ProviderMetadata metadata, RenderJob job) {
        int score = 0;

        // Status-based scoring
        score += switch (metadata.status()) {
            case PRODUCTION -> 0;
            case POC -> 100;
            case OPTIONAL -> 200;
            case HOLD -> 300;
            case SPIKE -> 400;
            default -> 999; // Should not reach here for eligible providers
        };

        // Priority-based scoring
        score += switch (metadata.priority()) {
            case "P0" -> 0;
            case "P1" -> 10;
            case "P2" -> 20;
            case "P3" -> 30;
            default -> 40;
        };

        // Preferred provider bonus
        for (String preferred : job.preferredProviders()) {
            if (metadata.name().equals(preferred)) {
                score -= 50;
            }
        }

        return score;
    }

    private static boolean isExplicitlyAllowed(ProviderMetadata metadata, RenderJob job) {
        // Check if the job explicitly requests this provider
        for (String preferred : job.preferredProviders()) {
            if (metadata.name().equals(preferred)) {
                return true;
            }
        }
        // Check experiment/manual mode
        return "experiment".equals(job.mode()) || "manual".equals(job.mode());
    }

    private static boolean checkCapabilities(ProviderMetadata metadata, RenderJob job) {
        // Check blocked capabilities
        for (String notFor : metadata.notFor()) {
            if (job.requiredCapabilities().contains(notFor)) {
                return false;
            }
        }

        // Check blocked providers
        for (String blocked : job.blockedProviders()) {
            if (metadata.name().equals(blocked)) {
                return false;
            }
        }

        // Check required capabilities
        for (String required : job.requiredCapabilities()) {
            if (!metadata.canHandleCapability(required)) {
                return false;
            }
        }

        return true;
    }
}
