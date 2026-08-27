package com.example.platform.graph.faof2;

import com.example.platform.graph.faof2.Faof2WitnessCorpus.Corpus;
import com.example.platform.graph.faof2.Faof2WitnessCorpus.Fixture;
import com.example.platform.graph.faof2.Faof2WitnessCorpus.SemanticNode;
import com.example.platform.graph.faof2.Faof2WitnessCorpus.Witness;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

abstract class CustomBackendFaof2ConformanceTest {

    private static final Comparator<SemanticNode> SEMANTIC_ID_ORDER =
            Comparator.comparing(SemanticNode::id);

    abstract Faof2GraphBackend backend();

    @Test
    void sharedWitnessCorpusConformsToPlatformGraphLaws() throws Exception {
        Corpus corpus = Faof2WitnessCorpus.load();
        assertThat(corpus.schemaVersion()).isEqualTo(1);
        assertThat(corpus.witnesses()).hasSizeGreaterThanOrEqualTo(18);
        Map<String, List<String>> constructionOrders = new HashMap<>();

        for (Witness witness : corpus.witnesses()) {
            Fixture fixture = Faof2WitnessCorpus.fixture(witness);
            Set<SemanticNode> nodesBefore = fixture.graph().nodes();
            int edgesBefore = fixture.graph().edgeCount();
            Faof2GraphBackend.Topology topology = backend().topologicalOrder(fixture, SEMANTIC_ID_ORDER);

            assertThat(backend().hasCycle(fixture))
                    .as(backend().name() + ": " + witness.id() + " cycle")
                    .isEqualTo(witness.expected().cycle());
            assertTopology(witness, topology);
            assertThat(backend().topologicalOrder(fixture, SEMANTIC_ID_ORDER))
                    .as(backend().name() + ": " + witness.id() + " repeated result")
                    .isEqualTo(topology);
            assertThat(fixture.graph().nodes()).as(witness.id() + " nodes unchanged")
                    .isEqualTo(nodesBefore);
            assertThat(fixture.graph().edgeCount()).as(witness.id() + " edges unchanged")
                    .isEqualTo(edgesBefore);

            if (!witness.sources().isEmpty()) {
                Set<SemanticNode> reachable = backend().reachableFrom(
                        fixture, fixture.nodes(witness.sources()));
                assertThat(ids(reachable)).as(witness.id() + " reachable")
                        .containsExactlyInAnyOrderElementsOf(witness.expected().reachable());
            }
            if (witness.bounded() != null) {
                Set<SemanticNode> descendants = backend().descendantsBounded(
                        fixture, fixture.node(witness.bounded().start()), witness.bounded().depth());
                assertThat(ids(descendants)).as(witness.id() + " bounded descendants")
                        .containsExactlyInAnyOrderElementsOf(witness.expected().descendants());
            }
            if (witness.expected().edgeCount() != null) {
                assertThat(backend().edgeCount(fixture)).as(witness.id() + " edge count")
                        .isEqualTo(witness.expected().edgeCount().longValue());
            }
            if (!witness.expected().roots().isEmpty()) {
                assertThat(ids(backend().roots(fixture))).containsExactlyInAnyOrderElementsOf(
                        witness.expected().roots());
            }
            if (!witness.expected().sinks().isEmpty()) {
                assertThat(ids(backend().sinks(fixture))).containsExactlyInAnyOrderElementsOf(
                        witness.expected().sinks());
            }
            if (witness.constructionGroup() != null && !topology.cycle()) {
                constructionOrders.computeIfAbsent(witness.constructionGroup(), ignored -> new ArrayList<>())
                        .add(String.join(",", ids(topology.nodes())));
            }
        }

        assertThat(constructionOrders.get("construction-order")).containsOnly("a,b,c,d");
    }

    @Test
    void equalToStringDistinctNodesAreRetainedWithExplicitOrderAndCollapsingOrderIsRejected()
            throws Exception {
        Witness collision = Faof2WitnessCorpus.load().witnesses().stream()
                .filter(witness -> witness.id().equals("equal-to-string-distinct-ids"))
                .findFirst().orElseThrow();
        Fixture fixture = Faof2WitnessCorpus.fixture(collision);

        Faof2GraphBackend.Topology retained = backend().topologicalOrder(fixture, SEMANTIC_ID_ORDER);
        assertThat(retained.cycle()).as(backend().name()).isFalse();
        assertThat(ids(retained.nodes())).as(backend().name() + " retained semantic identities")
                .containsExactly("root", "node-001", "node-002");
        assertThat(retained.nodes()).extracting(SemanticNode::toString)
                .containsOnly("same-rendering");

        assertThatThrownBy(() -> backend().topologicalOrder(
                        fixture, Comparator.comparing(SemanticNode::toString)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("distinguish all distinct semantic graph nodes");
    }

    @Test
    void negativeBoundedDepthIsRejectedByBothMechanics() throws Exception {
        Witness bounded = Faof2WitnessCorpus.load().witnesses().stream()
                .filter(witness -> witness.id().equals("bounded-depth"))
                .findFirst().orElseThrow();
        Fixture fixture = Faof2WitnessCorpus.fixture(bounded);

        assertThatThrownBy(() -> backend().descendantsBounded(
                        fixture, fixture.node(bounded.bounded().start()), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    void nonTransitiveExplicitOrderIsRejectedByBothMechanics() throws Exception {
        Witness collision = Faof2WitnessCorpus.load().witnesses().stream()
                .filter(witness -> witness.id().equals("equal-to-string-distinct-ids"))
                .findFirst().orElseThrow();
        Fixture fixture = Faof2WitnessCorpus.fixture(collision);
        Comparator<SemanticNode> cyclic = (left, right) -> {
            if (left.equals(right)) {
                return 0;
            }
            boolean precedes = left.id().equals("root") && right.id().equals("node-001")
                    || left.id().equals("node-001") && right.id().equals("node-002")
                    || left.id().equals("node-002") && right.id().equals("root");
            return precedes ? -1 : 1;
        };

        assertThatThrownBy(() -> backend().topologicalOrder(fixture, cyclic))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transitive");
    }

    private static void assertTopology(
            Witness witness,
            Faof2GraphBackend.Topology topology) {
        if (witness.expected().kind().equals("CYCLE")) {
            assertThat(topology.cycle()).as(witness.id()).isTrue();
            assertThat(topology.nodes()).as(witness.id()).isEmpty();
            return;
        }
        assertThat(topology.cycle()).as(witness.id()).isFalse();
        assertThat(ids(topology.nodes())).as(witness.id() + " order")
                .containsExactlyElementsOf(witness.expected().order());
    }

    private static List<String> ids(Iterable<SemanticNode> nodes) {
        List<String> ids = new ArrayList<>();
        nodes.forEach(node -> ids.add(node.id()));
        return ids;
    }
}
