package com.example.platform.notification.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record NotificationEventDefinition(
        String eventKey,
        String name,
        String description,
        String category,
        String severity,
        String visibility,
        boolean userConfigurable,
        boolean critical,
        boolean defaultEnabled,
        List<String> supportedChannels,
        List<String> requiredPermissions,
        List<String> requiredEntitlements,
        String featureFlagKey,
        String novuWorkflowId,
        String localTemplateKey,
        boolean archived,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
