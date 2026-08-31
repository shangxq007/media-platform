package com.example.platform.studio.scope;

public record StudioScope(TenantId tenantId, ProjectId projectId) {
    public StudioScope {
        if (tenantId == null || projectId == null) {
            throw new IllegalArgumentException("tenant and project scope are required");
        }
    }
}
