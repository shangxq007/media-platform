package com.example.platform.compositeresource.domain;

import com.example.platform.shared.digest.ContentDigest;

public record CompositeComponentPin(
        CompositeResourceVersionPin resourcePin,
        SemanticFacetId facetId,
        CompositeComponentId componentId,
        ContentDigest componentDigest) {
    public CompositeComponentPin {
        if (resourcePin == null || facetId == null || componentId == null || componentDigest == null) {
            throw new IllegalArgumentException("CompositeComponentPin requires exact resource, facet, component, and digest");
        }
    }
}
