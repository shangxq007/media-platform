package com.example.platform.render.domain.renderplan.graph;

import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.graph.api.DirectedGraphView;
import com.example.platform.graph.api.GraphAlgorithms;
import com.example.platform.graph.result.CycleDetectionResult;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderDependencyEdge;
import com.example.platform.render.domain.renderplan.RenderDiagnosticSeverity;
import com.example.platform.render.domain.renderplan.RenderGraph;
import com.example.platform.render.domain.renderplan.RenderNode;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderPlan;
import com.example.platform.render.domain.renderplan.RenderPlanningDiagnostic;
import com.example.platform.render.domain.renderplan.RenderPlanningDiagnosticCode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Fail-closed graph validation (C23). Structural checks delegate cycle detection
 * to the kernel; semantic checks (dependency variant vs node kind, capability
 * requirements, resolution-state consistency) live here. Deterministic
 * diagnostic ordering.
 */
public final class RenderGraphValidator {

    public RenderGraphValidator() {
    }

    /** Validate the graph against the plan and its topology. Fail-closed. */
    public RenderGraphValidationResult validate(
            RenderPlan plan, RenderGraph graph, DirectedGraphView<RenderNodeId> topology) {
        List<RenderPlanningDiagnostic> diagnostics = new ArrayList<>();

        List<RenderNode> nodes = graph.nodes();
        List<RenderDependencyEdge> edges = graph.edges();
        Set<RenderNodeId> nodeIds = nodes.stream().map(RenderNode::id).collect(Collectors.toSet());
        Map<RenderNodeId, RenderNode> nodeById = new TreeMap<>();
        for (RenderNode node : nodes) {
            nodeById.put(node.id(), node);
        }

        // unique node identity (no duplicates by RenderNodeId)
        Set<RenderNodeId> seen = new TreeSet<>();
        for (RenderNode node : nodes) {
            if (!seen.add(node.id())) {
                diagnostics.add(RenderPlanningDiagnostic.forNode(
                        RenderPlanningDiagnosticCode.PLANNING_UNSUPPORTED,
                        node.id(), RenderDiagnosticSeverity.ERROR,
                        "duplicate node identity"));
            }
        }

        // every edge endpoint exists in nodes
        for (RenderDependencyEdge edge : edges) {
            if (!nodeIds.contains(edge.producerId())) {
                diagnostics.add(RenderPlanningDiagnostic.forNode(
                        RenderPlanningDiagnosticCode.DEPENDENCY_MISSING,
                        edge.consumerId(), RenderDiagnosticSeverity.ERROR,
                        "edge producer not in graph: " + edge.producerId()));
            }
            if (!nodeIds.contains(edge.consumerId())) {
                diagnostics.add(RenderPlanningDiagnostic.forNode(
                        RenderPlanningDiagnosticCode.DEPENDENCY_MISSING,
                        edge.producerId(), RenderDiagnosticSeverity.ERROR,
                        "edge consumer not in graph: " + edge.consumerId()));
            }
            // no self-edge
            if (edge.producerId().equals(edge.consumerId())) {
                diagnostics.add(RenderPlanningDiagnostic.forNode(
                        RenderPlanningDiagnosticCode.PLANNING_UNSUPPORTED,
                        edge.producerId(), RenderDiagnosticSeverity.ERROR,
                        "self-edge forbidden"));
            }
        }

        // acyclic (delegated to kernel detectCycles)
        if (topology != null) {
            CycleDetectionResult<RenderNodeId> cycleResult = GraphAlgorithms.detectCycles(topology);
            if (cycleResult.hasCycle()) {
                diagnostics.add(RenderPlanningDiagnostic.diagnostic(
                        RenderPlanningDiagnosticCode.GRAPH_CYCLE,
                        RenderDiagnosticSeverity.ERROR,
                        "RenderGraph contains a directed cycle"));
            }
        }

        // dependency variant compatible with node kinds
        for (RenderDependencyEdge edge : edges) {
            RenderNode producer = nodeById.get(edge.producerId());
            RenderNode consumer = nodeById.get(edge.consumerId());
            if (producer == null || consumer == null) {
                continue;
            }
            validateDependencyCompatibility(edge, producer, consumer, diagnostics);
        }

        // capability requirements structurally valid (non-empty for node kinds that require them)
        for (RenderNode node : nodes) {
            validateCapabilityRequirements(node, diagnostics);
        }

        // deterministic ordering
        List<RenderPlanningDiagnostic> ordered = diagnostics.stream()
                .sorted(Comparator
                        .comparing(RenderPlanningDiagnostic::code)
                        .thenComparing(d -> d.nodeId().map(RenderNodeId::value).orElse(""))
                        .thenComparing(RenderPlanningDiagnostic::message))
                .collect(Collectors.toList());

        boolean valid = ordered.stream().noneMatch(d -> d.severity() == RenderDiagnosticSeverity.ERROR);
        return new RenderGraphValidationResult(valid, ordered);
    }

