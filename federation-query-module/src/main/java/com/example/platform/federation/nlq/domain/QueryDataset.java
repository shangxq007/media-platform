package com.example.platform.federation.nlq.domain;

import java.time.Instant;
import java.util.List;

public record QueryDataset(
    String datasetKey,
    String name,
    String description,
    String viewName,
    String module,
    String owner,
    boolean enabled,
    String defaultTimeField,
    boolean tenantScoped,
    boolean workspaceScoped,
    boolean userScoped,
    List<String> allowedRoles,
    List<String> allowedPermissions,
    int maxRows,
    int maxLookbackDays,
    String sensitivityLevel,
    Instant createdAt,
    Instant updatedAt
) {}
