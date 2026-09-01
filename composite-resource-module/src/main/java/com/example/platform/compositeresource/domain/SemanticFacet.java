package com.example.platform.compositeresource.domain;

import com.example.platform.shared.digest.ContentDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record SemanticFacet(
        SemanticFacetId facetId,
        SemanticFacetTypeId facetType,
        ComponentCollectionSemantics collectionSemantics,
        List<SemanticComponent> components) {
    public SemanticFacet {
        if (facetId == null || facetType == null || collectionSemantics == null || components == null) {
            throw new IllegalArgumentException("SemanticFacet requires identity, type, collection semantics, and components");
        }
        components = List.copyOf(components);
        Set<CompositeComponentId> identities = new HashSet<>();
        for (SemanticComponent component : components) {
            if (component == null || !identities.add(component.componentId())) {
                throw new IllegalArgumentException("SemanticFacet component identities must be non-null and unique");
            }
        }
    }

    public ContentDigest facetDigest() {
        return CompositeResourceCanonicalSerializerV1.digestFacet(this);
    }
}
