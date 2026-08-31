package com.example.platform.compositeresource.domain;

import com.example.platform.shared.digest.ContentDigest;

public record CompositeResourceVersionPin(
        CompositeResourceId resourceId,
        CompositeResourceVersionId versionId,
        ContentDigest contentDigest) {
    public CompositeResourceVersionPin {
        if (resourceId == null || versionId == null || contentDigest == null) {
            throw new IllegalArgumentException("CompositeResourceVersionPin requires exact resource, version, and digest");
        }
    }
}
