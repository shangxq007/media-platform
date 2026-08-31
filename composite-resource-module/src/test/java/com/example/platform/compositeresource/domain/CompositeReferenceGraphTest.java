package com.example.platform.compositeresource.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.shared.digest.ContentDigest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CompositeReferenceGraphTest {

    @Test
    void acceptsSingleNodeOneHopAndMultiHopExactClosures() {
        CompositeResourceVersion c = emptyVersion("c", "v1");
        CompositeResourceVersion b = nestedVersion("b", "v1", c.pin());
        CompositeResourceVersion a = nestedVersion("a", "v1", b.pin());

        assertThat(CompositeReferenceGraph.fromResolvedVersions(List.of(c), true).nodes()).hasSize(1);
        assertThat(CompositeReferenceGraph.fromResolvedVersions(List.of(b, c), true).nodes()).hasSize(2);
        assertThat(CompositeReferenceGraph.fromResolvedVersions(List.of(c, a, b), true).nodes()).hasSize(3);
    }

    @Test
    void rejectsDirectTwoNodeAndMultiHopCyclesWithOrderedTypedDiagnostics() {
        CompositeResourceVersion direct = nestedVersion("a", "v1", arbitraryPin("a", "v1", "1"));
        assertCycle(List.of(direct), List.of(node("a", "v1"), node("a", "v1")));

        CompositeResourceVersion a2 = nestedVersion("a", "v1", arbitraryPin("b", "v1", "2"));
        CompositeResourceVersion b2 = nestedVersion("b", "v1", arbitraryPin("a", "v1", "3"));
        assertCycle(List.of(b2, a2), List.of(node("a", "v1"), node("b", "v1"), node("a", "v1")));

        CompositeResourceVersion a3 = nestedVersion("a", "v1", arbitraryPin("b", "v1", "4"));
        CompositeResourceVersion b3 = nestedVersion("b", "v1", arbitraryPin("c", "v1", "5"));
        CompositeResourceVersion c3 = nestedVersion("c", "v1", arbitraryPin("a", "v1", "6"));
        assertCycle(List.of(c3, b3, a3), List.of(
                node("a", "v1"), node("b", "v1"), node("c", "v1"), node("a", "v1")));
    }

    @Test
    void allowsSameResourceToReferenceAnOlderExactVersionWhenAcyclic() {
        CompositeResourceVersion v1 = emptyVersion("a", "v1");
        CompositeResourceVersion v2 = nestedVersion("a", "v2", v1.pin());

        CompositeReferenceGraph graph = CompositeReferenceGraph.fromResolvedVersions(List.of(v2, v1), true);
        assertThat(graph.nodes()).containsExactlyInAnyOrder(node("a", "v1"), node("a", "v2"));
    }

    @Test
    void completeClosureFailsClosedForAbsentOrDigestMismatchedExactNode() {
        CompositeResourceVersion absent = nestedVersion("a", "v1", arbitraryPin("missing", "v1", "7"));
        assertThatThrownBy(() -> CompositeReferenceGraph.fromResolvedVersions(List.of(absent), true))
                .isInstanceOfSatisfying(CompositeReferenceValidationException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo(CompositeReferenceErrorCode.INCOMPLETE_REFERENCE_CLOSURE));

        CompositeResourceVersion b = emptyVersion("b", "v1");
        CompositeResourceVersion mismatch = nestedVersion("a", "v1", arbitraryPin("b", "v1", "8"));
        assertThatThrownBy(() -> CompositeReferenceGraph.fromResolvedVersions(List.of(mismatch, b), true))
                .isInstanceOfSatisfying(CompositeReferenceValidationException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo(CompositeReferenceErrorCode.EXACT_PIN_DIGEST_MISMATCH));
    }

    private static void assertCycle(
            List<CompositeResourceVersion> versions,
            List<CompositeReferenceNodeId> expectedNodes) {
        assertThatThrownBy(() -> CompositeReferenceGraph.fromResolvedVersions(versions, true))
                .isInstanceOfSatisfying(CompositeReferenceValidationException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(CompositeReferenceErrorCode.CYCLE_DETECTED);
                    assertThat(exception.cycle()).isPresent();
                    assertThat(exception.cycle().orElseThrow().nodes()).containsExactlyElementsOf(expectedNodes);
                });
    }

    private static CompositeResourceVersion emptyVersion(String resource, String version) {
        return CompositeResourceModelTest.version(resource, version, Optional.empty(), List.of());
    }

    private static CompositeResourceVersion nestedVersion(
            String resource, String version, CompositeResourceVersionPin target) {
        SemanticFacet facet = new SemanticFacet(
                new SemanticFacetId("nested"),
                new SemanticFacetTypeId("platform:nested"),
                ComponentCollectionSemantics.ORDERED,
                List.of(new NestedCompositeResourceComponent(new CompositeComponentId("child"), target)));
        return CompositeResourceModelTest.version(resource, version, Optional.empty(), List.of(facet));
    }

    private static CompositeResourceVersionPin arbitraryPin(
            String resource, String version, String digestDigit) {
        return new CompositeResourceVersionPin(
                new CompositeResourceId(resource),
                new CompositeResourceVersionId(version),
                ContentDigest.sha256(digestDigit.repeat(64)));
    }

    private static CompositeReferenceNodeId node(String resource, String version) {
        return new CompositeReferenceNodeId(
                new CompositeResourceId(resource), new CompositeResourceVersionId(version));
    }
}
