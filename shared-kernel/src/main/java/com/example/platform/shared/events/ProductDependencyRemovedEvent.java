package com.example.platform.shared.events;

/**
 * Published when a product dependency is removed.
 */
public record ProductDependencyRemovedEvent(
        String dependencyId,
        String productId,
        String dependsOnProductId) {}
