package com.example.platform.compositeresource.domain;

public record ComponentAddress(
        SemanticFacetId facetId,
        CompositeComponentId componentId) implements CompositeResourceAddress {
    public ComponentAddress {
        if (facetId == null || componentId == null) {
            throw new IllegalArgumentException("ComponentAddress requires facet and component identity");
        }
    }
}
