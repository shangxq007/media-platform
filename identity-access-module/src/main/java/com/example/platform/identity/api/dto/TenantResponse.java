package com.example.platform.identity.api.dto;

import com.example.platform.identity.domain.Tenant;

public record TenantResponse(
        String id,
        String name,
        String status,
        java.time.Instant createdAt) {

    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(tenant.id(), tenant.name(), tenant.status().name(), tenant.createdAt());
    }
}
