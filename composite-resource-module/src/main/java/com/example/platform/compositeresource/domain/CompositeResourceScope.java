package com.example.platform.compositeresource.domain;

public record CompositeResourceScope(
        SemanticOwnerId authority,
        SemanticObjectId externalScopeId) {
    public static final String IDENTITY_SCOPE_AUTHORITY = "EXTERNAL";

    public CompositeResourceScope {
        if (authority == null || externalScopeId == null) {
            throw new IllegalArgumentException("CompositeResourceScope requires an external authority reference");
        }
    }
}
