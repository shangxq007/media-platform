package com.example.platform.render.domain.governance;

/**
 * Multi-dimensional attribution for a meter event.
 * Immutable. Composable — one event may have multiple dimensions.
 */
public record MeterAttribution(
        String tenantId,
        String projectId,
        String assetId,
        String productId,
        String producerId,
        String backendId,
        String environmentId) {

    public static MeterAttribution producer(String producerId) {
        return new MeterAttribution(null, null, null, null, producerId, null, null);
    }

    public static MeterAttribution tenant(String tenantId) {
        return new MeterAttribution(tenantId, null, null, null, null, null, null);
    }
}