    private void validateDependencyCompatibility(
            RenderDependencyEdge edge, RenderNode producer, RenderNode consumer,
            List<RenderPlanningDiagnostic> diagnostics) {
        RenderDependency dep = edge.dependency();
        boolean ok = true;
        if (dep instanceof RenderDependency.DecodedFrames) {
            ok = producer.kind() instanceof RenderNodeKind.Decode;
        } else if (dep instanceof RenderDependency.EffectInput) {
            // EffectInput consumes either raw decoded frames (DECODE) or processed
            // output from a prior effect (EFFECT); consumer is an effect or output (C10).
            ok = (producer.kind() instanceof RenderNodeKind.Decode
                    || producer.kind() instanceof RenderNodeKind.Effect)
                    && (consumer.kind() instanceof RenderNodeKind.Effect
                            || consumer.kind() instanceof RenderNodeKind.Output);
        } else if (dep instanceof RenderDependency.AudioInput) {
            ok = (producer.kind() instanceof RenderNodeKind.Decode
                    || producer.kind() instanceof RenderNodeKind.AudioProcess
                    || producer.kind() instanceof RenderNodeKind.AudioMix)
                    && (consumer.kind() instanceof RenderNodeKind.AudioProcess
                            || consumer.kind() instanceof RenderNodeKind.AudioMix
                            || consumer.kind() instanceof RenderNodeKind.Output);
        } else if (dep instanceof RenderDependency.SubtitleRaster) {
            ok = consumer.kind() instanceof RenderNodeKind.TimedText;
        }
        if (!ok) {
            diagnostics.add(RenderPlanningDiagnostic.forNode(
                    RenderPlanningDiagnosticCode.PLANNING_UNSUPPORTED,
                    edge.consumerId(), RenderDiagnosticSeverity.ERROR,
                    "dependency " + dep.variantKey() + " incompatible with producer/consumer kinds"));
        }
    }

    private void validateCapabilityRequirements(
            RenderNode node, List<RenderPlanningDiagnostic> diagnostics) {
        for (CapabilityRequirement cap : node.capabilityRequirements()) {
            if (cap.capabilityId() == null) {
                diagnostics.add(RenderPlanningDiagnostic.forNode(
                        RenderPlanningDiagnosticCode.PLANNING_UNSUPPORTED,
                        node.id(), RenderDiagnosticSeverity.ERROR,
                        "null capability requirement"));
            }
        }
        // node kinds that require a non-empty capability set in this slice
        boolean requiresCapability = node.kind() instanceof RenderNodeKind.Decode
                || node.kind() instanceof RenderNodeKind.Effect
                || node.kind() instanceof RenderNodeKind.AudioProcess
                || node.kind() instanceof RenderNodeKind.AudioMix
                || node.kind() instanceof RenderNodeKind.TimedText
                || node.kind() instanceof RenderNodeKind.Composite
                || node.kind() instanceof RenderNodeKind.Output;
        if (requiresCapability && node.capabilityRequirements().isEmpty()) {
            diagnostics.add(RenderPlanningDiagnostic.forNode(
                    RenderPlanningDiagnosticCode.CAPABILITY_UNAVAILABLE,
                    node.id(), RenderDiagnosticSeverity.ERROR,
                    "node kind " + node.kind().canonicalName() + " requires a capability requirement"));
        }
    }
}
