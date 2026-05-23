package com.example.platform.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateEventDefinitionRequest(
        @NotBlank String eventKey,
        @NotBlank String name,
        String description,
        @NotBlank String category,
        @NotBlank String severity,
        @NotBlank String visibility,
        boolean userConfigurable,
        boolean critical,
        boolean defaultEnabled,
        List<String> supportedChannels,
        List<String> requiredPermissions,
        List<String> requiredEntitlements,
        String featureFlagKey,
        String novuWorkflowId,
        String localTemplateKey
) {}
