package com.example.platform.compositeresource.domain;

import com.example.platform.shared.digest.ContentDigest;

public record ExactSemanticObjectPin(
        SemanticOwnerId owner,
        SemanticObjectTypeId type,
        SemanticObjectId objectId,
        SemanticObjectVersionId versionId,
        ContentDigest contentDigest) {
    public ExactSemanticObjectPin {
        if (owner == null || type == null || objectId == null || versionId == null || contentDigest == null) {
            throw new IllegalArgumentException("ExactSemanticObjectPin requires every exact typed value");
        }
    }
}
