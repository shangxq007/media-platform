package com.example.platform.compositeresource.domain;

public record CompositeResourceId(String value) {
    public CompositeResourceId {
        value = OpaqueIdentityPolicy.require(value, "CompositeResourceId");
    }
}
