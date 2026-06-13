package com.example.platform.render.infrastructure.unified;

import java.time.Instant;
import java.util.*;

/**
 * Unified Execution & Economy Graph (UEEG) - the canonical model for render job lifecycle.
 * 
 * <p>This graph unifies all subsystem traces into a single immutable structure:
 * <ul>
 *   <li>StateMachine (execution state)</li>
 *   <li>BillingDecisionEngine (economic decision)</li>
 *   <li>PolicyEngine (rules)</li>
 *   <li>ProviderRuntimeEngine (execution routing)</li>
 *   <li>ArtifactGraph (outputs)</li>
 * </ul>
 * 
 * <p>The entire render job can be reconstructed from this ONE graph.
 */
public record UnifiedRequestGraph(
        String graphId,
        String requestId,
        String tenantId,
        String workspaceId,
        String jobId,
        Map<String, GraphNode> nodes,
        List<GraphEdge> edges,
        String rootNodeId,
        GraphStatus status,
        Instant createdAt,
        Instant completedAt,
        Map<String, Object> metadata
) {
    /**
     * Create a new empty graph for a request.
     */
    public static UnifiedRequestGraph create(String requestId, String tenantId, String workspaceId) {
        Instant now = Instant.now();
        String graphId = "ueeg-" + requestId;
        return new UnifiedRequestGraph(
                graphId,
                requestId,
                tenantId,
                workspaceId,
                null,
                new LinkedHashMap<>(),
                new ArrayList<>(),
                null,
                GraphStatus.ACTIVE,
                now,
                null,
                Map.of()
        );
    }

    /**
     * Add a node to the graph.
     */
    public UnifiedRequestGraph addNode(GraphNode node) {
        Map<String, GraphNode> newNodes = new LinkedHashMap<>(nodes);
        newNodes.put(node.nodeId(), node);

        String newRootId = rootNodeId;
        if (newRootId == null && node.type() == NodeType.EXECUTION_STATE_NODE) {
            newRootId = node.nodeId();
        }

        return new UnifiedRequestGraph(
                graphId, requestId, tenantId, workspaceId, jobId,
                newNodes, edges, newRootId, status, createdAt, completedAt, metadata
        );
    }

    /**
     * Add an edge to the graph.
     */
    public UnifiedRequestGraph addEdge(GraphEdge edge) {
        List<GraphEdge> newEdges = new ArrayList<>(edges);
        newEdges.add(edge);
        return new UnifiedRequestGraph(
                graphId, requestId, tenantId, workspaceId, jobId,
                nodes, newEdges, rootNodeId, status, createdAt, completedAt, metadata
        );
    }

    /**
     * Add a node and connect it to a parent.
     */
    public UnifiedRequestGraph addNodeWithParent(GraphNode node, String parentNodeId, String edgeType) {
        return addNode(node).addEdge(GraphEdge.link(parentNodeId, node.nodeId(), edgeType));
    }

    /**
     * Set the job ID.
     */
    public UnifiedRequestGraph withJobId(String jobId) {
        return new UnifiedRequestGraph(
                graphId, requestId, tenantId, workspaceId, jobId,
                nodes, edges, rootNodeId, status, createdAt, completedAt, metadata
        );
    }

    /**
     * Mark the graph as completed.
     */
    public UnifiedRequestGraph complete() {
        return new UnifiedRequestGraph(
                graphId, requestId, tenantId, workspaceId, jobId,
                nodes, edges, rootNodeId, GraphStatus.COMPLETED, createdAt, Instant.now(), metadata
        );
    }

    /**
     * Mark the graph as failed.
     */
    public UnifiedRequestGraph fail(String reason) {
        Map<String, Object> newMeta = new HashMap<>(metadata);
        newMeta.put("failureReason", reason);
        return new UnifiedRequestGraph(
                graphId, requestId, tenantId, workspaceId, jobId,
                nodes, edges, rootNodeId, GraphStatus.FAILED, createdAt, Instant.now(), Map.copyOf(newMeta)
        );
    }

    /**
     * Get a node by ID.
     */
    public Optional<GraphNode> getNode(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    /**
     * Get all nodes of a specific type.
     */
    public List<GraphNode> getNodesByType(NodeType type) {
        return nodes.values().stream()
                .filter(n -> n.type() == type)
                .toList();
    }

    /**
     * Get the execution state node.
     */
    public Optional<GraphNode> getExecutionStateNode() {
        return getNodesByType(NodeType.EXECUTION_STATE_NODE).stream().findFirst();
    }

    /**
     * Get the billing decision node.
     */
    public Optional<GraphNode> getBillingDecisionNode() {
        return getNodesByType(NodeType.BILLING_DECISION_NODE).stream().findFirst();
    }

    /**
     * Get the policy decision node.
     */
    public Optional<GraphNode> getPolicyDecisionNode() {
        return getNodesByType(NodeType.POLICY_DECISION_NODE).stream().findFirst();
    }

    /**
     * Get the provider decision node.
     */
    public Optional<GraphNode> getProviderDecisionNode() {
        return getNodesByType(NodeType.PROVIDER_DECISION_NODE).stream().findFirst();
    }

    /**
     * Get all artifact nodes.
     */
    public List<GraphNode> getArtifactNodes() {
        return getNodesByType(NodeType.ARTIFACT_NODE);
    }

    /**
     * Get children of a node.
     */
    public List<GraphNode> getChildren(String nodeId) {
        return edges.stream()
                .filter(e -> e.sourceNodeId().equals(nodeId))
                .map(e -> nodes.get(e.targetNodeId()))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Get parents of a node.
     */
    public List<GraphNode> getParents(String nodeId) {
        return edges.stream()
                .filter(e -> e.targetNodeId().equals(nodeId))
                .map(e -> nodes.get(e.sourceNodeId()))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Get the full execution path from root to latest node.
     */
    public List<GraphNode> getExecutionPath() {
        List<GraphNode> path = new ArrayList<>();
        String currentId = rootNodeId;

        while (currentId != null) {
            GraphNode node = nodes.get(currentId);
            if (node == null) break;
            path.add(node);

            // Find next node in path
            String finalCurrentId = currentId;
            currentId = edges.stream()
                    .filter(e -> e.sourceNodeId().equals(finalCurrentId))
                    .findFirst()
                    .map(GraphEdge::targetNodeId)
                    .orElse(null);
        }

        return path;
    }

    /**
     * Get the number of nodes in the graph.
     */
    public int size() {
        return nodes.size();
    }

    /**
     * Check if the graph is empty.
     */
    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /**
     * Convert to a flat list for storage.
     */
    public List<GraphNode> toNodeList() {
        return List.copyOf(nodes.values());
    }

    /**
     * Convert edges to a flat list for storage.
     */
    public List<GraphEdge> toEdgeList() {
        return List.copyOf(edges);
    }

    /**
     * Get a summary of the graph.
     */
    public String getSummary() {
        return String.format("UEEG[%s] nodes=%d edges=%d status=%s",
                graphId, nodes.size(), edges.size(), status);
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public enum GraphStatus {
        ACTIVE,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public enum NodeType {
        EXECUTION_STATE_NODE,
        BILLING_DECISION_NODE,
        POLICY_DECISION_NODE,
        PROVIDER_DECISION_NODE,
        ARTIFACT_NODE
    }
}
