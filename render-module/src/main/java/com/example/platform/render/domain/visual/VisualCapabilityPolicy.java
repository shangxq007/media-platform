package com.example.platform.render.domain.visual;

import java.util.List;
import java.util.Map;

/**
 * Policy for visual capability validation and enforcement.
 * Pure, side-effect free. Internal domain model.
 *
 * <p>Encodes safety rules: forbidden capabilities are rejected,
 * restricted capabilities require manual review, POC capabilities
 * are internal-only, production capabilities may be eligible.</p>
 */
public final class VisualCapabilityPolicy {

    private VisualCapabilityPolicy() {}

    /**
     * Returns true if the capability is forbidden and must be rejected.
     */
    public static boolean isForbidden(VisualCapabilityDefinition capability) {
        if (capability == null) return true;
        return capability.status() == VisualCapabilityStatus.FORBIDDEN
                || capability.safetyLevel() == VisualCapabilitySafetyLevel.FORBIDDEN;
    }

    /**
     * Returns true if the capability requires manual review.
     */
    public static boolean requiresManualReview(VisualCapabilityDefinition capability) {
        if (capability == null) return true;
        return capability.status() == VisualCapabilityStatus.RESTRICTED
                || capability.safetyLevel() == VisualCapabilitySafetyLevel.RESTRICTED;
    }

    /**
     * Returns true if the capability is POC/internal-only.
     */
    public static boolean isInternalOnly(VisualCapabilityDefinition capability) {
        if (capability == null) return false;
        return capability.status() == VisualCapabilityStatus.POC
                || capability.status() == VisualCapabilityStatus.SPIKE;
    }

    /**
     * Returns true if the capability may be eligible for production.
     */
    public static boolean mayBeProductionEligible(VisualCapabilityDefinition capability) {
        if (capability == null) return false;
        return capability.isProductionAllowed();
    }

    /**
     * Validates that a capability definition is safe.
     * Returns empty list if valid, or issues if invalid.
     */
    public static List<VisualCapabilityIssue> validateCapability(VisualCapabilityDefinition capability) {
        if (capability == null) {
            return List.of(VisualCapabilityIssue.blocking(
                    VisualCapabilityIssueCode.INVALID_CAPABILITY_ID,
                    "Capability definition must not be null"));
        }

        if (isForbidden(capability)) {
            return List.of(VisualCapabilityIssue.blocking(
                    VisualCapabilityIssueCode.FORBIDDEN_CAPABILITY,
                    "Capability is forbidden: " + capability.id().value()));
        }

        if (requiresManualReview(capability)) {
            return List.of(VisualCapabilityIssue.warning(
                    VisualCapabilityIssueCode.RESTRICTED_CAPABILITY,
                    "Capability requires manual review: " + capability.id().value()));
        }

        return List.of();
    }

}
