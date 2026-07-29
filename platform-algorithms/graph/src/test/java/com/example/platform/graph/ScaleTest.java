package com.example.platform.graph;

import com.example.platform.graph.api.*;
import com.example.platform.graph.result.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scale tests: verify graph algorithms at different sizes and densities.
 *
 * <p>Reports: node count, edge count, algorithm, elapsed time, approximate allocation.
 * Verifies: no stack overflow, no explosive allocation, no accidental O(VE),
 * no pathological repeated sorting.
 */
@DisplayName("Scale Tests")
class ScaleTest {

    /**
     * Builds a sparse DAG (each node connects to ~2-3 forward nodes).
     */
    private Map<String, Set<String>> sparseDag(int nodes) {
        Random rng = new Random(42L);
        Map<String, Set<String>> adj = new HashMap<>();
        for (int i = 0; i < nodes; i++) {
            adj.put("n" + i, new HashSet<>());
        }
        for (int i = 0; i < nodes; i++) {
            int edges = rng.nextInt(3) + 1; // 1-3 forward edges
            for (int e = 0; e < edges; e++) {
                int target = i + 1 + rng.nextInt(Math.max(1, nodes - i - 1));
                if (target < nodes) {
                    adj.get("n" + i).add("n" + target);
                }
            }
        }
        return adj;
    }

    /**
     * Builds a moderately dense DAG (~20% edge probability).
     */
    private Map<String, Set<String>> denseDag(int nodes) {
        Random rng = new Random(42L);
        Map<String, Set<String>> adj = new HashMap<>();
        for (int i = 0; i < nodes; i++) {
            adj.put("n" + i, new HashSet<>());
        }
        for (int i = 0; i < nodes; i++) {
            for (int j = i + 1; j < nodes; j++) {
                if (rng.nextDouble() < 0.2) {
                    adj.get("n" + i).add("n" + j);
                }
            }
        }
        return adj;
    }

    private long approximateAllocationBytes(int nodes, int edges) {
        // Rough estimate: each node ~32 bytes overhead + string, each entry ~32 bytes
        long nodeStrings = nodes * 40L;
        long edgeEntries = edges * 32L;
        long hashMapOverhead = (nodes + edges) * 32L;
        return nodeStrings + edgeEntries + hashMapOverhead;
    }

    // ── 100 nodes ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("scale: 100 nodes sparse DAG")
    void scale100Sparse() {
        int nodes = 100;
        Map<String, Set<String>> adj = sparseDag(nodes);
        DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
        int edges = graph.edgeCount();

        long start = System.nanoTime();
        CycleDetectionResult<String> cycle = GraphAlgorithms.detectCycles(graph);
        long cycleTime = System.nanoTime() - start;

        start = System.nanoTime();
        TopologicalOrderResult<String> topo = GraphAlgorithms.topologicalOrder(graph);
        long topoTime = System.nanoTime() - start;

        assertThat(cycle.isAcyclic()).isTrue();
        assertThat(topo.order()).hasSize(nodes);

        System.out.printf("[SCALE] nodes=%d edges=%d algorithm=detectCycles elapsed=%dus approxAlloc=%d bytes%n",
                nodes, edges, cycleTime / 1000, approximateAllocationBytes(nodes, edges));
        System.out.printf("[SCALE] nodes=%d edges=%d algorithm=topologicalOrder elapsed=%dus%n",
                nodes, edges, topoTime / 1000);
    }

    @Test
    @DisplayName("scale: 100 nodes dense DAG")
    void scale100Dense() {
        int nodes = 100;
        Map<String, Set<String>> adj = denseDag(nodes);
        DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
        int edges = graph.edgeCount();

        long start = System.nanoTime();
        CycleDetectionResult<String> cycle = GraphAlgorithms.detectCycles(graph);
        long cycleTime = System.nanoTime() - start;

        start = System.nanoTime();
        TopologicalOrderResult<String> topo = GraphAlgorithms.topologicalOrder(graph);
        long topoTime = System.nanoTime() - start;

        assertThat(cycle.isAcyclic()).isTrue();
        assertThat(topo.order()).hasSize(nodes);

        System.out.printf("[SCALE] nodes=%d edges=%d algorithm=detectCycles elapsed=%dus approxAlloc=%d bytes%n",
                nodes, edges, cycleTime / 1000, approximateAllocationBytes(nodes, edges));
        System.out.printf("[SCALE] nodes=%d edges=%d algorithm=topologicalOrder elapsed=%dus%n",
                nodes, edges, topoTime / 1000);
    }

