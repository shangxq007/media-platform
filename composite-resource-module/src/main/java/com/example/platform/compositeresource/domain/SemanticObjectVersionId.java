package com.example.platform.compositeresource.domain;

public record SemanticObjectVersionId(String value) {
    public SemanticObjectVersionId {
        value = OpaqueIdentityPolicy.require(value, "SemanticObjectVersionId");
    }
}
