package com.example.platform.graph;

import com.example.platform.graph.api.*;
import com.example.platform.graph.result.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests: verify fundamental invariants that must hold for
 * all valid inputs to the graph algorithms.
 */
@DisplayName("Property Tests")
class PropertyTest {

    // ── Property 1: Every valid DAG has a complete topological order ────────

    @Nested
    @DisplayName("P1: Complete Topological Order")
    class CompleteTopoOrderTest {

        @Test
        @DisplayName("empty graph: order is complete (empty)")
        void emptyGraphComplete() {
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(Map.of());
            TopologicalOrderResult<String> result = GraphAlgorithms.topologicalOrder(graph);
            assertThat(result.order()).isEmpty();
        }

        @Test
        @DisplayName("chain: order includes all nodes exactly once")
        void chainComplete() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            List<String> order = GraphAlgorithms.topologicalOrder(graph).order();
            assertThat(order).containsExactlyInAnyOrder("a", "b", "c");
            assertThat(order).hasSize(3);
        }

        @Test
        @DisplayName("random DAG: order includes all nodes")
        void randomDagComplete() {
            Map<String, Set<String>> adj = randomDag(30, 0.15, 7L);
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
            List<String> order = GraphAlgorithms.topologicalOrder(graph).order();
            assertThat(order).hasSize(30);
            assertThat(new HashSet<>(order)).containsExactlyInAnyOrderElementsOf(graph.nodes());
        }
    }

    // ── Property 2: Topological order respects every edge ───────────────────

    @Nested
    @DisplayName("P2: Order Respects Edges")
    class OrderRespectsEdgesTest {

        @Test
        @DisplayName("every edge u→v satisfies index(u) < index(v)")
        void allEdgesRespected() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b", "c"));
            adj.put("b", Set.of("d"));
            adj.put("c", Set.of("d", "e"));
            adj.put("d", Set.of("e"));
            adj.put("e", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            List<String> order = GraphAlgorithms.topologicalOrder(graph).order();
            for (String node : graph.nodes()) {
                for (String succ : graph.successors(node)) {
                    assertThat(order.indexOf(node))
                            .as("Edge %s→%s violated: index(%s)=%d, index(%s)=%d", node, succ, node, order.indexOf(node), succ, order.indexOf(succ))
                            .isLessThan(order.indexOf(succ));
                }
            }
        }

        @Test
        @DisplayName("diamond: all 4 edges respected in topo order")
        void diamondEdgesRespected() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b", "c"));
            adj.put("b", Set.of("d"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            List<String> order = GraphAlgorithms.topologicalOrder(graph).order();
            assertThat(order.indexOf("a")).isLessThan(order.indexOf("b"));
            assertThat(order.indexOf("a")).isLessThan(order.indexOf("c"));
            assertThat(order.indexOf("b")).isLessThan(order.indexOf("d"));
            assertThat(order.indexOf("c")).isLessThan(order.indexOf("d"));
        }
    }

    // ── Property 3: Each node appears exactly once ──────────────────────────

    @Nested
    @DisplayName("P3: Each Node Appears Exactly Once")
    class EachNodeOnceTest {

        @Test
        @DisplayName("no duplicates in topological order")
        void noDuplicates() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b", "c"));
            adj.put("b", Set.of("d"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            List<String> order = GraphAlgorithms.topologicalOrder(graph).order();
            Set<String> unique = new HashSet<>(order);
            assertThat(unique).hasSize(order.size());
        }

        @Test
        @DisplayName("reachable set has no duplicates")
        void reachableNoDuplicates() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b", "c"));
            adj.put("b", Set.of("d"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            Set<String> reachable = GraphAlgorithms.reachableFrom(graph, Set.of("a"));
            // Set inherently has no duplicates — verify size matches expectation
            assertThat(reachable).containsExactlyInAnyOrder("a", "b", "c", "d");
        }
    }

    // ── Property 4: Adding a back edge to a path creates a cycle ─────────────

    @Nested
    @DisplayName("P4: Back Edge Creates Cycle")
    class BackEdgeCreatesCycleTest {

        @Test
        @DisplayName("adding back edge to chain creates cycle")
        void backEdgeChain() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());
            DirectedGraphView<String> base = GraphViews.directedFromAdjacency(adj);
            assertThat(GraphAlgorithms.detectCycles(base).isAcyclic()).isTrue();

            // Add back edge c→a
            Map<String, Set<String>> withBack = new HashMap<>();
            withBack.put("a", Set.of("b"));
            withBack.put("b", Set.of("c"));
            withBack.put("c", Set.of("a"));
            DirectedGraphView<String> modified = GraphViews.directedFromAdjacency(withBack);
            assertThat(GraphAlgorithms.detectCycles(modified).isAcyclic()).isFalse();
        }

        @Test
        @DisplayName("adding self-loop creates cycle")
        void selfLoopCreatesCycle() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of());
            DirectedGraphView<String> base = GraphViews.directedFromAdjacency(adj);
            assertThat(GraphAlgorithms.detectCycles(base).isAcyclic()).isTrue();

            Map<String, Set<String>> withSelfLoop = new HashMap<>();
            withSelfLoop.put("a", Set.of("a"));
            DirectedGraphView<String> modified = GraphViews.directedFromAdjacency(withSelfLoop);
            assertThat(GraphAlgorithms.detectCycles(modified).isAcyclic()).isFalse();
        }
    }

    // ── Property 5: Insertion order does not change stable ordering ─────────

    @Nested
    @DisplayName("P5: Insertion Order Independence")
    class InsertionOrderTest {

        @Test
        @DisplayName("different insertion orders produce same topo order")
        void sameTopoOrderDifferentInsertion() {
            Map<String, Set<String>> adj1 = new LinkedHashMap<>();
            adj1.put("a", Set.of("b", "c"));
            adj1.put("b", Set.of("d"));
            adj1.put("c", Set.of("d"));
            adj1.put("d", Set.of());

            Map<String, Set<String>> adj2 = new LinkedHashMap<>();
            adj2.put("d", Set.of());
            adj2.put("c", Set.of("d"));
            adj2.put("b", Set.of("d"));
            adj2.put("a", Set.of("b", "c"));

            Map<String, Set<String>> adj3 = new LinkedHashMap<>();
            adj3.put("b", Set.of("d"));
            adj3.put("a", Set.of("b", "c"));
            adj3.put("d", Set.of());
            adj3.put("c", Set.of("d"));

            DirectedGraphView<String> g1 = GraphViews.directedFromAdjacency(adj1);
            DirectedGraphView<String> g2 = GraphViews.directedFromAdjacency(adj2);
            DirectedGraphView<String> g3 = GraphViews.directedFromAdjacency(adj3);

            List<String> o1 = GraphAlgorithms.topologicalOrder(g1).order();
            List<String> o2 = GraphAlgorithms.topologicalOrder(g2).order();
            List<String> o3 = GraphAlgorithms.topologicalOrder(g3).order();

            assertThat(o1).isEqualTo(o2);
            assertThat(o2).isEqualTo(o3);
        }

        @Test
        @DisplayName("reverse insertion order produces same cycle result")
        void reverseInsertionSameCycleResult() {
            Map<String, Set<String>> adj1 = new LinkedHashMap<>();
            adj1.put("a", Set.of("b"));
            adj1.put("b", Set.of("c"));
            adj1.put("c", Set.of("a"));

            Map<String, Set<String>> adj2 = new LinkedHashMap<>();
            adj2.put("c", Set.of("a"));
            adj2.put("b", Set.of("c"));
            adj2.put("a", Set.of("b"));

            DirectedGraphView<String> g1 = GraphViews.directedFromAdjacency(adj1);
            DirectedGraphView<String> g2 = GraphViews.directedFromAdjacency(adj2);

            assertThat(GraphAlgorithms.detectCycles(g1).isAcyclic()).isFalse();
            assertThat(GraphAlgorithms.detectCycles(g2).isAcyclic()).isFalse();
        }
    }

    // ── Property 6: Reachability is reflexive only if API declares it ───────

    @Nested
    @DisplayName("P6: Reachability Reflexivity")
    class ReachabilityReflexivityTest {

        @Test
        @DisplayName("reachableFrom includes the source itself")
        void reachableIncludesSelf() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            Set<String> reachable = GraphAlgorithms.reachableFrom(graph, Set.of("a"));
            assertThat(reachable).contains("a");
        }

        @Test
        @DisplayName("BidirectionalGraphView.isReachable is reflexive")
        void bidirectionalIsReachableReflexive() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of());
            BidirectionalGraphView<String> graph = GraphViews.bidirectionalFromAdjacency(adj);
            assertThat(graph.isReachable("a", "a")).isTrue();
        }

        @Test
        @DisplayName("BidirectionalGraphView.descendants does NOT include self")
        void descendantsExcludesSelf() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());
            BidirectionalGraphView<String> graph = GraphViews.bidirectionalFromAdjacency(adj);
            assertThat(graph.descendants("a")).containsExactlyInAnyOrder("b", "c");
            assertThat(graph.descendants("a")).doesNotContain("a");
        }
    }

    // ── Property 7: Bounded traversal never exceeds maxDepth ─────────────────

    @Nested
    @DisplayName("P7: Bounded Traversal maxDepth")
    class BoundedMaxDepthTest {

        @Test
        @DisplayName("bounded descendants depth limit respected")
        void boundedDescendantsDepth() {
            Map<String, Set<String>> adj = new HashMap<>();
            // chain: a→b→c→d→e
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of("e"));
            adj.put("e", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            for (int d = 0; d <= 5; d++) {
                Set<String> desc = GraphAlgorithms.descendantsBounded(graph, "a", d);
                // At depth d, reachable nodes within d steps
                assertThat(desc).as("depth %d", d).hasSize(Math.min(d + 1, 5));
            }
        }

        @Test
        @DisplayName("bounded ancestors depth limit respected")
        void boundedAncestorsDepth() {
            Map<String, Set<String>> adj = new HashMap<>();
            // chain: a→b→c→d→e
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of("e"));
            adj.put("e", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            for (int d = 0; d <= 5; d++) {
                Set<String> anc = GraphAlgorithms.ancestorsBounded(graph, "e", d);
                assertThat(anc).as("depth %d", d).hasSize(Math.min(d + 1, 5));
            }
        }
    }

    // ── Property 8: Bounded traversal never exceeds maxVisitedNodes ──────────
    // (Our API doesn't have a maxVisitedNodes param yet, but we verify bounded
    //  traversal doesn't visit more than the entire graph)

    @Nested
    @DisplayName("P8: Bounded Traversal Node Count")
    class BoundedMaxNodesTest {

        @Test
        @DisplayName("bounded descendants never visits more nodes than graph has")
        void boundedDescendantsNodeCount() {
            Map<String, Set<String>> adj = randomDag(50, 0.1, 99L);
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            Set<String> desc = GraphAlgorithms.descendantsBounded(graph, "n0", 100);
            assertThat(desc.size()).isLessThanOrEqualTo(graph.nodeCount());
        }

        @Test
        @DisplayName("bounded ancestors never visits more nodes than graph has")
        void boundedAncestorsNodeCount() {
            Map<String, Set<String>> adj = randomDag(50, 0.1, 99L);
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            Set<String> anc = GraphAlgorithms.ancestorsBounded(graph, "n49", 100);
            assertThat(anc.size()).isLessThanOrEqualTo(graph.nodeCount());
        }
    }

    // ── Property 9: Caller collections are not mutated ──────────────────────

    @Nested
    @DisplayName("P9: Caller Collections Not Mutated")
    class CallerCollectionsTest {

        @Test
        @DisplayName("GraphViews.directedFromAdjacency does not mutate input map")
        void inputMapNotMutated() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", new HashSet<>(Set.of("b")));
            adj.put("b", new HashSet<>(Set.of("c")));
            adj.put("c", new HashSet<>());

            Map<String, Set<String>> original = new HashMap<>();
            for (var e : adj.entrySet()) {
                original.put(e.getKey(), new HashSet<>(e.getValue()));
            }

            GraphViews.directedFromAdjacency(adj);

            assertThat(adj).isEqualTo(original);
        }

        @Test
        @DisplayName("GraphViews.directedFromEdges does not mutate input")
        void inputEdgesNotMutated() {
            Set<String> nodes = new HashSet<>(Set.of("a", "b", "c"));
            List<Map.Entry<String, String>> edges = new ArrayList<>(List.of(
                    Map.entry("a", "b"),
                    Map.entry("b", "c")
            ));

            Set<String> nodesCopy = new HashSet<>(nodes);
            List<Map.Entry<String, String>> edgesCopy = new ArrayList<>(edges);

            GraphViews.directedFromEdges(nodes, edges);

            assertThat(nodes).isEqualTo(nodesCopy);
            assertThat(edges).isEqualTo(edgesCopy);
        }

        @Test
        @DisplayName("successors returns unmodifiable set")
        void successorsUnmodifiable() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of());
            DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

            Set<String> succ = graph.successors("a");
            assertThatThrownBy(() -> succ.add("x"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Map<String, Set<String>> randomDag(int nodes, double edgeProbability, long seed) {
        Random rng = new Random(seed);
        Map<String, Set<String>> adj = new HashMap<>();
        for (int i = 0; i < nodes; i++) {
            adj.put("n" + i, new HashSet<>());
        }
        for (int i = 0; i < nodes; i++) {
            for (int j = i + 1; j < nodes; j++) {
                if (rng.nextDouble() < edgeProbability) {
                    adj.get("n" + i).add("n" + j);
                }
            }
        }
        return adj;
    }
}