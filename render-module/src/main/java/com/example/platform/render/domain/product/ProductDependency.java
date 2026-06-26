package com.example.platform.render.domain.product;

import java.time.Instant;

/**
 * Edge in the Product Graph — connects a product to its upstream dependency.
 */
public record ProductDependency(
        String dependencyId,
        String tenantId,
        String projectId,
        String productId,
        String dependsOnProductId,
        DependencyType dependencyType,
        Instant createdAt) {}
