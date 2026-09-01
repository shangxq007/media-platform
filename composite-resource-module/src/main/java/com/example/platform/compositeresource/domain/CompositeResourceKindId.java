package com.example.platform.compositeresource.domain;

public record CompositeResourceKindId(String value) {
    public CompositeResourceKindId {
        value = NamespacedIdentityPolicy.require(value, "CompositeResourceKindId");
    }
}
