package com.example.platform.shared.events;

/**
 * Published when a product fails to be produced.
 */
public record ProductFailedEvent(
        String productId,
        String productType,
        String assetId,
        String error) {}
