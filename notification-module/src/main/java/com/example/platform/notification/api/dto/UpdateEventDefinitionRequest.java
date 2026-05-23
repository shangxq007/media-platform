package com.example.platform.notification.api.dto;

import java.util.List;

public record UpdateEventDefinitionRequest(
        String name,
        String description,
        String category,
        String severity,
        String visibility,
        Boolean userConfigurable,
        Boolean critical,
        Boolean defaultEnabled,
        List<String> supportedChannels,
        List<String> requiredPermissions,
        List<String> requiredEntitlements,
        String featureFlagKey,
        String novuWorkflowId,
        String localTemplateKey
) {}
