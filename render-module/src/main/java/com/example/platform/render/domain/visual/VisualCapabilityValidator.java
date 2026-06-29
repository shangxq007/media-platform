package com.example.platform.render.domain.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validator for visual capabilities and provider support.
 * Pure, side-effect free. Internal domain model.
 */
public final class VisualCapabilityValidator {

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "filter_complex", "filtergraph", "rawCommand", "shell command",
            "Runtime.getRuntime", "ProcessBuilder", "npx remotion",
            "remotion render", "npm install", "pnpm", "yarn");

    private VisualCapabilityValidator() {}

    /**
     * Validates a capability definition for safety.
     */
    public static List<VisualCapabilityIssue> validateDefinition(VisualCapabilityDefinition definition) {
        if (definition == null) {
            return List.of(VisualCapabilityIssue.blocking(
                    VisualCapabilityIssueCode.INVALID_CAPABILITY_ID,
                    "Definition must not be null"));
        }

        List<VisualCapabilityIssue> issues = new ArrayList<>();

        // Check forbidden status
        if (VisualCapabilityPolicy.isForbidden(definition)) {
            issues.add(VisualCapabilityIssue.blocking(
                    VisualCapabilityIssueCode.FORBIDDEN_CAPABILITY,
                    "Capability is forbidden: " + definition.id().value()));
        }

        // Check safety level
        if (definition.safetyLevel() == VisualCapabilitySafetyLevel.FORBIDDEN) {
            issues.add(VisualCapabilityIssue.blocking(
                    VisualCapabilityIssueCode.SAFETY_LEVEL_FORBIDDEN,
                    "Safety level is FORBIDDEN: " + definition.id().value()));
        }

        // Check parameter safety
        for (var param : definition.parameters()) {
            if (param.required() && param.defaultValue() == null) {
                issues.add(VisualCapabilityIssue.warning(
                        VisualCapabilityIssueCode.MISSING_REQUIRED_PARAMETER,
                        "Required parameter has no default: " + param.name()));
            }
        }

        return issues;
    }

    /**
     * Validates provider support declaration.
     */
    public static List<VisualCapabilityIssue> validateProviderSupport(
            ProviderVisualCapabilitySupport support) {
        if (support == null) {
            return List.of(VisualCapabilityIssue.blocking(
                    VisualCapabilityIssueCode.UNSUPPORTED_PROVIDER,
                    "Support declaration must not be null"));
        }

        List<VisualCapabilityIssue> issues = new ArrayList<>();

        // Auto-dispatch check
        if (support.autoDispatchAllowed() && !support.isAutoDispatchEligible()) {
            issues.add(VisualCapabilityIssue.error(
                    VisualCapabilityIssueCode.AUTO_DISPATCH_NOT_ALLOWED,
                    "Auto-dispatch not allowed for: " + support.visualCapabilityId().value()));
        }

        // Production check
        if (support.productionAllowed() && !support.isProductionEligible()) {
            issues.add(VisualCapabilityIssue.error(
                    VisualCapabilityIssueCode.PROVIDER_NOT_PRODUCTION_ALLOWED,
                    "Production not allowed for: " + support.visualCapabilityId().value()));
        }

        // Consistency check
        if (support.consistencyLevel() == VisualConsistencyLevel.UNKNOWN) {
            issues.add(VisualCapabilityIssue.warning(
                    VisualCapabilityIssueCode.CONSISTENCY_LEVEL_UNKNOWN,
                    "Consistency level unknown for: " + support.visualCapabilityId().value()));
        }

        return issues;
    }

    /**
     * Returns true if the capability is production-allowed.
     */
    public static boolean isProductionAllowed(VisualCapabilityDefinition capability) {
        return VisualCapabilityPolicy.mayBeProductionEligible(capability);
    }

    /**
     * Returns true if auto-dispatch is allowed for the provider support.
     */
    public static boolean isAutoDispatchAllowed(ProviderVisualCapabilitySupport support) {
        return VisualCapabilityPolicy.isAutoDispatchAllowed(support);
    }

    /**
     * Returns true if the capability requires manual review.
     */
    public static boolean requiresManualReview(VisualCapabilityDefinition capability) {
        return VisualCapabilityPolicy.requiresManualReview(capability);
    }

    /**
     * Checks for forbidden keywords in metadata.
     */
    public static List<VisualCapabilityIssue> checkForbiddenMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) return List.of();

        List<VisualCapabilityIssue> issues = new ArrayList<>();
        for (var entry : metadata.entrySet()) {
            String key = entry.getKey() != null ? entry.getKey().toLowerCase() : "";
            String value = entry.getValue() != null ? entry.getValue().toLowerCase() : "";
            for (String keyword : FORBIDDEN_KEYWORDS) {
                if (key.contains(keyword) || value.contains(keyword)) {
                    issues.add(VisualCapabilityIssue.blocking(
                            VisualCapabilityIssueCode.PROVIDER_INTERNAL_LEAK,
                            "Metadata contains forbidden keyword: " + keyword));
                    break;
                }
            }
        }
        return issues;
    }
}
