package com.example.platform.execution.planning;

import com.example.platform.render.domain.renderplan.RenderComponentPath;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderDependencyEdge;
import com.example.platform.render.domain.renderplan.RenderNode;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import java.util.List;
import java.util.Objects;

/**
 * Roadmap #21 LogicalExecutionGraph (C6-C11) — deterministic typed DAG.
 *
 * <p>RENDER_NODE_TO_LOGICAL_NODE=1_TO_1. Each logical node retains its exact
 * typed #20 source semantics (typed RenderNodeKind, typed RenderComponentPath,
 * operationKey, typed requirement declarations, exact sample window). Logical
 * dependencies preserve the exact RenderDependencyEdge / RenderDependency
 * semantic variants with full payload — NO generic DATA/CONTROL/VALIDATION
 * authority, NO invented barrier, NO float-time.
 *
 * <p>FAOF-1 hooks: laws are documented on the builder and digest types.
 */
public record LogicalExecutionGraph(
        String formatVersion,
        RenderPlanFingerprint planFingerprint,
        List<LogicalExecutionNode> nodes,
        List<LogicalDependencyEdge> edges,
        PruningEvidence pruningEvidence,
        LogicalExecutionGraphDigest digest) {

    public LogicalExecutionGraph {
        Objects.requireNonNull(formatVersion, "formatVersion");
        Objects.requireNonNull(planFingerprint, "planFingerprint");
        Objects.requireNonNull(nodes, "nodes");
        Objects.requireNonNull(edges, "edges");
        Objects.requireNonNull(digest, "digest");
        nodes = PlanningCanonicalOrder.logicalNodes(nodes);
        edges = PlanningCanonicalOrder.logicalEdges(edges);
        // pruningEvidence may be null when no pruning was performed (no extent
        // in request or nothing was out of range); when non-null it proves the
        // elimination.
    }

    /**
     * 1:1 logical node — exact typed source semantics preserved (Blocker A).
     */
    public record LogicalExecutionNode(
            String logicalNodeId,
            RenderNodeId sourceRenderNodeId,
            RenderNodeKind sourceRenderNodeKind,
            RenderComponentPath componentPath,
            String operationKey,
            List<com.example.platform.render.domain.renderplan.RenderArtifactReference> artifactReferences,
            List<com.example.platform.extension.domain.CapabilityRequirement> capabilityRequirements,
            List<com.example.platform.render.domain.renderplan.RenderExecutionRequirement> executionRequirements,
            List<com.example.platform.render.domain.renderplan.RenderOutputRequirement> outputRequirements,
            List<com.example.platform.render.domain.renderplan.RenderMaterializationRequirement> materializationRequirements,
            RenderSampleWindow requiredSampleWindow,
            com.example.platform.render.domain.renderplan.RenderExecutionCoverage executionCoverage) {

        public LogicalExecutionNode {
            Objects.requireNonNull(logicalNodeId, "logicalNodeId");
            Objects.requireNonNull(sourceRenderNodeId, "sourceRenderNodeId");
            Objects.requireNonNull(sourceRenderNodeKind, "sourceRenderNodeKind — typed preservation required");
            artifactReferences = PlanningCanonicalOrder.artifacts(artifactReferences);
            capabilityRequirements = PlanningCanonicalOrder.capabilities(capabilityRequirements);
            executionRequirements = PlanningCanonicalOrder.executionRequirements(executionRequirements);
            outputRequirements = PlanningCanonicalOrder.outputRequirements(outputRequirements);
            materializationRequirements = PlanningCanonicalOrder.materializations(materializationRequirements);
            // executionCoverage nullable — timeline-coordinate contribution
            // (C12/C13 correction); DISTINCT from requiredSampleWindow
            // (source-coordinate sampling). Never compared to each other.
        }
    }

    /**
     * Logical dependency edge — preserves the EXACT RenderDependencyEdge /
     * RenderDependency semantic variant WITH full payload (Blocker C). The
     * variant record is the authoritative semantic carrier.
     */
    public record LogicalDependencyEdge(
            com.example.platform.execution.domain.ExecutionEdgeId edgeId,
            String producerLogicalNodeId,
            String consumerLogicalNodeId,
            RenderNodeId producerRenderNodeId,
            RenderNodeId consumerRenderNodeId,
            RenderDependency dependencyVariant) {

        public LogicalDependencyEdge {
            Objects.requireNonNull(edgeId, "edgeId");
            Objects.requireNonNull(producerLogicalNodeId, "producerLogicalNodeId");
            Objects.requireNonNull(consumerLogicalNodeId, "consumerLogicalNodeId");
            Objects.requireNonNull(dependencyVariant, "dependencyVariant — exact RenderDependency required");
        }
    }

    /**
     * Deterministic extent-pruning evidence (C12/C13 Option A, frozen).
     * Proves that every eliminated node is provably outside the requested
     * RenderExtent: its OWN typed RenderExecutionCoverage (timeline/render
     * coordinate domain) is disjoint from the extent
     * (coverage.end <= extent.start OR coverage.start >= extent.end), so no
     * contributing work is removed. RenderSampleWindow (source-media sampling
     * coordinates) NEVER participates in extent-pruning comparison.
     * ALL_PRODUCERS_ELIMINATED transitive inference is FORBIDDEN — a node is
     * eliminated only by its own typed coverage evidence.
     */
    public record PruningEvidence(
            String requestedExtent,
            List<EliminatedNode> eliminatedNodes,
            boolean pruningApplied) {

        public PruningEvidence {
            eliminatedNodes = PlanningCanonicalOrder.eliminatedNodes(eliminatedNodes);
        }

        public record EliminatedNode(
                String logicalNodeId,
                RenderNodeId sourceRenderNodeId,
                String requiredWindow,
                String extentWindow,
                String reason) {
        }
    }
}
