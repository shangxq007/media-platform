package com.example.platform.compositeresource.domain;

public record CompositeResourceVersionId(String value) {
    public CompositeResourceVersionId {
        value = OpaqueIdentityPolicy.require(value, "CompositeResourceVersionId");
    }
}
