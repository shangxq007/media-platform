package com.example.platform.commerce.domain;

public record CatalogActor(String tenantId, String principalType, String principalId, boolean globalCatalog) {
    public CatalogActor {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("actor tenant is required");
        if (principalType == null || principalType.isBlank()) throw new IllegalArgumentException("actor principal type is required");
        if (principalId == null || principalId.isBlank()) throw new IllegalArgumentException("actor principal id is required");
        if (globalCatalog && !"GLOBAL".equals(tenantId)) throw new IllegalArgumentException("global catalog actor must use GLOBAL scope");
    }

    public static CatalogActor global(String principalId, String principalType) {
        return new CatalogActor("GLOBAL", principalType, principalId, true);
    }

    public static CatalogActor tenant(String tenantId, String principalId, String principalType) {
        return new CatalogActor(tenantId, principalType, principalId, false);
    }

    public String catalogScope() { return globalCatalog ? "GLOBAL" : tenantId; }
}
