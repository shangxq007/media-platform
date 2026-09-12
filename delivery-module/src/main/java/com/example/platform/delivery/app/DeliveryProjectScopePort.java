package com.example.platform.delivery.app;

/** Read-only composition seam to the canonical Project owner. */
public interface DeliveryProjectScopePort {
    boolean belongsToTenant(String tenantId, String projectId);
}
