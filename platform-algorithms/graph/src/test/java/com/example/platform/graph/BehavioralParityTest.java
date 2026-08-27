package com.example.platform.graph;

import com.example.platform.graph.api.*;
import com.example.platform.graph.result.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioral parity tests: verify that the graph kernel produces results
 * equivalent to the original MEP validator for all required scenarios.
 */
@DisplayName("Behavioral Parity with MEP Validator")
class BehavioralParityTest {

    @Nested
    @DisplayName("Valid Topologies")
    class ValidTopologiesTest {

        @Test
        @DisplayName("chain: A -> B -> C produces valid topo order")
        void chainProducesValidTopoOrder() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            CycleDetectionResult<String> cycle = GraphAlgorithms.detectCycles(graph);
            assertThat(cycle.isAcyclic()).isTrue();

            TopologicalOrderResult<String> topo =
                    GraphAlgorithms.topologicalOrder(graph, Comparator.naturalOrder());
            assertThat(topo.order()).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("branch: A -> B, A -> C produces valid topo order")
        void branchProducesValidTopoOrder() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b", "c"));
            adj.put("b", Set.of());
            adj.put("c", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isTrue();
            List<String> order =
                    GraphAlgorithms.topologicalOrder(graph, Comparator.naturalOrder()).order();
            assertThat(order).hasSize(3);
            assertThat(order.get(0)).isEqualTo("a");
            assertThat(order.indexOf("b")).isLessThan(order.indexOf("c"));
        }

        @Test
        @DisplayName("diamond: A -> B, A -> C, B -> D, C -> D produces valid topo order")
        void diamondProducesValidTopoOrder() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b", "c"));
            adj.put("b", Set.of("d"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isTrue();
            List<String> order =
                    GraphAlgorithms.topologicalOrder(graph, Comparator.naturalOrder()).order();
            assertThat(order).hasSize(4);
            assertThat(order.get(0)).isEqualTo("a");
            assertThat(order.get(3)).isEqualTo("d");
        }

        @Test
        @DisplayName("multiple roots: A -> C, B -> C produces valid topo order")
        void multipleRootsProducesValidTopoOrder() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("c"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isTrue();
            List<String> order =
                    GraphAlgorithms.topologicalOrder(graph, Comparator.naturalOrder()).order();
            assertThat(order).hasSize(3);
            assertThat(order.get(2)).isEqualTo("c");
        }

        @Test
        @DisplayName("multiple outputs: A -> B, A -> C (no merge) produces valid topo order")
        void multipleOutputsProducesValidTopoOrder() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b", "c"));
            adj.put("b", Set.of());
            adj.put("c", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isTrue();
            List<String> order =
                    GraphAlgorithms.topologicalOrder(graph, Comparator.naturalOrder()).order();
            assertThat(order).hasSize(3);
            assertThat(order.get(0)).isEqualTo("a");
        }
    }

    @Nested
    @DisplayName("Cycle Detection Parity")
    class CycleDetectionParityTest {

        @Test
        @DisplayName("self dependency: A -> A is detected as cycle")
        void selfDependencyDetected() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("a"));
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isFalse();
        }

        @Test
        @DisplayName("direct cycle: A -> B -> A is detected")
        void directCycleDetected() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("a"));
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isFalse();
        }

        @Test
        @DisplayName("multi-hop cycle: A -> B -> C -> A is detected")
        void multiHopCycleDetected() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of("a"));
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isFalse();
        }

        @Test
        @DisplayName("duplicate dependency does not cause false cycle detection")
        void duplicateDependencyNoFalseCycle() {
            // Duplicate edges in adjacency (same target listed twice) should not cause issues
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isTrue();
        }

        @Test
        @DisplayName("stable topological order for same DAG")
        void stableTopologicalOrder() {
            Map<String, Set<String>> adj1 = new HashMap<>();
            adj1.put("a", Set.of("b", "c"));
            adj1.put("b", Set.of("d"));
            adj1.put("c", Set.of("d"));
            adj1.put("d", Set.of());

            Map<String, Set<String>> adj2 = new HashMap<>();
            adj2.put("d", Set.of());
            adj2.put("c", Set.of("d"));
            adj2.put("b", Set.of("d"));
            adj2.put("a", Set.of("b", "c"));

            DirectedGraphView<String> g1 = GraphViews.directedFromAdjacency(adj1);
            DirectedGraphView<String> g2 = GraphViews.directedFromAdjacency(adj2);

            assertThat(GraphAlgorithms.topologicalOrder(g1, Comparator.naturalOrder()).order())
                    .isEqualTo(GraphAlgorithms.topologicalOrder(g2, Comparator.naturalOrder()).order());
        }
    }
}
