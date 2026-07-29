package com.example.platform.graph;

import com.example.platform.graph.api.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BidirectionalGraphView")
class BidirectionalGraphViewTest {

    @Nested
    @DisplayName("isAcyclic")
    class IsAcyclicTest {

        @Test
        @DisplayName("chain is acyclic")
        void chainIsAcyclic() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());
            BidirectionalGraphView<String> graph = GraphViews.bidirectionalFromAdjacency(adj);
            assertThat(graph.isAcyclic()).isTrue();
        }

        @Test
        @DisplayName("2-node cycle is cyclic")
        void twoNodeCycleIsCyclic() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("a"));
            BidirectionalGraphView<String> graph = GraphViews.bidirectionalFromAdjacency(adj);
            assertThat(graph.isAcyclic()).isFalse();
        }

        @Test
        @DisplayName("empty graph is acyclic")
        void emptyGraphIsAcyclic() {
            BidirectionalGraphView<String> graph = GraphViews.bidirectionalFromAdjacency(Map.of());
            assertThat(graph.isAcyclic()).isTrue();
        }
    }

    @Nested
    @DisplayName("descendants")
    class DescendantsTest {

        @Test
        @DisplayName("all descendants for chain")
        void allDescendantsForChain() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());
            BidirectionalGraphView<String> graph = GraphViews.bidirectionalFromAdjacency(adj);
            assertThat(graph.descendants("a")).containsExactlyInAnyOrder("b", "c");
            assertThat(graph.descendants("b")).containsExactly("c");
            assertThat(graph.descendants("c")).isEmpty();
        }

        @Test
        @DisplayName("all descendants for diamond")
        void allDescendantsForDiamond() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b", "c"));
            adj.put("b", Set.of("d"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of());
            BidirectionalGraphView<String> graph = GraphViews.bidirectionalFromAdjacency(adj);
            assertThat(graph.descendants("a")).containsExactlyInAnyOrder("b", "c", "d");
            assertThat(graph.descendants("b")).containsExactly("d");
        }
    }

    @Nested
    @DisplayName("ancestors")
    class AncestorsTest {

        @Test
        @DisplayName("all ancestors for chain")
        void allAncestorsForChain() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());
            BidirectionalGraphView<String> graph = GraphViews.bidirectionalFromAdjacency(adj);
            assertThat(graph.ancestors("c")).containsExactlyInAnyOrder("a", "b");
            assertThat(graph.ancestors("b")).containsExactly("a");
            assertThat(graph.ancestors("a")).isEmpty();
        }
    }

    @Nested
    @DisplayName("isReachable")
    class IsReachableTest {

        @Test
        @DisplayName("reachable returns true")
        void reachableReturnsTrue() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());
            BidirectionalGraphView<String> graph = GraphViews.bidirectionalFromAdjacency(adj);
            assertThat(graph.isReachable("a", "c")).isTrue();
            assertThat(graph.isReachable("a", "a")).isTrue();
        }

        @Test
        @DisplayName("unreachable returns false")
        void unreachableReturnsFalse() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of());
            adj.put("c", Set.of());
            BidirectionalGraphView<String> graph = GraphViews.bidirectionalFromAdjacency(adj);
            assertThat(graph.isReachable("b", "a")).isFalse();
            assertThat(graph.isReachable("a", "c")).isFalse();
        }
    }
}