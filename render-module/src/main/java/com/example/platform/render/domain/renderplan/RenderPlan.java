package com.example.platform.render.domain.renderplan;

import java.util.List;
import java.util.Objects;

/**
 * Immutable, deterministic, derived, provider-neutral RenderPlan (C1/C7).
 * Describes WHAT must be rendered/materialized from ONE immutable TimelineRevision
 * (+ a RenderRequest), BEFORE provider-specific execution. It is NOT a Timeline
 * replacement, NOT a provider command list, NOT a workflow definition, NOT a
 * durable job execution record. It never creates revisions.
 */
public record RenderPlan(
        RenderPlanId id,
        String formatVersion,
        TimelineRevisionReference revision,
        RenderRequest request,
        List<RenderNode> nodes,
        List<RenderDependencyEdge> edges,
        RenderPlanFingerprint fingerprint,
        RenderPlanProvenance provenance) {

    public RenderPlan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(formatVersion, "formatVersion");
        if (formatVersion.isBlank()) {
            throw new IllegalArgumentException("formatVersion must not be blank");
        }
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(fingerprint, "fingerprint");
        Objects.requireNonNull(provenance, "provenance");
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }
}
