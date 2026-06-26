package com.example.platform.shared.events;

/**
 * Published when a product is registered in the Product Runtime.
 */
public record ProductRegisteredEvent(
        String productId,
        String productType,
        String assetId,
        String projectId,
        String producerId) {}
