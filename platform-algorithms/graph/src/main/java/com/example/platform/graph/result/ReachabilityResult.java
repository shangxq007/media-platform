package com.example.platform.graph.result;

import java.util.Set;

/**
 * Result of reachability analysis from a set of source nodes.
 *
 * @param <N> node type
 */
public sealed interface ReachabilityResult<N> {

    /**
     * Returns the set of nodes reachable from the sources.
     */
    Set<N> reachable();

    /**
     * Returns the set of source nodes used for this computation.
     */
    Set<N> sources();

    /**
     * Full reachability: all descendants discovered.
     *
     * @param sources original source set
     * @param reachable transitive closure of successors from sources
     */
    record FullReachability<N>(Set<N> sources, Set<N> reachable) implements ReachabilityResult<N> {
        public FullReachability {
            sources = Set.copyOf(sources);
            reachable = Set.copyOf(reachable);
        }
    }

    /**
     * Factory: full reachability.
     */
    static <N> ReachabilityResult<N> full(Set<N> sources, Set<N> reachable) {
        return new FullReachability<>(sources, reachable);
    }
}