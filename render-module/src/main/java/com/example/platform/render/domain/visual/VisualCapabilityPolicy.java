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
     * Returns true if auto-dispatch is allowed for this provider support.
     */
    public static boolean isAutoDispatchAllowed(ProviderVisualCapabilitySupport support) {
        if (support == null) return false;
        return support.isAutoDispatchEligible();
    }

    /**
     * Returns true if the provider support is production-eligible.
     */
    public static boolean isProductionAllowed(ProviderVisualCapabilitySupport support) {
        if (support == null) return false;
        return support.isProductionEligible();
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

    /**
     * Validates provider support for a capability.
     * Returns empty list if valid, or issues if invalid.
     */
    public static List<VisualCapabilityIssue> validateProviderSupport(
            VisualCapabilityDefinition capability,
            ProviderVisualCapabilitySupport support) {
        if (capability == null || support == null) {
            return List.of(VisualCapabilityIssue.blocking(
                    VisualCapabilityIssueCode.INVALID_CAPABILITY_ID,
                    "Capability and support must not be null"));
        }

        if (isForbidden(capability)) {
            return List.of(VisualCapabilityIssue.blocking(
                    VisualCapabilityIssueCode.FORBIDDEN_CAPABILITY,
                    "Capability is forbidden: " + capability.id().value()));
        }

        if (!support.isProductionEligible() && capability.isProductionAllowed()) {
            return List.of(VisualCapabilityIssue.warning(
                    VisualCapabilityIssueCode.PROVIDER_NOT_PRODUCTION_ALLOWED,
                    "Provider does not support production use for: " + capability.id().value()));
        }

        if (support.consistencyLevel() == VisualConsistencyLevel.UNKNOWN) {
            return List.of(VisualCapabilityIssue.warning(
                    VisualCapabilityIssueCode.CONSISTENCY_LEVEL_UNKNOWN,
                    "Consistency level unknown for: " + capability.id().value()));
        }

        return List.of();
    }
}
