package com.example.platform.graph;

import com.example.platform.graph.api.*;
import com.example.platform.graph.result.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GraphAlgorithms")
class GraphAlgorithmsTest {

    @Nested
    @DisplayName("Cycle Detection")
    class CycleDetectionTest {

        @Test
        @DisplayName("empty graph is acyclic")
        void emptyGraphIsAcyclic() {
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(Map.of());
            CycleDetectionResult<String> result = GraphAlgorithms.detectCycles(graph);
            assertThat(result.isAcyclic()).isTrue();
        }

        @Test
        @DisplayName("single node is acyclic")
        void singleNodeIsAcyclic() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isTrue();
        }

        @Test
        @DisplayName("chain is acyclic")
        void chainIsAcyclic() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isTrue();
        }

        @Test
        @DisplayName("diamond is acyclic")
        void diamondIsAcyclic() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b", "c"));
            adj.put("b", Set.of("d"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isTrue();
        }

        @Test
        @DisplayName("detects 2-node cycle")
        void detects2NodeCycle() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("a"));
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            CycleDetectionResult<String> result = GraphAlgorithms.detectCycles(graph);
            assertThat(result.isAcyclic()).isFalse();
            assertThat(result).isInstanceOf(CycleDetectionResult.Cyclic.class);
        }

        @Test
        @DisplayName("detects 3-node cycle")
        void detects3NodeCycle() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of("a"));
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            CycleDetectionResult<String> result = GraphAlgorithms.detectCycles(graph);
            assertThat(result.isAcyclic()).isFalse();
        }

        @Test
        @DisplayName("detects cycle in complex DAG")
        void detectsCycleInComplexDAG() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of("b")); // cycle: b->c->d->b
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isFalse();
        }

        @Test
        @DisplayName("disconnected components - one cyclic")
        void disconnectedComponentsOneCyclic() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of()); // acyclic component
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of("e"));
            adj.put("e", Set.of("c")); // cycle: c->d->e->c
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isFalse();
        }
    }

    @Nested
    @DisplayName("Topological Order")
    class TopologicalOrderTest {

        @Test
        @DisplayName("empty graph returns empty order")
        void emptyGraphReturnsEmptyOrder() {
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(Map.of());
            TopologicalOrderResult<String> result = GraphAlgorithms.topologicalOrder(graph);
            assertThat(result.order()).isEmpty();
        }

        @Test
        @DisplayName("chain produces correct order")
        void chainProducesCorrectOrder() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            TopologicalOrderResult<String> result = GraphAlgorithms.topologicalOrder(graph);
            assertThat(result.order()).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("diamond produces valid topological order")
        void diamondProducesValidTopologicalOrder() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b", "c"));
            adj.put("b", Set.of("d"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            TopologicalOrderResult<String> result = GraphAlgorithms.topologicalOrder(graph);
            List<String> order = result.order();
            assertThat(order).hasSize(4);
            assertThat(order.indexOf("a")).isLessThan(order.indexOf("b"));
            assertThat(order.indexOf("a")).isLessThan(order.indexOf("c"));
            assertThat(order.indexOf("b")).isLessThan(order.indexOf("d"));
            assertThat(order.indexOf("c")).isLessThan(order.indexOf("d"));
        }

        @Test
        @DisplayName("deterministic order for diamond")
        void deterministicOrderForDiamond() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b", "c"));
            adj.put("b", Set.of("d"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            List<String> order1 = GraphAlgorithms.topologicalOrder(graph).order();
            List<String> order2 = GraphAlgorithms.topologicalOrder(graph).order();
            assertThat(order1).isEqualTo(order2);
        }

        @Test
        @DisplayName("cycle detected returns cycle result")
        void cycleDetectedReturnsCycleResult() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("a"));
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            TopologicalOrderResult<String> result = GraphAlgorithms.topologicalOrder(graph);
            assertThat(result).isInstanceOf(TopologicalOrderResult.CycleDetected.class);
        }

        @Test
        @DisplayName("disconnected nodes all included")
        void disconnectedNodesAllIncluded() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of());
            adj.put("b", Set.of());
            adj.put("c", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            TopologicalOrderResult<String> result = GraphAlgorithms.topologicalOrder(graph);
            assertThat(result.order()).containsExactlyInAnyOrder("a", "b", "c");
        }
    }

    @Nested
    @DisplayName("Reachability")
    class ReachabilityTest {

        @Test
        @DisplayName("reachable from single source")
        void reachableFromSingleSource() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b", "c"));
            adj.put("b", Set.of("d"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            Set<String> reachable = GraphAlgorithms.reachableFrom(graph, Set.of("a"));
            assertThat(reachable).containsExactlyInAnyOrder("a", "b", "c", "d");
        }

        @Test
        @DisplayName("reachable from multiple sources")
        void reachableFromMultipleSources() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("c"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            Set<String> reachable = GraphAlgorithms.reachableFrom(graph, Set.of("a", "b"));
            assertThat(reachable).containsExactlyInAnyOrder("a", "b", "c", "d");
        }

        @Test
        @DisplayName("unreachable nodes excluded")
        void unreachableNodesExcluded() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of());
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            Set<String> reachable = GraphAlgorithms.reachableFrom(graph, Set.of("a"));
            assertThat(reachable).containsExactlyInAnyOrder("a", "b");
        }
    }

    @Nested
    @DisplayName("Bounded Descendants")
    class BoundedDescendantsTest {

        @Test
        @DisplayName("depth 0 returns only the node")
        void depth0ReturnsOnlyNode() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            Set<String> desc = GraphAlgorithms.descendantsBounded(graph, "a", 0);
            assertThat(desc).containsExactly("a");
        }

        @Test
        @DisplayName("depth 1 returns direct successors")
        void depth1ReturnsDirectSuccessors() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b", "c"));
            adj.put("b", Set.of("d"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            Set<String> desc = GraphAlgorithms.descendantsBounded(graph, "a", 1);
            assertThat(desc).containsExactlyInAnyOrder("a", "b", "c");
        }

        @Test
        @DisplayName("depth 2 includes grandchildren")
        void depth2IncludesGrandchildren() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b", "c"));
            adj.put("b", Set.of("d"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            Set<String> desc = GraphAlgorithms.descendantsBounded(graph, "a", 2);
            assertThat(desc).containsExactlyInAnyOrder("a", "b", "c", "d");
        }

        @Test
        @DisplayName("large depth returns all descendants")
        void largeDepthReturnsAllDescendants() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            Set<String> desc = GraphAlgorithms.descendantsBounded(graph, "a", 100);
            assertThat(desc).containsExactlyInAnyOrder("a", "b", "c", "d");
        }
    }

    @Nested
    @DisplayName("Bounded Ancestors")
    class BoundedAncestorsTest {

        @Test
        @DisplayName("depth 0 returns only the node")
        void depth0ReturnsOnlyNode() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            Set<String> anc = GraphAlgorithms.ancestorsBounded(graph, "c", 0);
            assertThat(anc).containsExactly("c");
        }

        @Test
        @DisplayName("depth 1 returns direct predecessors")
        void depth1ReturnsDirectPredecessors() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("c"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            Set<String> anc = GraphAlgorithms.ancestorsBounded(graph, "d", 1);
            assertThat(anc).containsExactlyInAnyOrder("c", "d");
        }

        @Test
        @DisplayName("depth 2 includes grandparents")
        void depth2IncludesGrandparents() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("c"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            Set<String> anc = GraphAlgorithms.ancestorsBounded(graph, "d", 2);
            assertThat(anc).containsExactlyInAnyOrder("a", "b", "c", "d");
        }
    }
}