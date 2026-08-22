package com.example.platform.execution.planning;

import com.example.platform.render.domain.renderplan.RenderDependencyEdge;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderGraph;
import com.example.platform.render.domain.renderplan.RenderNode;
import com.example.platform.render.domain.renderplan.RenderNodeId;
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
 * Roadmap #21 LogicalExecutionGraph builder (C6-C13) — deterministic pure
 * construction with fail-closed validation (Blocker J) and deterministic
 * extent-based elimination (C12/C13, Blocker E).
 *
 * <p>FAOF-1 proof obligations (language-neutral):
 * <ul>
 *   <li>law:graph-1-to-1 — |logical nodes| == |eligible render nodes|</li>
 *   <li>law:dag-acyclic — no directed cycle in logical edges</li>
 *   <li>law:inputs-closed — every edge endpoint resolves to a node</li>
 *   <li>law:extent-elimination-sound — every eliminated node's window is
 *       disjoint from the requested extent, or all its producers were
 *       eliminated (transitive closure, no contributing work removed)</li>
 *   <li>law:extent-elimination-deterministic — same inputs produce the same
 *       elimination set (pure comparison on exact rational time)</li>
 * </ul>
 */
public final class LogicalExecutionGraphBuilder {

    private LogicalExecutionGraphBuilder() {
    }

    /** Build the logical graph from a validated RenderGraph (1:1, pruned). */
    public static LogicalExecutionGraph build(RenderGraph graph, RenderExtent requestedExtent) {
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

        // ---- deterministic extent-based elimination (C12/C13) ----
        Set<String> eliminated = new LinkedHashSet<>();
        List<LogicalExecutionGraph.PruningEvidence.EliminatedNode> eliminatedRecords = new ArrayList<>();
        boolean extentPresent = requestedExtent != null;
        // producer -> consumers adjacency for transitive elimination
        Map<String, List<String>> consumersByProducer = new HashMap<>();
        if (graph.edges() != null) {
            for (RenderDependencyEdge e : graph.edges()) {
                consumersByProducer.computeIfAbsent(e.producerId().value(), k -> new ArrayList<>())
                        .add(e.consumerId().value());
            }
        }
        if (extentPresent) {
            boolean changed = true;
            while (changed) {
                changed = false;
                for (RenderNode n : graph.nodes()) {
                    String id = n.id().value();
                    if (eliminated.contains(id)) {
                        continue;
                    }
                    RenderSampleWindow w = n.requiredSampleWindow() != null
                            ? n.requiredSampleWindow().orElse(null) : null;
                    boolean disjoint = w != null && windowDisjointFromExtent(w, requestedExtent);
                    if (disjoint) {
                        eliminated.add(id);
                        eliminatedRecords.add(new LogicalExecutionGraph.PruningEvidence.EliminatedNode(
                                "ln-" + id, n.id(), canonicalWindow(w), canonicalExtent(requestedExtent),
                                "DISJOINT_WINDOW"));
                        changed = true;
                        continue;
                    }
                    // transitive: all producers eliminated -> consumer has no
                    // contributing input within extent -> eliminate
                    List<String> producers = producersOf(id, graph);
                    if (!producers.isEmpty()) {
                        boolean allProducersEliminated = true;
                        for (String p : producers) {
                            if (!eliminated.contains(p)) {
                                allProducersEliminated = false;
                                break;
                            }
                        }
                        if (allProducersEliminated) {
                            eliminated.add(id);
                            eliminatedRecords.add(new LogicalExecutionGraph.PruningEvidence.EliminatedNode(
                                    "ln-" + id, n.id(), canonicalWindow(w), canonicalExtent(requestedExtent),
                                    "ALL_PRODUCERS_ELIMINATED"));
                            changed = true;
                        }
                    }
                }
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
                    n.requiredSampleWindow() != null ? n.requiredSampleWindow().orElse(null) : null));
        }

        // ---- edges over surviving nodes, fail-closed on dangling refs ----
        // Distinguish (a) nodes absent from the source RenderGraph (dangling,
        // fail-closed) from (b) nodes eliminated by extent pruning (edge is
        // eliminated together with its endpoint).
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
                    // consumer eliminated by extent pruning -> edge eliminated with it
                    continue;
                }
                if (!surviving.contains(producer)) {
                    throw new ExecutionPlanningException(
                            ExecutionPlanningFailureReason.INVALID_LOGICAL_GRAPH,
                            new ExecutionPlanningException.MissingReferenceContext(
                                    "producerLogicalNode", producer,
                                    "dangling producer after extent elimination"));
                }
                edges.add(new LogicalExecutionGraph.LogicalDependencyEdge(
                        "le-" + e.producerId().value() + "-" + e.consumerId().value(),
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
                LogicalExecutionGraphDigest.compute(nodes, edges, graph.planFingerprint(), evidence));
    }

    private static List<String> producersOf(String nodeId, RenderGraph graph) {
        List<String> out = new ArrayList<>();
        if (graph.edges() != null) {
            for (RenderDependencyEdge e : graph.edges()) {
                if (e.consumerId().value().equals(nodeId)) {
                    out.add(e.producerId().value());
                }
            }
        }
        return out;
    }

    /** Exact rational-time disjointness (law:extent-elimination-sound). */
    static boolean windowDisjointFromExtent(RenderSampleWindow w, RenderExtent extent) {
        if (extent.start() == null || extent.end() == null || w.start() == null || w.end() == null) {
            return false;
        }
        // w.end <= extent.start OR w.start >= extent.end (exact rational comparison)
        boolean before = w.end().isLessThanOrEqualTo(extent.start());
        boolean after = w.start().isGreaterThanOrEqualTo(extent.end());
        return before || after;
    }

    static String canonicalWindow(RenderSampleWindow w) {
        if (w == null) {
            return "null";
        }
        return w.start().ticks() + "/" + w.start().timeScale()
                + "-" + w.end().ticks() + "/" + w.end().timeScale()
                + "@" + (w.frameRate() != null ? w.frameRate().toString() : "null");
    }

    static String canonicalExtent(RenderExtent e) {
        if (e == null) {
            return "null";
        }
        return canonicalTime(e.start()) + "-" + canonicalTime(e.end());
    }

    static String canonicalTime(com.example.platform.shared.time.MediaTime t) {
        if (t == null) {
            return "null";
        }
        return t.ticks() + "/" + t.timeScale();
    }
}
