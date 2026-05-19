package com.example.platform.federation.nlq.domain;

import java.util.List;

public record QueryDatasetPolicy(
    String datasetKey,
    List<String> requiredRoles,
    List<String> requiredPermissions,
    List<String> requiredEntitlements,
    List<String> requiredFeatureFlags,
    int maxRows,
    int maxLookbackDays
) {}
