package com.example.platform.execution.planning;

import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Roadmap #21 LogicalExecutionGraphDigest (C16/C17, Blocker H).
 *
 * <p>Deterministic semantic-content digest over ALL logical semantic fields:
 * node identities, typed kind, component path, operation key, artifact refs,
 * capability declarations (contract range + alternatives + required),
 * execution intents, output/materialization requirements, exact sample
 * window, exact dependency variant payloads, pruning evidence and plan
 * fingerprint.
 *
 * <p>Provenance/correlation/createdAt/trace are EXCLUDED. Same frozen semantic
 * input → same digest. Different layer digests are NOT equivalence proof.
 *
 * <p>FAOF-1 law: law:logical-digest-content-complete — every semantic field
 * that changes logical meaning changes the digest.
 */
public record LogicalExecutionGraphDigest(String sha256Hex) {

    public LogicalExecutionGraphDigest {
        Objects.requireNonNull(sha256Hex, "sha256Hex");
    }

    public static LogicalExecutionGraphDigest compute(
            String formatVersion,
            com.example.platform.render.domain.renderplan.RenderExtent requestedExtent,
            List<LogicalExecutionGraph.LogicalExecutionNode> nodes,
            List<LogicalExecutionGraph.LogicalDependencyEdge> edges,
            RenderPlanFingerprint planFingerprint,
            LogicalExecutionGraph.PruningEvidence pruningEvidence) {
        StringBuilder sb = new StringBuilder();
        sb.append("LOGICAL_EXECUTION_GRAPH_V1\n");
        sb.append("formatVersion=").append(formatVersion).append('\n');
        sb.append("planFingerprint=").append(planFingerprint.sha256Hex()).append('\n');
        sb.append("requestedExtent=")
                .append(LogicalExecutionGraphBuilder.canonicalExtent(requestedExtent)).append('\n');
        for (var n : nodes) {
            sb.append("node|").append(n.sourceRenderNodeId().value())
                    .append('|').append(n.sourceRenderNodeKind().toString())
                    .append('|').append(n.componentPath() != null ? n.componentPath().toString() : "null")
                    .append('|').append(n.operationKey()).append('\n');
            for (var a : n.artifactReferences()) {
                sb.append("artifact|").append(Canonical.artifact(a)).append('\n');
            }
            for (var cr : n.capabilityRequirements()) {
                sb.append("cap|").append(Canonical.capability(cr)).append('\n');
            }
            for (var er : n.executionRequirements()) {
                sb.append("intent|").append(Canonical.executionIntent(er)).append('\n');
            }
            for (var o : n.outputRequirements()) {
                sb.append("out|").append(Canonical.outputRequirement(o)).append('\n');
            }
            for (var m : n.materializationRequirements()) {
                sb.append("mat|").append(Canonical.materialization(m)).append('\n');
            }
            sb.append("window|")
                    .append(LogicalExecutionGraphBuilder.canonicalWindow(n.requiredSampleWindow()))
                    .append('\n');
            sb.append("coverage|")
                    .append(LogicalExecutionGraphBuilder.canonicalCoverage(n.executionCoverage()))
                    .append('\n');
        }
        var sortedEdges = edges.stream()
                .sorted(Comparator.comparing(e -> e.edgeId().value()))
                .toList();
        for (var e : sortedEdges) {
            // full payload via deterministic canonical record toString — the
            // variant's semantic fields (not just class name) enter the digest
            sb.append("edge|").append(e.producerLogicalNodeId())
                    .append('|').append(e.consumerLogicalNodeId())
                    .append('|').append(Canonical.dependency(e.dependencyVariant())).append('\n');
        }
        if (pruningEvidence != null && pruningEvidence.pruningApplied()) {
            for (var p : pruningEvidence.eliminatedNodes()) {
                sb.append("pruned|").append(p.sourceRenderNodeId().value())
                        .append('|').append(p.requiredWindow())
                        .append('|').append(p.extentWindow())
                        .append('|').append(p.reason()).append('\n');
            }
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
