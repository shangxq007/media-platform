package com.example.platform.compositeresource.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.shared.digest.ContentDigest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompositeResourceDiffTest {

    @Test
    void reportsFacetAdditionRemovalTypeAndCollectionSemanticsChanges() {
        SemanticFacet original = facet("visual", "platform:visual", ComponentCollectionSemantics.ORDERED,
                external("primary", "v1", "1"));
        SemanticFacet changed = facet("visual", "platform:description", ComponentCollectionSemantics.UNORDERED,
                external("primary", "v1", "1"));
        SemanticFacet added = facet("metadata", "platform:metadata", ComponentCollectionSemantics.UNORDERED);

        CompositeResourceDiff changedDiff = CompositeResourceDiff.between(
                version("resource", "v1", List.of(original)),
                version("resource", "v2", List.of(changed, added)));
        assertThat(changedDiff.changes()).anyMatch(CompositeResourceChange.FacetAdded.class::isInstance);
        assertThat(changedDiff.changes()).anyMatch(CompositeResourceChange.FacetTypeChanged.class::isInstance);
        assertThat(changedDiff.changes()).anyMatch(CompositeResourceChange.CollectionSemanticsChanged.class::isInstance);

        CompositeResourceDiff removedDiff = CompositeResourceDiff.between(
                version("resource", "v2", List.of(changed, added)),
                version("resource", "v3", List.of(changed)));
        assertThat(removedDiff.changes()).containsExactly(
                new CompositeResourceChange.FacetRemoved(new SemanticFacetId("metadata")));
    }

    @Test
    void reportsComponentAdditionRemovalAndBindingVariantRebound() {
        SemanticFacet left = facet("visual", "platform:visual", ComponentCollectionSemantics.UNORDERED,
                external("removed", "v1", "1"),
                external("rebound", "v1", "2"));
        SemanticFacet right = facet("visual", "platform:visual", ComponentCollectionSemantics.UNORDERED,
                nested("rebound", "nested", "nv1", "3"),
                external("added", "v1", "4"));

        CompositeResourceDiff diff = CompositeResourceDiff.between(
                version("resource", "v1", List.of(left)),
                version("resource", "v2", List.of(right)));

        assertThat(diff.changes()).anyMatch(CompositeResourceChange.ComponentAdded.class::isInstance);
        assertThat(diff.changes()).anyMatch(CompositeResourceChange.ComponentRemoved.class::isInstance);
        assertThat(diff.changes()).anyMatch(CompositeResourceChange.ComponentRebound.class::isInstance);
    }

    @Test
    void reportsExternalAndNestedExactPinChangesSeparately() {
        SemanticFacet left = facet("visual", "platform:visual", ComponentCollectionSemantics.UNORDERED,
                external("external", "v1", "1"),
                nested("nested", "child", "nv1", "2"));
        SemanticFacet right = facet("visual", "platform:visual", ComponentCollectionSemantics.UNORDERED,
                external("external", "v2", "3"),
                nested("nested", "child", "nv2", "4"));

        CompositeResourceDiff diff = CompositeResourceDiff.between(
                version("resource", "v1", List.of(left)),
                version("resource", "v2", List.of(right)));

        assertThat(diff.changes()).hasSize(2);
        assertThat(diff.changes()).anyMatch(CompositeResourceChange.ExternalSemanticPinChanged.class::isInstance);
        assertThat(diff.changes()).anyMatch(CompositeResourceChange.NestedResourcePinChanged.class::isInstance);
    }

    @Test
    void orderedReorderEmitsMovesWhileUnorderedConstructionReorderIsEmpty() {
        SemanticComponent a = external("a", "v1", "1");
        SemanticComponent b = external("b", "v1", "2");
        SemanticFacet orderedLeft = facet(
                "visual", "platform:visual", ComponentCollectionSemantics.ORDERED, a, b);
        SemanticFacet orderedRight = facet(
                "visual", "platform:visual", ComponentCollectionSemantics.ORDERED, b, a);

        CompositeResourceDiff orderedDiff = CompositeResourceDiff.between(
                version("resource", "v1", List.of(orderedLeft)),
                version("resource", "v2", List.of(orderedRight)));
        assertThat(orderedDiff.changes())
                .allMatch(CompositeResourceChange.ComponentMoved.class::isInstance)
                .hasSize(2);

        SemanticFacet unorderedLeft = facet(
                "visual", "platform:visual", ComponentCollectionSemantics.UNORDERED, a, b);
        SemanticFacet unorderedRight = facet(
                "visual", "platform:visual", ComponentCollectionSemantics.UNORDERED, b, a);
        CompositeResourceDiff unorderedDiff = CompositeResourceDiff.between(
                version("resource", "v1", List.of(unorderedLeft)),
                version("resource", "v2", List.of(unorderedRight)));
        assertThat(unorderedDiff.changes()).isEmpty();
        assertThatThrownBy(() -> unorderedDiff.changes().add(
                new CompositeResourceChange.FacetAdded(new SemanticFacetId("forbidden"))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDifferentResourceIdentitiesAsTypedInvalidDiffInput() {
        assertThatThrownBy(() -> CompositeResourceDiff.between(
                version("left", "v1", List.of()), version("right", "v1", List.of())))
                .isInstanceOfSatisfying(CompositeResourceDiffException.class, exception ->
                        assertThat(exception.code()).isEqualTo(CompositeResourceDiffErrorCode.INVALID_DIFF_INPUT));
    }

    private static CompositeResourceVersion version(
            String resource, String version, List<SemanticFacet> facets) {
        return CompositeResourceModelTest.version(resource, version, Optional.empty(), facets);
    }

    private static SemanticFacet facet(
            String id,
            String type,
            ComponentCollectionSemantics semantics,
            SemanticComponent... components) {
        return new SemanticFacet(
                new SemanticFacetId(id), new SemanticFacetTypeId(type), semantics, List.of(components));
    }

    private static ExternalSemanticComponent external(String id, String version, String digit) {
        return new ExternalSemanticComponent(
                new CompositeComponentId(id),
                new ExactSemanticObjectPin(
                        new SemanticOwnerId("platform:catalog"),
                        new SemanticObjectTypeId("platform:image"),
                        new SemanticObjectId("object-" + id),
                        new SemanticObjectVersionId(version),
                        ContentDigest.sha256(digit.repeat(64))));
    }

    private static NestedCompositeResourceComponent nested(
            String id, String resource, String version, String digit) {
        return new NestedCompositeResourceComponent(
                new CompositeComponentId(id),
                new CompositeResourceVersionPin(
                        new CompositeResourceId(resource),
                        new CompositeResourceVersionId(version),
                        ContentDigest.sha256(digit.repeat(64))));
    }
}
