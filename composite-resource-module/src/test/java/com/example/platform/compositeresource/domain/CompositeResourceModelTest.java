package com.example.platform.compositeresource.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.shared.digest.ContentDigest;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompositeResourceModelTest {
    private static final ContentDigest EXTERNAL_DIGEST = ContentDigest.sha256("1".repeat(64));

    @Test
    void exactPinsRequireEveryTypedIdentityVersionAndDigest() {
        ExactSemanticObjectPin externalPin = externalPin("object-v1", "1");
        CompositeResourceVersionPin resourcePin = resourcePin("nested", "nested-v1", "2");
        SemanticFacetPin facetPin = new SemanticFacetPin(
                resourcePin, new SemanticFacetId("visual"), ContentDigest.sha256("3".repeat(64)));
        CompositeComponentPin componentPin = new CompositeComponentPin(
                resourcePin,
                new SemanticFacetId("visual"),
                new CompositeComponentId("primary"),
                ContentDigest.sha256("4".repeat(64)));

        assertThat(externalPin.versionId()).isEqualTo(new SemanticObjectVersionId("object-v1"));
        assertThat(resourcePin.versionId()).isEqualTo(new CompositeResourceVersionId("nested-v1"));
        assertThat(facetPin.facetDigest().canonicalValue()).isEqualTo("3".repeat(64));
        assertThat(componentPin.componentDigest().canonicalValue()).isEqualTo("4".repeat(64));

        assertThatThrownBy(() -> new ExactSemanticObjectPin(
                null,
                new SemanticObjectTypeId("platform:image"),
                new SemanticObjectId("image-1"),
                new SemanticObjectVersionId("image-v1"),
                EXTERNAL_DIGEST)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompositeResourceVersionPin(
                new CompositeResourceId("nested"), null, EXTERNAL_DIGEST))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CompositeResourceVersionPin(
                new CompositeResourceId("nested"), new CompositeResourceVersionId("nested-v1"), null))
                .isInstanceOf(IllegalArgumentException.class);

        List<String> pinFields = Arrays.stream(ExactSemanticObjectPin.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertThat(pinFields).containsExactly("owner", "type", "objectId", "versionId", "contentDigest");
        assertThat(pinFields).noneMatch(name -> name.matches("(?i).*(latest|current|uri|path|provider|label).*"));
    }

    @Test
    void sealedComponentsHaveExactlyExternalAndNestedVariantsWithStableIds() {
        CompositeComponentId id = new CompositeComponentId("primary-image");
        SemanticComponent external = new ExternalSemanticComponent(id, externalPin("object-v1", "1"));
        SemanticComponent rebound = new ExternalSemanticComponent(id, externalPin("object-v2", "2"));
        SemanticComponent nested = new NestedCompositeResourceComponent(
                new CompositeComponentId("nested-scene"), resourcePin("nested", "nested-v1", "3"));

        assertThat(SemanticComponent.class.isSealed()).isTrue();
        assertThat(SemanticComponent.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(ExternalSemanticComponent.class, NestedCompositeResourceComponent.class);
        assertThat(external.componentId()).isEqualTo(rebound.componentId());
        assertThat(external.componentDigest()).isNotEqualTo(rebound.componentDigest());
        assertThat(nested).isInstanceOf(NestedCompositeResourceComponent.class);
    }

    @Test
    void facetsRejectDuplicateComponentsAndDefensivelyCopyOrderedAndUnorderedCollections() {
        SemanticComponent first = externalComponent("first", "object-1", "1");
        SemanticComponent second = externalComponent("second", "object-2", "2");
        ArrayList<SemanticComponent> source = new ArrayList<>(List.of(first, second));
        SemanticFacet ordered = new SemanticFacet(
                new SemanticFacetId("visual"),
                new SemanticFacetTypeId("platform:visual"),
                ComponentCollectionSemantics.ORDERED,
                source);
        source.clear();

        assertThat(ordered.components()).containsExactly(first, second);
        assertThatThrownBy(() -> ordered.components().add(first))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(ordered.facetDigest()).isNotNull();

        SemanticFacet unordered = new SemanticFacet(
                new SemanticFacetId("attachments"),
                new SemanticFacetTypeId("platform:attachments"),
                ComponentCollectionSemantics.UNORDERED,
                List.of(second, first));
        assertThat(unordered.collectionSemantics()).isEqualTo(ComponentCollectionSemantics.UNORDERED);

        assertThatThrownBy(() -> new SemanticFacet(
                new SemanticFacetId("duplicate"),
                new SemanticFacetTypeId("platform:visual"),
                ComponentCollectionSemantics.ORDERED,
                List.of(first, new ExternalSemanticComponent(first.componentId(), externalPin("other", "3")))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void versionsAreImmutableExactValuesWithOptionalParentAndDerivedVerifiedDigest() {
        SemanticFacet visual = facet("visual", ComponentCollectionSemantics.ORDERED,
                externalComponent("primary", "image-1", "1"));
        ArrayList<SemanticFacet> facets = new ArrayList<>(List.of(visual));
        CompositeResourceVersion first = version("resource-1", "version-1", Optional.empty(), facets);
        facets.clear();

        CompositeResourceVersion second = version(
                "resource-1", "version-2", Optional.of(first.versionId()), List.of(visual));

        assertThat(first.facets()).containsExactly(visual);
        assertThat(first.parentVersionId()).isEmpty();
        assertThat(second.parentVersionId()).contains(first.versionId());
        assertThat(second.resourceId()).isEqualTo(first.resourceId());
        assertThat(second.versionId()).isNotEqualTo(first.versionId());
        assertThat(second.semanticContentDigest()).isNotEqualTo(second.versionId().value());
        assertThatThrownBy(() -> first.facets().clear()).isInstanceOf(UnsupportedOperationException.class);

        CompositeResourceVersion verified = CompositeResourceVersion.create(
                first.resourceId(), first.versionId(), first.parentVersionId(), first.kind(), first.schemaVersion(),
                first.scope(), first.facets(), first.semanticContentDigest());
        assertThat(verified.semanticContentDigest()).isEqualTo(first.semanticContentDigest());
        assertThatThrownBy(() -> CompositeResourceVersion.create(
                first.resourceId(), first.versionId(), first.parentVersionId(), first.kind(), first.schemaVersion(),
                first.scope(), first.facets(), ContentDigest.sha256("f".repeat(64))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("digest");

        assertThatThrownBy(() -> version("resource-1", "version-3", Optional.empty(), List.of(visual, visual)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void localAddressesValidateAgainstOneExactVersionAndRejectDanglingPaths() {
        SemanticFacet visual = facet("visual", ComponentCollectionSemantics.ORDERED,
                externalComponent("primary", "image-1", "1"));
        CompositeResourceVersion version = version("resource-1", "version-1", Optional.empty(), List.of(visual));

        assertThat(version.validateAddress(new WholeResourceAddress())).isEqualTo(new WholeResourceAddress());
        assertThat(version.validateAddress(new FacetAddress(new SemanticFacetId("visual"))))
                .isEqualTo(new FacetAddress(new SemanticFacetId("visual")));
        assertThat(version.validateAddress(new ComponentAddress(
                new SemanticFacetId("visual"), new CompositeComponentId("primary"))))
                .isInstanceOf(ComponentAddress.class);

        assertThatThrownBy(() -> version.validateAddress(new FacetAddress(new SemanticFacetId("missing"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> version.validateAddress(new ComponentAddress(
                new SemanticFacetId("visual"), new CompositeComponentId("missing"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ExactSemanticObjectPin externalPin(String version, String digestDigit) {
        return new ExactSemanticObjectPin(
                new SemanticOwnerId("platform:catalog"),
                new SemanticObjectTypeId("platform:image"),
                new SemanticObjectId("image-1"),
                new SemanticObjectVersionId(version),
                ContentDigest.sha256(digestDigit.repeat(64)));
    }

    private static CompositeResourceVersionPin resourcePin(String resource, String version, String digestDigit) {
        return new CompositeResourceVersionPin(
                new CompositeResourceId(resource),
                new CompositeResourceVersionId(version),
                ContentDigest.sha256(digestDigit.repeat(64)));
    }

    private static ExternalSemanticComponent externalComponent(
            String component, String version, String digestDigit) {
        return new ExternalSemanticComponent(new CompositeComponentId(component), externalPin(version, digestDigit));
    }

    private static SemanticFacet facet(
            String id, ComponentCollectionSemantics semantics, SemanticComponent... components) {
        return new SemanticFacet(
                new SemanticFacetId(id), new SemanticFacetTypeId("platform:" + id), semantics, List.of(components));
    }

    static CompositeResourceVersion version(
            String resource,
            String version,
            Optional<CompositeResourceVersionId> parent,
            List<SemanticFacet> facets) {
        return CompositeResourceVersion.create(
                new CompositeResourceId(resource),
                new CompositeResourceVersionId(version),
                parent,
                new CompositeResourceKindId("platform:scene"),
                1,
                new CompositeResourceScope(
                        new SemanticOwnerId("platform:project-authority"), new SemanticObjectId("project-1")),
                facets);
    }
}
