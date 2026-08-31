package com.example.platform.compositeresource.domain;

public record SemanticFacetTypeId(String value) {
    public SemanticFacetTypeId {
        value = NamespacedIdentityPolicy.require(value, "SemanticFacetTypeId");
    }
}
