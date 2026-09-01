package com.example.platform.compositeresource.domain;

public record SemanticFacetId(String value) {
    public SemanticFacetId {
        value = OpaqueIdentityPolicy.require(value, "SemanticFacetId");
    }
}
