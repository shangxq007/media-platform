package com.example.platform.compositeresource.domain;

import com.example.platform.shared.digest.ContentDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class CompositeResourceVersion {
    private final CompositeResourceId resourceId;
    private final CompositeResourceVersionId versionId;
    private final Optional<CompositeResourceVersionId> parentVersionId;
    private final CompositeResourceKindId kind;
    private final int schemaVersion;
    private final CompositeResourceScope scope;
    private final List<SemanticFacet> facets;
    private final ContentDigest semanticContentDigest;

    private CompositeResourceVersion(
            CompositeResourceId resourceId,
            CompositeResourceVersionId versionId,
            Optional<CompositeResourceVersionId> parentVersionId,
            CompositeResourceKindId kind,
            int schemaVersion,
            CompositeResourceScope scope,
            List<SemanticFacet> facets) {
        if (resourceId == null || versionId == null || parentVersionId == null || kind == null || scope == null
                || facets == null || schemaVersion < 1) {
            throw new IllegalArgumentException("CompositeResourceVersion requires complete immutable identity and schema");
        }
        this.resourceId = resourceId;
        this.versionId = versionId;
        this.parentVersionId = parentVersionId;
        this.kind = kind;
        this.schemaVersion = schemaVersion;
        this.scope = scope;
        this.facets = List.copyOf(facets);
        Set<SemanticFacetId> identities = new HashSet<>();
        for (SemanticFacet facet : this.facets) {
            if (facet == null || !identities.add(facet.facetId())) {
                throw new IllegalArgumentException("CompositeResourceVersion facet identities must be non-null and unique");
            }
        }
        this.semanticContentDigest = CompositeResourceCanonicalSerializerV1.digestResource(this);
    }

    public static CompositeResourceVersion create(
            CompositeResourceId resourceId,
            CompositeResourceVersionId versionId,
            Optional<CompositeResourceVersionId> parentVersionId,
            CompositeResourceKindId kind,
            int schemaVersion,
            CompositeResourceScope scope,
            List<SemanticFacet> facets) {
        return new CompositeResourceVersion(
                resourceId, versionId, parentVersionId, kind, schemaVersion, scope, facets);
    }

    public static CompositeResourceVersion create(
            CompositeResourceId resourceId,
            CompositeResourceVersionId versionId,
            Optional<CompositeResourceVersionId> parentVersionId,
            CompositeResourceKindId kind,
            int schemaVersion,
            CompositeResourceScope scope,
            List<SemanticFacet> facets,
            ContentDigest expectedDigest) {
        if (expectedDigest == null) {
            throw new IllegalArgumentException("expected digest is required");
        }
        CompositeResourceVersion version = create(
                resourceId, versionId, parentVersionId, kind, schemaVersion, scope, facets);
        if (!version.semanticContentDigest.matches(expectedDigest)) {
            throw new IllegalArgumentException("expected digest does not match derived semantic digest");
        }
        return version;
    }

    public CompositeResourceId resourceId() { return resourceId; }
    public CompositeResourceVersionId versionId() { return versionId; }
    public Optional<CompositeResourceVersionId> parentVersionId() { return parentVersionId; }
    public CompositeResourceKindId kind() { return kind; }
    public int schemaVersion() { return schemaVersion; }
    public CompositeResourceScope scope() { return scope; }
    public List<SemanticFacet> facets() { return facets; }
    public ContentDigest semanticContentDigest() { return semanticContentDigest; }

    public CompositeResourceVersionPin pin() {
        return new CompositeResourceVersionPin(resourceId, versionId, semanticContentDigest);
    }

    public boolean semanticallyEquals(CompositeResourceVersion other) {
        return other != null && java.util.Arrays.equals(
                CompositeResourceCanonicalSerializerV1.serializeSemanticContent(this),
                CompositeResourceCanonicalSerializerV1.serializeSemanticContent(other));
    }

    public CompositeResourceAddress validateAddress(CompositeResourceAddress address) {
        if (address == null) {
            throw new IllegalArgumentException("address is required");
        }
        if (address instanceof WholeResourceAddress) {
            return address;
        }
        SemanticFacetId facetId = address instanceof FacetAddress facetAddress
                ? facetAddress.facetId()
                : ((ComponentAddress) address).facetId();
        SemanticFacet facet = facets.stream()
                .filter(candidate -> candidate.facetId().equals(facetId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("dangling local facet address"));
        if (address instanceof ComponentAddress componentAddress
                && facet.components().stream().noneMatch(component ->
                        component.componentId().equals(componentAddress.componentId()))) {
            throw new IllegalArgumentException("dangling local component address");
        }
        return address;
    }
}
