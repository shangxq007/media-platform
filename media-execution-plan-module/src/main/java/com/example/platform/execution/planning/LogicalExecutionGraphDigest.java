package com.example.platform.execution.planning;

import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;

/**
 * Roadmap #21 LogicalExecutionGraphDigest (C16/C17).
 *
 * <p>Deterministic semantic-content digest of the logical execution graph.
 * Provenance/correlation/createdAt/trace identity is EXCLUDED. Same frozen
 * semantic input → same digest.
 */
public record LogicalExecutionGraphDigest(String sha256Hex) {

    public LogicalExecutionGraphDigest {
        Objects.requireNonNull(sha256Hex, "sha256Hex");
    }

    public static LogicalExecutionGraphDigest compute(
            List<LogicalExecutionGraph.LogicalExecutionNode> nodes,
            List<LogicalExecutionGraph.LogicalDependencyEdge> edges,
            RenderPlanFingerprint planFingerprint) {
        StringBuilder sb = new StringBuilder();
        sb.append("LOGICAL_EXECUTION_GRAPH_V1\n");
        sb.append("planFingerprint=").append(planFingerprint.sha256Hex()).append('\n');
        for (var n : nodes) {
            sb.append("node|").append(n.sourceRenderNodeId().value())
                    .append('|').append(n.sourceRenderNodeKind())
                    .append('|').append(n.operationKey()).append('\n');
            for (var cr : n.capabilityRequirementRefs()) {
                sb.append("cap|").append(cr.capabilityId().value())
                        .append('|').append(cr.required()).append('\n');
            }
            for (var er : n.executionIntentRefs()) {
                sb.append("intent|").append(er.determinismClass().name())
                        .append('|').append(er.sandboxedIntent()).append('\n');
            }
        }
        // deterministic edge order (sorted by id)
        var sortedEdges = edges.stream()
                .sorted(java.util.Comparator.comparing(LogicalExecutionGraph.LogicalDependencyEdge::edgeId))
                .toList();
        for (var e : sortedEdges) {
            sb.append("edge|").append(e.producerLogicalNodeId())
                    .append('|').append(e.consumerLogicalNodeId())
                    .append('|').append(e.dependencyVariant().getClass().getSimpleName()).append('\n');
        }
        return new LogicalExecutionGraphDigest(sha256(sb.toString()));
    }

    static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
