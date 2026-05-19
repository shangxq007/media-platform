package com.example.platform.shared.entitlement;

import java.util.List;

/**
 * Port interface for entitlement validation, implemented by entitlement module.
 */
public interface EntitlementPort {
    ExportValidationResult validateExport(String tenantId, String userId,
            String requestedPreset, String outputFormat, long estimatedDurationSeconds);
    String getTier(String tenantId);

    record ExportValidationResult(
            boolean allowed,
            String reasonCode,
            String currentTier,
            String requestedPreset,
            String recommendedPreset,
            List<String> providerCandidates,
            double estimatedCost,
            String currency,
            Object budgetStatus,
            List<String> upgradeOptions,
            String userFriendlyMessage,
            List<String> violations,
            List<String> recommendations) {}
}
