package com.example.platform.federation.graphql.dto;

import java.util.List;

public record MeOverview(
        String id,
        String displayName,
        TenantInfo currentTenant,
        WorkspaceInfo currentWorkspace,
        List<CapabilityDto> capabilities,
        List<NavigationRoute> navigation,
        BillingSummary billing
) {}
