package com.example.platform.shared.events;

/**
 * Published when a product dependency is created.
 */
public record ProductDependencyCreatedEvent(
        String dependencyId,
        String productId,
        String dependsOnProductId,
        String dependencyType) {}
