package com.example.platform.shared.events;

/**
 * Published when a product becomes READY for consumption.
 */
public record ProductReadyEvent(
        String productId,
        String productType,
        String assetId) {}
