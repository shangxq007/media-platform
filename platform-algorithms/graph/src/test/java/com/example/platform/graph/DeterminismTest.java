package com.example.platform.graph;

import com.example.platform.graph.api.*;
import com.example.platform.graph.result.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Determinism Tests")
class DeterminismTest {

    @Test
    @DisplayName("topological order is deterministic regardless of insertion order")
    void topologicalOrderDeterministicRegardlessOfInsertionOrder() {
        // Build same graph with different insertion orders
        List<List<Map.Entry<String, String>>> edgeOrders = List.of(
                List.of(
                        Map.entry("a", "b"),
                        Map.entry("b", "c"),
                        Map.entry("a", "c"),
                        Map.entry("c", "d")
                ),
                List.of(
                        Map.entry("c", "d"),
                        Map.entry("a", "c"),
                        Map.entry("b", "c"),
                        Map.entry("a", "b")
                ),
                List.of(
                        Map.entry("b", "c"),
                        Map.entry("a", "c"),
                        Map.entry("c", "d"),
                        Map.entry("a", "b")
                )
        );

        List<String> firstOrder = null;
        for (var edges : edgeOrders) {
            Set<String> nodes = Set.of("a", "b", "c", "d");
            DirectedGraphView<String> graph = GraphViews.directedFromEdges(nodes, edges);
            List<String> order =
                    GraphAlgorithms.topologicalOrder(graph, Comparator.naturalOrder()).order();
            if (firstOrder == null) {
                firstOrder = order;
            } else {
                assertThat(order).as("Insertion order %s produced different result", edges).isEqualTo(firstOrder);
            }
        }
    }

    @Test
    @DisplayName("cycle detection is deterministic")
    void cycleDetectionDeterministic() {
        // Same cyclic graph built different ways
        Map<String, Set<String>> adj1 = new LinkedHashMap<>();
        adj1.put("a", Set.of("b"));
        adj1.put("b", Set.of("c"));
        adj1.put("c", Set.of("a"));

        Map<String, Set<String>> adj2 = new LinkedHashMap<>();
        adj2.put("c", Set.of("a"));
        adj2.put("a", Set.of("b"));
        adj2.put("b", Set.of("c"));

        DirectedGraphView<String> g1 = GraphViews.directedFromAdjacency(adj1);
        DirectedGraphView<String> g2 = GraphViews.directedFromAdjacency(adj2);

        assertThat(GraphAlgorithms.detectCycles(g1).isAcyclic()).isFalse();
        assertThat(GraphAlgorithms.detectCycles(g2).isAcyclic()).isFalse();
    }

    @Test
    @DisplayName("deterministic order for branch topology")
    void deterministicOrderForBranchTopology() {
        // step-1 -> step-2, step-1 -> step-3
        Map<String, Set<String>> adj = new HashMap<>();
        adj.put("step-1", Set.of("step-2", "step-3"));
        adj.put("step-2", Set.of());
        adj.put("step-3", Set.of());
        DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

        TopologicalOrderResult<String> result =
                GraphAlgorithms.topologicalOrder(graph, Comparator.naturalOrder());
        List<String> order = result.order();
        assertThat(order.get(0)).isEqualTo("step-1");
        // step-2 should come before step-3 (sorted order)
        assertThat(order.indexOf("step-2")).isLessThan(order.indexOf("step-3"));
    }

    @Test
    @DisplayName("deterministic order for diamond topology")
    void deterministicOrderForDiamondTopology() {
        // step-1 -> step-2, step-1 -> step-3, step-2 -> step-4, step-3 -> step-4
        Map<String, Set<String>> adj = new HashMap<>();
        adj.put("step-1", Set.of("step-2", "step-3"));
        adj.put("step-2", Set.of("step-4"));
        adj.put("step-3", Set.of("step-4"));
        adj.put("step-4", Set.of());
        DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

        TopologicalOrderResult<String> result =
                GraphAlgorithms.topologicalOrder(graph, Comparator.naturalOrder());
        List<String> order = result.order();
        assertThat(order.get(0)).isEqualTo("step-1");
        assertThat(order.get(3)).isEqualTo("step-4");
        // step-2 before step-3 (deterministic sort)
        assertThat(order.indexOf("step-2")).isLessThan(order.indexOf("step-3"));
    }

    @Test
    @DisplayName("repeated calls produce identical results")
    void repeatedCallsProduceIdenticalResults() {
        Map<String, Set<String>> adj = new HashMap<>();
        adj.put("x", Set.of("y", "z"));
        adj.put("y", Set.of("w"));
        adj.put("z", Set.of("w"));
        adj.put("w", Set.of());
        DirectedGraphView<String> graph = GraphViews.directedFromAdjacency(adj);

        for (int i = 0; i < 100; i++) {
            TopologicalOrderResult<String> r1 =
                    GraphAlgorithms.topologicalOrder(graph, Comparator.naturalOrder());
            assertThat(r1.order()).isEqualTo(
                    GraphAlgorithms.topologicalOrder(graph, Comparator.naturalOrder()).order());
        }
    }
}
