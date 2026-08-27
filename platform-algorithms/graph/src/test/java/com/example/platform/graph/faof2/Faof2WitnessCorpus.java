package com.example.platform.graph.faof2;

import com.example.platform.graph.api.DirectedGraphView;
import com.example.platform.graph.api.GraphViews;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class Faof2WitnessCorpus {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Faof2WitnessCorpus() {
    }

    static Corpus load() throws IOException {
        Path root = Path.of(System.getProperty("faof2.repositoryRoot"));
        return JSON.readValue(
                root.resolve("formal/witnesses/faof2-graph-witnesses-v1.json").toFile(),
                Corpus.class);
    }

    static Fixture fixture(Witness witness) {
        Map<String, SemanticNode> byId = new LinkedHashMap<>();
        for (Node node : witness.nodes()) {
            SemanticNode prior = byId.put(node.id(), new SemanticNode(node.id(), node.display()));
            if (prior != null) {
                throw new IllegalArgumentException("duplicate semantic node id: " + node.id());
            }
        }
        List<Map.Entry<SemanticNode, SemanticNode>> edges = new ArrayList<>();
        for (List<String> edge : witness.edges()) {
            if (edge.size() != 2) {
                throw new IllegalArgumentException("edge must have exactly two endpoints: " + edge);
            }
            edges.add(Map.entry(required(byId, edge.get(0)), required(byId, edge.get(1))));
        }
        DirectedGraphView<SemanticNode> graph =
                GraphViews.directedFromEdges(Set.copyOf(byId.values()), edges);
        return new Fixture(graph, Map.copyOf(byId));
    }

    private static SemanticNode required(Map<String, SemanticNode> byId, String id) {
        SemanticNode node = byId.get(id);
        if (node == null) {
            throw new IllegalArgumentException("unknown semantic node id: " + id);
        }
        return node;
    }

    record Corpus(int schemaVersion, String corpusId, String identityField,
                  String displayFieldIsNonSemantic, String canonicalOrder,
                  List<Witness> witnesses) {
    }

    record Witness(String id, String constructionGroup, List<Node> nodes,
                   List<List<String>> edges, List<String> sources, Bounded bounded,
                   Expected expected, List<String> laws) {
        Witness {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            edges = edges == null ? List.of() : List.copyOf(edges);
            sources = sources == null ? List.of() : List.copyOf(sources);
            laws = laws == null ? List.of() : List.copyOf(laws);
        }
    }

    record Node(String id, String display) {
    }

    record Bounded(String start, int depth) {
    }

    record Expected(String kind, List<String> order, boolean cycle,
                    List<String> reachable, Integer edgeCount,
                    List<String> roots, List<String> sinks,
                    List<String> descendants) {
        Expected {
            order = order == null ? List.of() : List.copyOf(order);
            reachable = reachable == null ? List.of() : List.copyOf(reachable);
            roots = roots == null ? List.of() : List.copyOf(roots);
            sinks = sinks == null ? List.of() : List.copyOf(sinks);
            descendants = descendants == null ? List.of() : List.copyOf(descendants);
        }
    }

    record SemanticNode(String id, String display) {
        SemanticNode {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(display, "display");
        }

        @Override
        public boolean equals(Object candidate) {
            return candidate instanceof SemanticNode other && id.equals(other.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public String toString() {
            return display;
        }
    }

    record Fixture(DirectedGraphView<SemanticNode> graph, Map<String, SemanticNode> byId) {
        SemanticNode node(String id) {
            return required(byId, id);
        }

        Set<SemanticNode> nodes(List<String> ids) {
            return ids.stream().map(this::node).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }
}
