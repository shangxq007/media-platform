package com.example.platform.render.domain.renderplan;

import java.util.Objects;

/**
 * Directed render dependency edge. DIRECTION CONVENTION: producer -> consumer
 * (data flows producer->consumer; kernel topological order == execution order,
 * producers first).
 *
 * @param producerId node that produces the dependency payload
 * @param consumerId node that consumes the dependency payload
 * @param dependency typed dependency variant
 */
public record RenderDependencyEdge(
        RenderNodeId producerId,
        RenderNodeId consumerId,
        RenderDependency dependency) {

    public RenderDependencyEdge {
        Objects.requireNonNull(producerId, "producerId");
        Objects.requireNonNull(consumerId, "consumerId");
        Objects.requireNonNull(dependency, "dependency");
    }
}
