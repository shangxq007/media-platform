package com.example.platform.delivery.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDeliveryPolicyRequest(
        @NotBlank String destinationId,
        String artifactSelector,
        String pathTemplate,
        String triggerMode) {

    public String artifactSelectorOrDefault() {
        return artifactSelector == null || artifactSelector.isBlank() ? "FINAL_ONLY" : artifactSelector;
    }

    public String pathTemplateOrDefault() {
        return pathTemplate == null || pathTemplate.isBlank()
                ? "{tenantId}/{projectId}/{jobId}/{filename}"
                : pathTemplate;
    }

    public String triggerModeOrDefault() {
        return triggerMode == null || triggerMode.isBlank() ? "AUTO" : triggerMode;
    }
}
