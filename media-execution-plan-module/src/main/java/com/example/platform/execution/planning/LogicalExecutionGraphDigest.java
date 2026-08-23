package com.example.platform.execution.planning;

import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Roadmap #21 LogicalExecutionGraphDigest (C6/C17) — end-to-end injective
 * canonical encoding via {@link CanonicalWriter} (Correction 5 B2, Correction
 * 6 C6-A/C6-B).
 *
 * <p>The COMPLETE semantic stream is framed: every variable-length scalar is
 * UTF-8 byte-length-prefixed; lists carry explicit counts; optionals carry
 * presence markers; #20-owned objects delegate to the #20 canonical codec.
 * Different supported semantic structures ⇒ different canonical bytes.
 *
 * <p>C6-A: the logical EDGE COLLECTION is NON_SEMANTIC order — upstream #20
 * RenderPlanCanonicalCodec.graphFingerprintCanonical sorts its edge encodings
 * (Collections.sort(edgeEncodings)), so RenderGraph edge insertion order
 * carries no semantic meaning. Complete framed canonical per edge → sort →
 * framed list. Duplicate distinct edges are preserved (sorting never
 * discards). ORDER_SEMANTICS=NON_SEMANTIC_CANONICAL_SORT.
 *
 * <p>C6-B: the eliminated-node set is STRUCTURAL — explicit list count +
 * individually framed elements (no delimiter join; ["a","b"] can never
 * collide with ["a\nb"]). ORDER_SEMANTICS=NON_SEMANTIC_CANONICAL_SORT (set
 * membership).
 *
 * <p>ExecutionPlanId / createdAt / correlation / trace / provenance EXCLUDED
 * (identity and provenance, never semantic content).
 */
public record LogicalExecutionGraphDigest(String sha256Hex) {

    public LogicalExecutionGraphDigest {
        Objects.requireNonNull(sha256Hex, "sha256Hex");
    }

    public static LogicalExecutionGraphDigest compute(
            String formatVersion,
            RenderExtent requestedExtent,
            List<LogicalExecutionGraph.LogicalExecutionNode> nodes,
            List<LogicalExecutionGraph.LogicalDependencyEdge> edges,
            RenderPlanFingerprint planFingerprint,
            LogicalExecutionGraph.PruningEvidence pruningEvidence) {
        CanonicalWriter w = new CanonicalWriter();
        w.tag("LOGICAL_EXECUTION_GRAPH_V1");
        w.field("formatVersion", formatVersion);
        w.field("planFingerprint", planFingerprint.sha256Hex());
        w.field("requestedExtent", LogicalExecutionGraphBuilder.canonicalExtent(requestedExtent));
        // C6-B: eliminated-node collection — structural framing
        List<String> eliminated = new ArrayList<>();
        if (pruningEvidence != null && pruningEvidence.eliminatedNodes() != null) {
            for (var en : pruningEvidence.eliminatedNodes()) {
                eliminated.add(en.sourceRenderNodeId().value());
            }
        }
        w.tag("ELIMINATED_NODES");
        w.list(CanonicalWriter.sorted(eliminated));
        // nodes: deterministic order by logical node id (structural partition —
        // node order is NOT semantic)
        List<LogicalExecutionGraph.LogicalExecutionNode> sortedNodes = new ArrayList<>(nodes);
        sortedNodes.sort(Comparator.comparing(LogicalExecutionGraph.LogicalExecutionNode::logicalNodeId));
        List<String> nodeCanonicals = new ArrayList<>();
        for (var n : sortedNodes) {
            CanonicalWriter nw = new CanonicalWriter();
            nw.tag("NODE");
            nw.field("logicalNodeId", n.logicalNodeId());
            nw.field("sourceRenderNodeId", n.sourceRenderNodeId().value());
            nw.field("renderNodeKind", Canonical.renderNodeKind(n.sourceRenderNodeKind()));
            nw.field("componentPath", Canonical.componentPath(n.componentPath()));
            nw.field("operationKey", n.operationKey());
            // C7-B: nested collections follow #20 NON_SEMANTIC order — the #20
            // codec sorts artifact/capability/output/materialization canonical
            // encodings (sortedEncodings); executionRequirements carry no #20
            // positional authority (List container only) -> canonical sort.
            nw.list(CanonicalWriter.sorted(
                    n.artifactReferences().stream().map(Canonical::artifact).toList()));
            nw.list(CanonicalWriter.sorted(
                    n.capabilityRequirements().stream().map(Canonical::capability).toList()));
            nw.list(CanonicalWriter.sorted(
                    n.executionRequirements().stream().map(Canonical::executionIntent).toList()));
            nw.list(CanonicalWriter.sorted(
                    n.outputRequirements().stream().map(Canonical::outputRequirement).toList()));
            nw.list(CanonicalWriter.sorted(
                    n.materializationRequirements().stream().map(Canonical::materialization).toList()));
            nw.optional(n.requiredSampleWindow() != null,
                    LogicalExecutionGraphBuilder.canonicalWindow(n.requiredSampleWindow()));
            nw.optional(n.executionCoverage() != null,
                    LogicalExecutionGraphBuilder.canonicalCoverage(n.executionCoverage()));
            nodeCanonicals.add(nw.build());
        }
        w.list(nodeCanonicals);
        // C6-A: edge collection — NON_SEMANTIC canonical sort
        List<String> edgeCanonicals = new ArrayList<>();
        for (var e : edges) {
            CanonicalWriter ew = new CanonicalWriter();
            ew.tag("EDGE");
            ew.field("edgeId", e.edgeId().value());
            ew.field("producer", e.producerLogicalNodeId());
            ew.field("consumer", e.consumerLogicalNodeId());
            ew.field("dependency", Canonical.dependency(e.dependencyVariant()));
            edgeCanonicals.add(ew.build());
        }
        w.list(CanonicalWriter.sorted(edgeCanonicals));
        return new LogicalExecutionGraphDigest(sha256(w.build()));
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
