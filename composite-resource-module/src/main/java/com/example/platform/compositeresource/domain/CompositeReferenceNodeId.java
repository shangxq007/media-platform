package com.example.platform.compositeresource.domain;

public record CompositeReferenceNodeId(
        CompositeResourceId resourceId,
        CompositeResourceVersionId versionId) implements Comparable<CompositeReferenceNodeId> {
    public CompositeReferenceNodeId {
        if (resourceId == null || versionId == null) {
            throw new IllegalArgumentException("CompositeReferenceNodeId requires resource and exact version");
        }
    }

    @Override
    public int compareTo(CompositeReferenceNodeId other) {
        int resourceOrder = resourceId.value().compareTo(other.resourceId.value());
        return resourceOrder != 0 ? resourceOrder : versionId.value().compareTo(other.versionId.value());
    }
}
