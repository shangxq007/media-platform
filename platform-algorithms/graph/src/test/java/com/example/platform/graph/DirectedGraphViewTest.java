package com.example.platform.graph;

import com.example.platform.graph.api.*;
import com.example.platform.graph.result.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DirectedGraphView Basic Operations")
class DirectedGraphViewTest {

    @Nested
    @DisplayName("Construction")
    class ConstructionTest {

        @Test
        @DisplayName("creates graph from adjacency map")
        void createsFromAdjacencyMap() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b", "c"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());

            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            assertThat(graph.nodeCount()).isEqualTo(3);
            assertThat(graph.edgeCount()).isEqualTo(3);
            assertThat(graph.nodes()).containsExactlyInAnyOrder("a", "b", "c");
        }

        @Test
        @DisplayName("creates graph from edges")
        void createsFromEdges() {
            Set<String> nodes = Set.of("a", "b", "c", "d");
            List<Map.Entry<String, String>> edges = List.of(
                    Map.entry("a", "b"),
                    Map.entry("a", "c"),
                    Map.entry("b", "d"),
                    Map.entry("c", "d")
            );

            DirectedGraphView<String> graph = GraphViews.directedFromEdges(nodes, edges);

            assertThat(graph.nodeCount()).isEqualTo(4);
            assertThat(graph.edgeCount()).isEqualTo(4);
            assertThat(graph.successors("a")).containsExactlyInAnyOrder("b", "c");
            assertThat(graph.successors("d")).isEmpty();
        }

        @Test
        @DisplayName("isolated nodes are included")
        void isolatedNodesIncluded() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of());
            adj.put("c", Set.of()); // isolated

            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            assertThat(graph.nodeCount()).isEqualTo(3);
            assertThat(graph.nodes()).containsExactlyInAnyOrder("a", "b", "c");
        }
    }

    @Nested
    @DisplayName("Navigation")
    class NavigationTest {

        @Test
        @DisplayName("successors returns outgoing edges")
        void successorsReturnsOutgoingEdges() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b", "c"));
            adj.put("b", Set.of());
            adj.put("c", Set.of());

            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            assertThat(graph.successors("a")).containsExactlyInAnyOrder("b", "c");
            assertThat(graph.successors("b")).isEmpty();
        }

        @Test
        @DisplayName("predecessors returns incoming edges")
        void predecessorsReturnsIncomingEdges() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("c"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());

            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            assertThat(graph.predecessors("c")).containsExactlyInAnyOrder("a", "b");
            assertThat(graph.predecessors("a")).isEmpty();
        }

        @Test
        @DisplayName("roots returns nodes with no predecessors")
        void rootsReturnsNodesWithNoPredecessors() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());

            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            assertThat(graph.roots()).containsExactly("a");
        }

        @Test
        @DisplayName("sinks returns nodes with no successors")
        void sinksReturnsNodesWithNoSuccessors() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());

            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            assertThat(graph.sinks()).containsExactly("c");
        }

        @Test
        @DisplayName("throws on unknown node")
        void throwsOnUnknownNode() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of());

            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            assertThatThrownBy(() -> graph.successors("x"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> graph.predecessors("x"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Empty Graph")
    class EmptyGraphTest {

        @Test
        @DisplayName("empty graph has zero nodes and edges")
        void emptyGraphHasZeroNodesAndEdges() {
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(Map.of());

            assertThat(graph.nodeCount()).isZero();
            assertThat(graph.edgeCount()).isZero();
            assertThat(graph.isEmpty()).isTrue();
            assertThat(graph.nodes()).isEmpty();
        }
    }
}