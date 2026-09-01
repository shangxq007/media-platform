package com.example.platform.compositeresource.domain;

public record SemanticObjectId(String value) {
    public SemanticObjectId {
        value = OpaqueIdentityPolicy.require(value, "SemanticObjectId");
    }
}
