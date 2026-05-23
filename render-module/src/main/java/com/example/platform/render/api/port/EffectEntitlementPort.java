package com.example.platform.render.api.port;

import java.util.List;

/**
 * Validates effect pack and effect tier access for a tenant before render/export.
 */
public interface EffectEntitlementPort {

    /**
     * @param tenantId   tenant identifier
     * @param tier       entitlement tier (FREE, PRO, …)
     * @param effectKeys effect keys used on the timeline
     * @param packIds    effect pack ids referenced by clips
     * @throws IllegalArgumentException when access is denied
     */
    void validateEffectAccess(String tenantId, String tier, List<String> effectKeys, List<String> packIds);
}
