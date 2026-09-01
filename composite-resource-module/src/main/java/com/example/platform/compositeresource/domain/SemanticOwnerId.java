package com.example.platform.compositeresource.domain;

public record SemanticOwnerId(String value) {
    public SemanticOwnerId {
        value = NamespacedIdentityPolicy.require(value, "SemanticOwnerId");
    }
}
