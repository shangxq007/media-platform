package com.example.platform.artifact.api.event;

/**
 * Published when a new asset is registered via the Asset Registry.
 */
public record AssetRegisteredEvent(
        String assetId,
        String assetVersion,
        String assetType,
        String projectId,
        String tenantId) {
    public AssetRegisteredEvent {
        if(tenantId==null||tenantId.isBlank()||assetId==null||assetId.isBlank())throw new IllegalArgumentException("Registered asset identity and tenant required");
    }
}
