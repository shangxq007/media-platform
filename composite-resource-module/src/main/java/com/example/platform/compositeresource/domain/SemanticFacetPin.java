package com.example.platform.compositeresource.domain;

import com.example.platform.shared.digest.ContentDigest;

public record SemanticFacetPin(
        CompositeResourceVersionPin resourcePin,
        SemanticFacetId facetId,
        ContentDigest facetDigest) {
    public SemanticFacetPin {
        if (resourcePin == null || facetId == null || facetDigest == null) {
            throw new IllegalArgumentException("SemanticFacetPin requires exact resource, facet, and digest");
        }
    }
}
