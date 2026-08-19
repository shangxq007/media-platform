package com.example.platform.render.domain.renderplan;

import java.util.List;
import java.util.Objects;

/**
 * Validated DAG projection of a RenderPlan's execution-relevant steps and their
 * dependencies (C2/C30). Distinct typed layer from RenderPlan, with its own
 * identity (graph fingerprint vs plan fingerprint) and validation. Thin typed
 * projection over the graph kernel's DirectedGraphView.
 */
public record RenderGraph(
        String formatVersion,
        RenderPlanFingerprint planFingerprint,
        List<RenderNode> nodes,
        List<RenderDependencyEdge> edges,
        RenderGraphFingerprint fingerprint) {

    public RenderGraph {
        Objects.requireNonNull(formatVersion, "formatVersion");
        if (formatVersion.isBlank()) {
            throw new IllegalArgumentException("formatVersion must not be blank");
        }
        Objects.requireNonNull(planFingerprint, "planFingerprint");
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(fingerprint, "fingerprint");
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }
}
