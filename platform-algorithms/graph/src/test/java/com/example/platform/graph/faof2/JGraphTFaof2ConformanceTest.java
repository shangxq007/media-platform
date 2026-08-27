package com.example.platform.graph.faof2;

import com.example.platform.graph.faof2.Faof2WitnessCorpus.Fixture;
import com.example.platform.graph.faof2.Faof2WitnessCorpus.SemanticNode;
import org.jgrapht.Graph;
import org.jgrapht.alg.cycle.CycleDetector;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.traverse.NotDirectedAcyclicGraphException;
import org.jgrapht.traverse.TopologicalOrderIterator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

class JGraphTFaof2ConformanceTest extends CustomBackendFaof2ConformanceTest {

    private static final Faof2GraphBackend BACKEND = new JGraphTTestBackend();

    @Override
    Faof2GraphBackend backend() {
        return BACKEND;
    }

    private static final class JGraphTTestBackend implements Faof2GraphBackend {
        @Override
        public String name() {
            return "JGRAPHT_1_5_2_TEST_POC";
        }

        @Override
        public Topology topologicalOrder(Fixture fixture, Comparator<SemanticNode> comparator) {
            requireStrictNodeOrder(fixture, comparator);
            try {
                List<SemanticNode> ordered = new ArrayList<>();
                new TopologicalOrderIterator<>(graph(fixture), comparator).forEachRemaining(ordered::add);
                return Topology.ordered(ordered);
            } catch (NotDirectedAcyclicGraphException cycle) {
                return Topology.cycleDetected();
            }
        }

        @Override
        public boolean hasCycle(Fixture fixture) {
            return new CycleDetector<>(graph(fixture)).detectCycles();
        }

        @Override
        public Set<SemanticNode> reachableFrom(Fixture fixture, Set<SemanticNode> sources) {
            Graph<SemanticNode, DefaultEdge> graph = graph(fixture);
            Set<SemanticNode> reached = new LinkedHashSet<>(sources);
            Queue<SemanticNode> pending = new ArrayDeque<>(sources);
            while (!pending.isEmpty()) {
                SemanticNode node = pending.remove();
                for (DefaultEdge edge : graph.outgoingEdgesOf(node)) {
                    SemanticNode successor = graph.getEdgeTarget(edge);
                    if (reached.add(successor)) {
                        pending.add(successor);
                    }
                }
            }
            return Set.copyOf(reached);
        }

        @Override
        public Set<SemanticNode> descendantsBounded(
                Fixture fixture, SemanticNode start, int depth) {
            if (depth < 0) {
                throw new IllegalArgumentException("maxDepth must be non-negative");
            }
            Graph<SemanticNode, DefaultEdge> graph = graph(fixture);
            Map<SemanticNode, Integer> distances = new HashMap<>();
            Queue<SemanticNode> pending = new ArrayDeque<>();
            distances.put(start, 0);
            pending.add(start);
            while (!pending.isEmpty()) {
                SemanticNode node = pending.remove();
                int distance = distances.get(node);
                if (distance == depth) {
                    continue;
                }
                for (DefaultEdge edge : graph.outgoingEdgesOf(node)) {
                    SemanticNode successor = graph.getEdgeTarget(edge);
                    if (!distances.containsKey(successor)) {
                        distances.put(successor, distance + 1);
                        pending.add(successor);
                    }
                }
            }
            return Set.copyOf(distances.keySet());
        }

        @Override
        public long edgeCount(Fixture fixture) {
            return graph(fixture).edgeSet().size();
        }

        @Override
        public Set<SemanticNode> roots(Fixture fixture) {
            Graph<SemanticNode, DefaultEdge> graph = graph(fixture);
            Set<SemanticNode> roots = new LinkedHashSet<>();
            for (SemanticNode node : graph.vertexSet()) {
                if (graph.inDegreeOf(node) == 0) {
                    roots.add(node);
                }
            }
            return Set.copyOf(roots);
        }

        @Override
        public Set<SemanticNode> sinks(Fixture fixture) {
            Graph<SemanticNode, DefaultEdge> graph = graph(fixture);
            Set<SemanticNode> sinks = new LinkedHashSet<>();
            for (SemanticNode node : graph.vertexSet()) {
                if (graph.outDegreeOf(node) == 0) {
                    sinks.add(node);
                }
            }
            return Set.copyOf(sinks);
        }

        private static Graph<SemanticNode, DefaultEdge> graph(Fixture fixture) {
            Graph<SemanticNode, DefaultEdge> graph =
                    new DefaultDirectedGraph<>(DefaultEdge.class);
            fixture.graph().nodes().forEach(graph::addVertex);
            for (SemanticNode source : fixture.graph().nodes()) {
                for (SemanticNode target : fixture.graph().successors(source)) {
                    graph.addEdge(source, target);
                }
            }
            return graph;
        }

        private static void requireStrictNodeOrder(
                Fixture fixture, Comparator<SemanticNode> comparator) {
            List<SemanticNode> nodes = List.copyOf(fixture.graph().nodes());
            for (SemanticNode node : nodes) {
                if (comparator.compare(node, node) != 0) {
                    throw new IllegalArgumentException(
                            "node comparator must compare each node equal to itself");
                }
            }
            for (int left = 0; left < nodes.size(); left++) {
                for (int right = left + 1; right < nodes.size(); right++) {
                    if (comparator.compare(nodes.get(left), nodes.get(right)) == 0) {
                        throw new IllegalArgumentException(
                                "node comparator must distinguish all distinct semantic graph nodes");
                    }
                    int forward = Integer.signum(comparator.compare(
                            nodes.get(left), nodes.get(right)));
                    int reverse = Integer.signum(comparator.compare(
                            nodes.get(right), nodes.get(left)));
                    if (forward != -reverse) {
                        throw new IllegalArgumentException("node comparator must be asymmetric");
                    }
                }
            }
            List<SemanticNode> sorted = new ArrayList<>(nodes);
            try {
                sorted.sort(comparator);
            } catch (IllegalArgumentException invalidComparator) {
                throw new IllegalArgumentException(
                        "node comparator must be transitive", invalidComparator);
            }
            for (int left = 0; left < sorted.size(); left++) {
                for (int right = left + 1; right < sorted.size(); right++) {
                    if (comparator.compare(sorted.get(left), sorted.get(right)) >= 0) {
                        throw new IllegalArgumentException("node comparator must be transitive");
                    }
                }
            }
        }
    }
}
