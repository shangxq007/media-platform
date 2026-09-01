package com.example.platform.compositeresource.domain;

public record FacetAddress(SemanticFacetId facetId) implements CompositeResourceAddress {
    public FacetAddress {
        if (facetId == null) {
            throw new IllegalArgumentException("FacetAddress requires facet identity");
        }
    }
}
