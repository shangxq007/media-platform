package com.example.platform.render.infrastructure.api;

public interface FontOwnershipChecker {
    boolean isOwnedByTenant(String fontAssetId, String tenantId);
    boolean isReady(String fontAssetId);
}
