package com.example.platform.commerce.domain;

import java.time.Instant;

public record ProductCatalogEntry(String productId, String productCode, ProductLineType lineType,
        String displayName, ProductLifecycleState lifecycleState, long version,
        Instant createdAt, Instant updatedAt) {}
