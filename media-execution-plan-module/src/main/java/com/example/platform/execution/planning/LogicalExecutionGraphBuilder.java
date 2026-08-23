package com.example.platform.execution.planning;

import com.example.platform.render.domain.renderplan.RenderDependencyEdge;
import com.example.platform.render.domain.renderplan.RenderExecutionCoverage;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderGraph;
import com.example.platform.render.domain.renderplan.RenderNode;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Roadmap #21 LogicalExecutionGraph builder (C6-C13, C12/C13 architecture
 * correction Option A) — deterministic pure construction with fail-closed
 * validation and extent-based elimination over TYPED EXECUTION COVERAGE.
 *
 * <p>Coordinate-domain law (FROZEN correction): pruning compares
 * RenderExecutionCoverage (timeline/render coordinates) against
 * RenderExtent (timeline/render coordinates). RenderSampleWindow (source
 * coordinates) NEVER participates in pruning comparison. A node without
 * coverage (null) is never pruned. ALL_PRODUCERS_ELIMINATED is FORBIDDEN.
 *
 * <p>FAOF-1 proof obligations:
 * <ul>
 *   <li>law:graph-1-to-1 — |logical nodes| == |eligible render nodes|</li>
 *   <li>law:dag-acyclic — no directed cycle</li>
 *   <li>law:inputs-closed — every edge endpoint resolves</li>
 *   <li>law:extent-elimination-sound — every eliminated node's OWN typed
 *       execution coverage is provably disjoint from the requested extent
 *       (exact rational), so no contributing work is removed</li>
 *   <li>law:extent-elimination-deterministic — same inputs -> same elimination</li>
 * </ul>
 */
final class LogicalExecutionGraphBuilder {

    private LogicalExecutionGraphBuilder() {
    }

    /** Build the logical graph from a validated RenderGraph (1:1, pruned). */
    static LogicalExecutionGraph build(RenderGraph graph, RenderExtent requestedExtent) {
        Objects.requireNonNull(graph, "graph");

        // ---- validation: duplicate source ids ----
        Set<String> sourceIds = new HashSet<>();
        for (RenderNode n : graph.nodes()) {
            if (!sourceIds.add(n.id().value())) {
                throw new ExecutionPlanningException(
                        ExecutionPlanningFailureReason.INVALID_LOGICAL_GRAPH,
                        new ExecutionPlanningException.DuplicateIdentityContext(
                                "sourceRenderNodeId", n.id().value(),
                                "duplicate RenderNodeId in RenderGraph"));
            }
        }

        // ---- extent validation (fail-closed at #21 public boundary) ----
        if (requestedExtent != null) {
            validateExtent(requestedExtent);
        }

                                // ---- deterministic extent-based elimination over typed coverage ----
        Set<String> eliminated = new LinkedHashSet<>();
        List<LogicalExecutionGraph.PruningEvidence.EliminatedNode> eliminatedRecords = new ArrayList<>();
        boolean extentPresent = requestedExtent != null;
        for (RenderNode n : graph.nodes()) {
            String id = n.id().value();
            RenderExecutionCoverage coverage = n.executionCoverage();
            if (!extentPresent || coverage == null) {
                continue; // no extent or no single coverage interval -> never pruned
            }
            if (coverageDisjointFromExtent(coverage, requestedExtent)) {
                eliminated.add(id);
                eliminatedRecords.add(new LogicalExecutionGraph.PruningEvidence.EliminatedNode(
                        "ln-" + id, n.id(),
                        LogicalExecutionGraphBuilder.canonicalCoverage(coverage),
                        LogicalExecutionGraphBuilder.canonicalExtent(requestedExtent),
                        "DISJOINT_COVERAGE"));
            }
        }

        // ---- 1:1 projection over non-eliminated nodes (deterministic order) ----
        var nodes = new ArrayList<LogicalExecutionGraph.LogicalExecutionNode>();
        for (RenderNode n : graph.nodes()) {
            if (eliminated.contains(n.id().value())) {
                continue;
            }
            nodes.add(new LogicalExecutionGraph.LogicalExecutionNode(
                    "ln-" + n.id().value(),
                    n.id(),
                    n.kind(),
                    n.componentPath(),
                    n.operationKey(),
                    n.artifactReferences() == null ? List.of() : n.artifactReferences(),
                    n.capabilityRequirements() == null ? List.of() : n.capabilityRequirements(),
                    n.executionRequirements() == null ? List.of() : n.executionRequirements(),
                    n.outputRequirements() == null ? List.of() : n.outputRequirements(),
                    n.materializationRequirements() == null ? List.of() : n.materializationRequirements(),
                    n.requiredSampleWindow() != null ? n.requiredSampleWindow().orElse(null) : null,
                    n.executionCoverage()));
        }

        // ---- edges over surviving nodes, fail-closed on dangling refs ----
        Set<String> sourceNodeIds = new HashSet<>();
        for (RenderNode n : graph.nodes()) {
            sourceNodeIds.add(n.id().value());
        }
        var edges = new ArrayList<LogicalExecutionGraph.LogicalDependencyEdge>();
        Set<String> surviving = new HashSet<>();
        for (var n : nodes) {
            surviving.add(n.logicalNodeId());
        }
        if (graph.edges() != null) {
            for (RenderDependencyEdge e : graph.edges()) {
                String producer = "ln-" + e.producerId().value();
                String consumer = "ln-" + e.consumerId().value();
                if (!sourceNodeIds.contains(e.consumerId().value())) {
                    throw new ExecutionPlanningException(
                            ExecutionPlanningFailureReason.INVALID_LOGICAL_GRAPH,
                            new ExecutionPlanningException.MissingReferenceContext(
                                    "consumerRenderNode", consumer,
                                    "dangling consumer — no such RenderNode in graph"));
                }
                if (!sourceNodeIds.contains(e.producerId().value())) {
                    throw new ExecutionPlanningException(
                            ExecutionPlanningFailureReason.INVALID_LOGICAL_GRAPH,
                            new ExecutionPlanningException.MissingReferenceContext(
                                    "producerRenderNode", producer,
                                    "dangling producer — no such RenderNode in graph"));
                }
                if (!surviving.contains(consumer)) {
                    continue; // consumer eliminated by coverage pruning -> edge eliminated with it
                }
                if (!surviving.contains(producer)) {
                    // GRAPH-CLOSED EXTENT PRUNING (Correction 3 B1): the
                    // producer carries typed DISJOINT_COVERAGE evidence proving
                    // its contribution is outside the requested extent; an
                    // eliminated producer's edge is not a required input of the
                    // surviving consumer. Removing the edge is semantically
                    // legal — the input was proven irrelevant. This is NOT
                    // ALL_PRODUCERS_ELIMINATED node pruning: the consumer
                    // survives with its remaining (in-extent) inputs.
                    continue;
                }
                edges.add(new LogicalExecutionGraph.LogicalDependencyEdge(
                        LogicalEdgeIdentity.derive(
                                e.producerId(), e.consumerId(), e.dependency()),
                        producer, consumer,
                        e.producerId(), e.consumerId(), e.dependency()));
            }
        }

        LogicalExecutionGraph.PruningEvidence evidence =
                new LogicalExecutionGraph.PruningEvidence(
                        extentPresent ? canonicalExtent(requestedExtent) : null,
                        eliminatedRecords, extentPresent && !eliminatedRecords.isEmpty());

        return new LogicalExecutionGraph(
                graph.formatVersion(),
                graph.planFingerprint(),
                nodes, edges, evidence,
                LogicalExecutionGraphDigest.compute(
                        graph.formatVersion(), requestedExtent, nodes, edges,
                        graph.planFingerprint(), evidence));
    }