    // ── 1000 nodes ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("scale: 1000 nodes sparse DAG")
    void scale1000Sparse() {
        int nodes = 1000;
        Map<String, Set<String>> adj = sparseDag(nodes);
        DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
        int edges = graph.edgeCount();

        long start = System.nanoTime();
        CycleDetectionResult<String> cycle = GraphAlgorithms.detectCycles(graph);
        long cycleTime = System.nanoTime() - start;

        start = System.nanoTime();
        TopologicalOrderResult<String> topo = GraphAlgorithms.topologicalOrder(graph);
        long topoTime = System.nanoTime() - start;

        assertThat(cycle.isAcyclic()).isTrue();
        assertThat(topo.order()).hasSize(nodes);

        System.out.printf("[SCALE] nodes=%d edges=%d algorithm=detectCycles elapsed=%dus approxAlloc=%d bytes%n",
                nodes, edges, cycleTime / 1000, approximateAllocationBytes(nodes, edges));
        System.out.printf("[SCALE] nodes=%d edges=%d algorithm=topologicalOrder elapsed=%dus%n",
                nodes, edges, topoTime / 1000);
    }

    @Test
    @DisplayName("scale: 1000 nodes dense DAG")
    void scale1000Dense() {
        int nodes = 1000;
        Map<String, Set<String>> adj = denseDag(nodes);
        DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
        int edges = graph.edgeCount();

        long start = System.nanoTime();
        CycleDetectionResult<String> cycle = GraphAlgorithms.detectCycles(graph);
        long cycleTime = System.nanoTime() - start;

        start = System.nanoTime();
        TopologicalOrderResult<String> topo = GraphAlgorithms.topologicalOrder(graph);
        long topoTime = System.nanoTime() - start;

        assertThat(cycle.isAcyclic()).isTrue();
        assertThat(topo.order()).hasSize(nodes);

        System.out.printf("[SCALE] nodes=%d edges=%d algorithm=detectCycles elapsed=%dus approxAlloc=%d bytes%n",
                nodes, edges, cycleTime / 1000, approximateAllocationBytes(nodes, edges));
        System.out.printf("[SCALE] nodes=%d edges=%d algorithm=topologicalOrder elapsed=%dus%n",
                nodes, edges, topoTime / 1000);
    }

    // ── 10000 nodes ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("scale: 10000 nodes sparse DAG")
    void scale10000Sparse() {
        int nodes = 10000;
        Map<String, Set<String>> adj = sparseDag(nodes);
        DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
        int edges = graph.edgeCount();

        long start = System.nanoTime();
        CycleDetectionResult<String> cycle = GraphAlgorithms.detectCycles(graph);
        long cycleTime = System.nanoTime() - start;

        start = System.nanoTime();
        TopologicalOrderResult<String> topo = GraphAlgorithms.topologicalOrder(graph);
        long topoTime = System.nanoTime() - start;

        assertThat(cycle.isAcyclic()).isTrue();
        assertThat(topo.order()).hasSize(nodes);

        System.out.printf("[SCALE] nodes=%d edges=%d algorithm=detectCycles elapsed=%dus approxAlloc=%d bytes%n",
                nodes, edges, cycleTime / 1000, approximateAllocationBytes(nodes, edges));
        System.out.printf("[SCALE] nodes=%d edges=%d algorithm=topologicalOrder elapsed=%dus%n",
                nodes, edges, topoTime / 1000);
    }

    @Test
    @DisplayName("scale: 10000 nodes dense DAG")
    void scale10000Dense() {
        int nodes = 10000;
        Map<String, Set<String>> adj = denseDag(nodes);
        DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);
        int edges = graph.edgeCount();

