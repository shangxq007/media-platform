package com.example.platform.compositeresource.domain;

public record SemanticObjectTypeId(String value) {
    public SemanticObjectTypeId {
        value = NamespacedIdentityPolicy.require(value, "SemanticObjectTypeId");
    }
}