    /** law:extent-valid — start < end, frameRate positive. */
    static void validateExtent(RenderExtent extent) {
        if (extent.start() == null || extent.end() == null) {
            throw new ExecutionPlanningException(
                    ExecutionPlanningFailureReason.INCONSISTENT_RENDER_EXTENT,
                    new ExecutionPlanningException.ExtentViolationContext(
                            null, null, canonicalExtent(extent),
                            "RenderExtent start/end must be present"));
        }
        if (!extent.end().isGreaterThan(extent.start())) {
            throw new ExecutionPlanningException(
                    ExecutionPlanningFailureReason.INCONSISTENT_RENDER_EXTENT,
                    new ExecutionPlanningException.ExtentViolationContext(
                            null, null, canonicalExtent(extent),
                            "RenderExtent end must be > start"));
        }
        if (extent.frameRate() != null
                && extent.frameRate().numerator().signum() <= 0) {
            throw new ExecutionPlanningException(
                    ExecutionPlanningFailureReason.INCONSISTENT_RENDER_EXTENT,
                    new ExecutionPlanningException.ExtentViolationContext(
                            null, null, canonicalExtent(extent),
                            "RenderExtent frameRate must be positive"));
        }
    }

    /**
     * Exact rational disjointness in the TIMELINE coordinate domain:
     * coverage.end <= extent.start OR coverage.start >= extent.end.
     * RenderSampleWindow NEVER appears in this comparison.
     */
    static boolean coverageDisjointFromExtent(RenderExecutionCoverage coverage, RenderExtent extent) {
        if (extent.start() == null || extent.end() == null
                || coverage.start() == null || coverage.end() == null) {
            return false;
        }
        boolean before = coverage.end().isLessThanOrEqualTo(extent.start());
        boolean after = coverage.start().isGreaterThanOrEqualTo(extent.end());
        return before || after;
    }

    static String canonicalWindow(RenderSampleWindow w) {
        if (w == null) {
            return "null";
        }
        return canonicalTime(w.start()) + "-" + canonicalTime(w.end())
                + "@" + (w.frameRate() != null ? canonicalFrameRate(w.frameRate()) : "null");
    }

    static String canonicalCoverage(RenderExecutionCoverage c) {
        if (c == null) {
            return "null";
        }
        return canonicalTime(c.start()) + "-" + canonicalTime(c.end())
                + "@" + (c.frameRate() != null ? canonicalFrameRate(c.frameRate()) : "null");
    }

    static String canonicalExtent(RenderExtent e) {
        if (e == null) {
            return "null";
        }
        return canonicalTime(e.start()) + "-" + canonicalTime(e.end())
                + "@" + (e.frameRate() != null ? canonicalFrameRate(e.frameRate()) : "null");
    }

    static String canonicalTime(com.example.platform.shared.time.MediaTime t) {
        if (t == null) {
            return "null";
        }
        return t.ticks() + "/" + t.timeScale();
    }

    static String canonicalFrameRate(com.example.platform.shared.time.FrameRate f) {
        if (f == null) {
            return "null";
        }
        return f.numerator() + "/" + f.denominator();
    }
}
