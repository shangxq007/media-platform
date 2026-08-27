package com.example.platform.graph.faof2;

import com.example.platform.graph.faof2.Faof2WitnessCorpus.Fixture;
import com.example.platform.graph.faof2.Faof2WitnessCorpus.SemanticNode;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

interface Faof2GraphBackend {

    String name();

    Topology topologicalOrder(Fixture fixture, Comparator<SemanticNode> comparator);

    boolean hasCycle(Fixture fixture);

    Set<SemanticNode> reachableFrom(Fixture fixture, Set<SemanticNode> sources);

    Set<SemanticNode> descendantsBounded(Fixture fixture, SemanticNode start, int depth);

    long edgeCount(Fixture fixture);

    Set<SemanticNode> roots(Fixture fixture);

    Set<SemanticNode> sinks(Fixture fixture);

    record Topology(boolean cycle, List<SemanticNode> nodes) {
        public Topology {
            nodes = List.copyOf(nodes);
        }

        static Topology ordered(List<SemanticNode> nodes) {
            return new Topology(false, nodes);
        }

        static Topology cycleDetected() {
            return new Topology(true, List.of());
        }
    }
}