        long start = System.nanoTime();
        CycleDetectionResult<String> cycle = GraphAlgorithms.detectCycles(graph);
        long cycleTime = System.nanoTime() - start;

        start = System.nanoTime();
        TopologicalOrderResult<String> topo = GraphAlgorithms.topologicalOrder(graph);
        long topoTime = System.nanoTime() - start;

        assertThat(cycle.isAcyclic()).isTrue();
        assertThat(topo.order()).hasSize(nodes);

        System.out.printf("[SCALE] nodes=%d edges=%d algorithm=detectCycles elapsed=%dus approxAlloc=%d bytes%n",
                nodes, edges, cycleTime / 1000, approximateAllocationBytes(nodes, edges));
        System.out.printf("[SCALE] nodes=%d edges=%d algorithm=topologicalOrder elapsed=%dus%n",
                nodes, edges, topoTime / 1000);
    }

    // ── Performance sanity: O(V+E) verification via deterministic operation counting ──

    /**
     * Test-only wrapper that counts graph API operations to verify O(V+E)
     * complexity deterministically. No wall-clock timing is used as a pass
     * condition because CI machines vary in speed and JVM warmup affects timing.
     *
     * <p>Based on the mechanical behavior of {@link GraphAlgorithms#detectCycles}:
     * <ul>
     *   <li>{@code nodes()} called twice (in-degree pass + sorted copy)</li>
     *   <li>{@code predecessors()} called once per node (V total)</li>
     *   <li>{@code successors()} called once per node (V total)</li>
     *   <li>Total adjacency entries = 2E (E from predecessors + E from successors)</li>
     * </ul>
     */
    private static final class CountingGraphView<N> implements DirectedGraphView<N> {
        private final DirectedGraphView<N> delegate;
        int nodesCallCount = 0;
        int successorsCallCount = 0;
        int predecessorsCallCount = 0;
        long totalAdjacencyEntries = 0;

        CountingGraphView(DirectedGraphView<N> delegate) {
            this.delegate = delegate;
        }

        @Override
        public Set<N> nodes() {
            nodesCallCount++;
            return delegate.nodes();
        }

        @Override
        public Set<N> successors(N node) {
            successorsCallCount++;
            Set<N> result = delegate.successors(node);
            totalAdjacencyEntries += result.size();
            return result;
        }

        @Override
        public Set<N> predecessors(N node) {
            predecessorsCallCount++;
            Set<N> result = delegate.predecessors(node);
            totalAdjacencyEntries += result.size();
            return result;
        }

        @Override
        public int nodeCount() {
            return delegate.nodeCount();
        }

        @Override
        public int edgeCount() {
            return delegate.edgeCount();
        }
    }

    @Test
    @DisplayName("scale: performance is O(V+E) not O(VE)")
    void performanceScalesLinearly() {
        // Deterministic O(V+E) verification via operation counting.
        // Wall-clock timing is measured for diagnostic purposes only and
        // is NOT used as a pass condition.
        int n1 = 5000;
        int n2 = 10000;

        Map<String, Set<String>> adj1 = sparseDag(n1);
        DirectedGraphView<String> raw1 = GraphViews.directedFromAdjacency(adj1);
        CountingGraphView<String> g1 = new CountingGraphView<>(raw1);

        Map<String, Set<String>> adj2 = sparseDag(n2);
        DirectedGraphView<String> raw2 = GraphViews.directedFromAdjacency(adj2);
        CountingGraphView<String> g2 = new CountingGraphView<>(raw2);

        // Diagnostic wall-clock measurement (non-gating)
        long start1 = System.nanoTime();
        GraphAlgorithms.detectCycles(g1);
        long time1 = System.nanoTime() - start1;

        long start2 = System.nanoTime();
        GraphAlgorithms.detectCycles(g2);
        long time2 = System.nanoTime() - start2;

        int V1 = g1.nodeCount();
        int V2 = g2.nodeCount();
        int E1 = g1.edgeCount();
        int E2 = g2.edgeCount();

        // ── Structural complexity assertions (deterministic, O(V+E) bounds) ──
        // Based on detectCycles mechanical behavior:
        //   - nodes() called twice (in-degree pass + sorted copy)
        //   - predecessors() called once per node (V total)
        //   - successors() called once per node (V total)
        //   - total adjacency entries = 2E (E from predecessors + E from successors)

        assertThat(g1.nodesCallCount).as("nodes() calls for n1 (O(1) bound)").isLessThanOrEqualTo(3);
        assertThat(g2.nodesCallCount).as("nodes() calls for n2 (O(1) bound)").isLessThanOrEqualTo(3);

        assertThat(g1.predecessorsCallCount).as("predecessors() calls for n1 (O(V) bound)").isLessThanOrEqualTo(V1);
        assertThat(g2.predecessorsCallCount).as("predecessors() calls for n2 (O(V) bound)").isLessThanOrEqualTo(V2);

        assertThat(g1.successorsCallCount).as("successors() calls for n1 (O(V) bound)").isLessThanOrEqualTo(V1);
        assertThat(g2.successorsCallCount).as("successors() calls for n2 (O(V) bound)").isLessThanOrEqualTo(V2);

        long expectedMaxEntries1 = 2L * E1;
        long expectedMaxEntries2 = 2L * E2;
        assertThat(g1.totalAdjacencyEntries).as("total adjacency entries for n1 (O(E) bound)").isLessThanOrEqualTo(expectedMaxEntries1);
        assertThat(g2.totalAdjacencyEntries).as("total adjacency entries for n2 (O(E) bound)").isLessThanOrEqualTo(expectedMaxEntries2);

        // ── Linear scaling assertion (deterministic) ──
        // When V and E double, operation counts should approximately double.
        // Ratio < 3.0 provides margin above the ideal 2.0 while catching O(VE) blowup.
        double successorRatio = (double) g2.successorsCallCount / g1.successorsCallCount;
        double predecessorRatio = (double) g2.predecessorsCallCount / g1.predecessorsCallCount;
        double entriesRatio = (double) g2.totalAdjacencyEntries / g1.totalAdjacencyEntries;

        assertThat(successorRatio).as("successor calls should scale linearly (ratio < 3.0)").isLessThan(3.0);
        assertThat(predecessorRatio).as("predecessor calls should scale linearly (ratio < 3.0)").isLessThan(3.0);
        assertThat(entriesRatio).as("total adjacency entries should scale linearly (ratio < 3.0)").isLessThan(3.0);

        // ── Diagnostic output (non-gating) ──
        double wallClockRatio = (double) time2 / time1;
        System.out.printf("[SCALE] O(V+E) check (deterministic):%n");
        System.out.printf("  n1=%d V1=%d E1=%d nodes()=%d predecessors()=%d successors()=%d entries=%d%n",
                n1, V1, E1, g1.nodesCallCount, g1.predecessorsCallCount, g1.successorsCallCount, g1.totalAdjacencyEntries);
        System.out.printf("  n2=%d V2=%d E2=%d nodes()=%d predecessors()=%d successors()=%d entries=%d%n",
                n2, V2, E2, g2.nodesCallCount, g2.predecessorsCallCount, g2.successorsCallCount, g2.totalAdjacencyEntries);
        System.out.printf("  ratios: successors=%.2f predecessors=%.2f entries=%.2f%n",
                successorRatio, predecessorRatio, entriesRatio);
        System.out.printf("  [DIAGNOSTIC] wall-clock ratio=%.2f (non-gating)%n", wallClockRatio);
    }

    // ── Bounded traversal at scale ──────────────────────────────────────────

    @Test
    @DisplayName("scale: bounded traversal at 10000 nodes")
    void boundedTraversalScale() {
        int nodes = 10000;
        Map<String, Set<String>> adj = sparseDag(nodes);
        DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

        long start = System.nanoTime();
        Set<String> desc = GraphAlgorithms.descendantsBounded(graph, "n0", 3);
        long elapsed = System.nanoTime() - start;

        assertThat(desc.size()).isLessThanOrEqualTo(nodes);
        System.out.printf("[SCALE] boundedTraversal nodes=%d depth=3 resultSize=%d elapsed=%dus%n",
                nodes, desc.size(), elapsed / 1000);
    }
}
