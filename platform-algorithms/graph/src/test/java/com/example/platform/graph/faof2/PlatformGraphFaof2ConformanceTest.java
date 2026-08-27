package com.example.platform.graph.faof2;

import com.example.platform.graph.api.GraphAlgorithms;
import com.example.platform.graph.faof2.Faof2WitnessCorpus.Fixture;
import com.example.platform.graph.faof2.Faof2WitnessCorpus.SemanticNode;
import com.example.platform.graph.result.TopologicalOrderResult;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

class PlatformGraphFaof2ConformanceTest extends CustomBackendFaof2ConformanceTest {

    private static final Faof2GraphBackend BACKEND = new Faof2GraphBackend() {
        @Override
        public String name() {
            return "CURRENT_CUSTOM_GRAPH_ALGORITHMS";
        }

        @Override
        public Topology topologicalOrder(Fixture fixture, Comparator<SemanticNode> comparator) {
            TopologicalOrderResult<SemanticNode> result =
                    GraphAlgorithms.topologicalOrder(fixture.graph(), comparator);
            if (result instanceof TopologicalOrderResult.Ordered<SemanticNode> ordered) {
                return Topology.ordered(ordered.order());
            }
            return Topology.cycleDetected();
        }

        @Override
        public boolean hasCycle(Fixture fixture) {
            return GraphAlgorithms.detectCycles(fixture.graph()).hasCycle();
        }

        @Override
        public Set<SemanticNode> reachableFrom(Fixture fixture, Set<SemanticNode> sources) {
            return GraphAlgorithms.reachableFrom(fixture.graph(), sources);
        }

        @Override
        public Set<SemanticNode> descendantsBounded(
                Fixture fixture, SemanticNode start, int depth) {
            return GraphAlgorithms.descendantsBounded(fixture.graph(), start, depth);
        }

        @Override
        public long edgeCount(Fixture fixture) {
            return fixture.graph().edgeCount();
        }

        @Override
        public Set<SemanticNode> roots(Fixture fixture) {
            return fixture.graph().roots();
        }

        @Override
        public Set<SemanticNode> sinks(Fixture fixture) {
            return fixture.graph().sinks();
        }
    };

    @Override
    Faof2GraphBackend backend() {
        return BACKEND;
    }
}
