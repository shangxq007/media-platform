package com.example.platform.commerce.domain;

public record CatalogReadScope(String tenantId) {
    public CatalogReadScope {
        if (tenantId == null || tenantId.isBlank() || "GLOBAL".equals(tenantId)) {
            throw new IllegalArgumentException("a concrete tenant scope is required");
        }
    }
    public static CatalogReadScope tenant(String tenantId) { return new CatalogReadScope(tenantId); }
}
