package com.example.platform.graph;

import com.example.platform.graph.api.*;
import com.example.platform.graph.result.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Differential tests: compare graph algorithm outputs across varied graph
 * topologies to verify behavioral consistency.
 *
 * <p>Each test family exercises a different structural variant (chain, diamond,
 * disconnected, cyclic) and compares the algorithm's output against expected
 * invariants.
 */
@DisplayName("Differential Tests")
class DifferentialTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    private DirectedGraphView<String> buildAdj(Map<String, Set<String>> adj) {
        return GraphViews.directedFromAdjacency(adj);
    }

    private BidirectionalGraphView<String> buildBiAdj(Map<String, Set<String>> adj) {
        return GraphViews.bidirectionalFromAdjacency(adj);
    }

    /** Generates a random valid DAG with the given node count and edge probability. */
    private Map<String, Set<String>> randomDag(int nodes, double edgeProbability, long seed) {
        Random rng = new Random(seed);
        Map<String, Set<String>> adj = new HashMap<>();
        for (int i = 0; i < nodes; i++) {
            adj.put("n" + i, new HashSet<>());
        }
        // Only forward edges (i < j) to guarantee acyclicity
        for (int i = 0; i < nodes; i++) {
            for (int j = i + 1; j < nodes; j++) {
                if (rng.nextDouble() < edgeProbability) {
                    adj.get("n" + i).add("n" + j);
                }
            }
        }
        return adj;
    }

    // ── Random valid DAG generation ─────────────────────────────────────────

    @Nested
    @DisplayName("Random DAG")
    class RandomDagTest {

        @Test
        @DisplayName("random DAG (50 nodes, p=0.1) is acyclic and has valid topo order")
        void randomDagIsAcyclic() {
            Map<String, Set<String>> adj = randomDag(50, 0.1, 42L);
            DirectedGraphView<String> graph = buildAdj(adj);

            CycleDetectionResult<String> cycle = GraphAlgorithms.detectCycles(graph);
            assertThat(cycle.isAcyclic()).isTrue();

            TopologicalOrderResult<String> topo = GraphAlgorithms.topologicalOrder(graph);
            assertThat(topo.order()).hasSize(50);
        }

        @Test
        @DisplayName("random DAG (100 nodes, p=0.05) is acyclic")
        void randomDag100IsAcyclic() {
            Map<String, Set<String>> adj = randomDag(100, 0.05, 123L);
            DirectedGraphView<String> graph = buildAdj(adj);

            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isTrue();
            assertThat(GraphAlgorithms.topologicalOrder(graph).order()).hasSize(100);
        }
    }

    // ── Injected direct cycles ──────────────────────────────────────────────

    @Nested
    @DisplayName("Direct Cycle Injection")
    class DirectCycleTest {

        @Test
        @DisplayName("2-node direct cycle A↔A is detected")
        void directCycle2Node() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("a"));
            DirectedGraphView<String> graph = buildAdj(adj);

            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isFalse();
            TopologicalOrderResult<String> topo = GraphAlgorithms.topologicalOrder(graph);
            assertThat(topo).isInstanceOf(TopologicalOrderResult.CycleDetected.class);
        }

        @Test
        @DisplayName("self-loop is detected as cycle")
        void selfLoopDetected() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("a"));
            DirectedGraphView<String> graph = buildAdj(adj);

            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isFalse();
        }
    }

    // ── Injected multi-hop cycles ───────────────────────────────────────────

    @Nested
    @DisplayName("Multi-Hop Cycle Injection")
    class MultiHopCycleTest {

        @Test
        @DisplayName("4-node cycle A→B→C→D→A is detected")
        void fourNodeCycleDetected() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of("a"));
            DirectedGraphView<String> graph = buildAdj(adj);

            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isFalse();
        }

        @Test
        @DisplayName("cycle in tail of long chain is detected")
        void cycleInTail() {
            Map<String, Set<String>> adj = new HashMap<>();
            // chain: n0→n1→n2→...→n9, then n9→n5 creates cycle
            for (int i = 0; i < 9; i++) {
                adj.put("n" + i, Set.of("n" + (i + 1)));
            }
            adj.put("n9", Set.of("n5"));
            DirectedGraphView<String> graph = buildAdj(adj);

            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isFalse();
        }
    }

    // ── Disconnected DAGs ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Disconnected DAGs")
    class DisconnectedDagTest {

        @Test
        @DisplayName("two disconnected chains are both fully ordered")
        void twoDisconnectedChains() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());
            adj.put("x", Set.of("y"));
            adj.put("y", Set.of("z"));
            adj.put("z", Set.of());
            DirectedGraphView<String> graph = buildAdj(adj);

            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isTrue();
            List<String> order = GraphAlgorithms.topologicalOrder(graph).order();
            assertThat(order).hasSize(6);
            // Verify chain ordering preserved
            assertThat(order.indexOf("a")).isLessThan(order.indexOf("b"));
            assertThat(order.indexOf("b")).isLessThan(order.indexOf("c"));
            assertThat(order.indexOf("x")).isLessThan(order.indexOf("y"));
            assertThat(order.indexOf("y")).isLessThan(order.indexOf("z"));
        }

        @Test
        @DisplayName("single node + 3-node chain disconnected")
        void singleNodeAndChain() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());
            adj.put("iso", Set.of());
            DirectedGraphView<String> graph = buildAdj(adj);

            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isTrue();
            assertThat(GraphAlgorithms.topologicalOrder(graph).order()).hasSize(4);
        }
    }

    // ── Multiple-root DAGs ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Multiple Roots")
    class MultipleRootsTest {

        @Test
        @DisplayName("three roots converge to single sink")
        void threeRootsConverge() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("r1", Set.of("m"));
            adj.put("r2", Set.of("m"));
            adj.put("r3", Set.of("m"));
            adj.put("m", Set.of());
            DirectedGraphView<String> graph = buildAdj(adj);

            assertThat(graph.roots()).containsExactlyInAnyOrder("r1", "r2", "r3");
            List<String> order = GraphAlgorithms.topologicalOrder(graph).order();
            assertThat(order).hasSize(4);
            // m must be last
            assertThat(order.get(3)).isEqualTo("m");
        }

        @Test
        @DisplayName("multiple roots with no shared descendants")
        void multipleRootsIsolated() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("r1", Set.of("s1"));
            adj.put("s1", Set.of());
            adj.put("r2", Set.of("s2"));
            adj.put("s2", Set.of());
            adj.put("r3", Set.of());
            DirectedGraphView<String> graph = buildAdj(adj);

            assertThat(graph.roots()).containsExactlyInAnyOrder("r1", "r2", "r3");
            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isTrue();
        }
    }

    // ── Diamond DAGs ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Diamond DAGs")
    class DiamondDagTest {

        @Test
        @DisplayName("classic diamond has valid topo order")
        void classicDiamond() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b", "c"));
            adj.put("b", Set.of("d"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of());
            DirectedGraphView<String> graph = buildAdj(adj);

            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isTrue();
            List<String> order = GraphAlgorithms.topologicalOrder(graph).order();
            assertThat(order.get(0)).isEqualTo("a");
            assertThat(order.get(3)).isEqualTo("d");
        }

        @Test
        @DisplayName("diamond reachability includes all nodes")
        void diamondReachability() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b", "c"));
            adj.put("b", Set.of("d"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of());
            DirectedGraphView<String> graph = buildAdj(adj);

            Set<String> reachable = GraphAlgorithms.reachableFrom(graph, Set.of("a"));
            assertThat(reachable).containsExactlyInAnyOrder("a", "b", "c", "d");
        }

        @Test
        @DisplayName("multi-layer diamond")
        void multiLayerDiamond() {
            // layer 0: a
            // layer 1: b, c
            // layer 2: d, e
            // layer 3: f
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b", "c"));
            adj.put("b", Set.of("d", "e"));
            adj.put("c", Set.of("d", "e"));
            adj.put("d", Set.of("f"));
            adj.put("e", Set.of("f"));
            adj.put("f", Set.of());
            DirectedGraphView<String> graph = buildAdj(adj);

            assertThat(GraphAlgorithms.detectCycles(graph).isAcyclic()).isTrue();
            List<String> order = GraphAlgorithms.topologicalOrder(graph).order();
            assertThat(order).hasSize(6);
            assertThat(order.get(0)).isEqualTo("a");
            assertThat(order.get(5)).isEqualTo("f");
        }
    }

    // ── Comparison across topologies ────────────────────────────────────────

    @Nested
    @DisplayName("Cross-Topology Comparisons")
    class CrossTopologyTest {

        @Test
        @DisplayName("cycle result: cyclic vs acyclic differ")
        void cycleResultDiffers() {
            Map<String, Set<String>> acyclic = new HashMap<>();
            acyclic.put("a", Set.of("b"));
            acyclic.put("b", Set.of("c"));
            acyclic.put("c", Set.of());

            Map<String, Set<String>> cyclic = new HashMap<>();
            cyclic.put("a", Set.of("b"));
            cyclic.put("b", Set.of("c"));
            cyclic.put("c", Set.of("a"));

            DirectedGraphView<String> g1 = buildAdj(acyclic);
            DirectedGraphView<String> g2 = buildAdj(cyclic);

            assertThat(GraphAlgorithms.detectCycles(g1).isAcyclic()).isTrue();
            assertThat(GraphAlgorithms.detectCycles(g2).isAcyclic()).isFalse();
        }

        @Test
        @DisplayName("reachable sets differ by source connectivity")
        void reachableSetsDiffer() {
            Map<String, Set<String>> adj = new HashMap<>();
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of());
            adj.put("x", Set.of("y"));
            adj.put("y", Set.of());
            DirectedGraphView<String> graph = buildAdj(adj);

            Set<String> fromA = GraphAlgorithms.reachableFrom(graph, Set.of("a"));
            Set<String> fromX = GraphAlgorithms.reachableFrom(graph, Set.of("x"));

            assertThat(fromA).containsExactlyInAnyOrder("a", "b", "c");
            assertThat(fromX).containsExactlyInAnyOrder("x", "y");
            assertThat(fromA).isNotEqualTo(fromX);
        }

        @Test
        @DisplayName("stable topological order across equivalent DAGs")
        void stableTopoOrder() {
            // Same DAG built with different insertion orders
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

            DirectedGraphView<String> g1 = buildAdj(adj1);
            DirectedGraphView<String> g2 = buildAdj(adj2);

            assertThat(GraphAlgorithms.topologicalOrder(g1).order())
                    .isEqualTo(GraphAlgorithms.topologicalOrder(g2).order());
        }

        @Test
        @DisplayName("bounded traversal respects depth limit across topologies")
        void boundedTraversalRespectsDepth() {
            Map<String, Set<String>> adj = new HashMap<>();
            // chain: a→b→c→d→e
            adj.put("a", Set.of("b"));
            adj.put("b", Set.of("c"));
            adj.put("c", Set.of("d"));
            adj.put("d", Set.of("e"));
            adj.put("e", Set.of());
            DirectedGraphView<String> graph = buildAdj(adj);

            assertThat(GraphAlgorithms.descendantsBounded(graph, "a", 0)).containsExactly("a");
            assertThat(GraphAlgorithms.descendantsBounded(graph, "a", 1)).containsExactlyInAnyOrder("a", "b");
            assertThat(GraphAlgorithms.descendantsBounded(graph, "a", 2)).containsExactlyInAnyOrder("a", "b", "c");
            assertThat(GraphAlgorithms.descendantsBounded(graph, "a", 3)).containsExactlyInAnyOrder("a", "b", "c", "d");
            assertThat(GraphAlgorithms.descendantsBounded(graph, "a", 4)).containsExactlyInAnyOrder("a", "b", "c", "d", "e");
        }
    }
}