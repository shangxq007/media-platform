package com.example.platform.render.infrastructure.unified;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracer for collecting and linking all subsystem traces into a UnifiedRequestGraph.
 * 
 * <p>This is the single point of trace collection for the entire render pipeline.
 * All subsystems emit their traces through this tracer.
 */
@Service
public class UnifiedExecutionTracer {

    private static final Logger log = LoggerFactory.getLogger(UnifiedExecutionTracer.class);

    private final Map<String, UnifiedRequestGraph> activeGraphs = new ConcurrentHashMap<>();

    /**
     * Create a new trace graph for a request.
     */
    public UnifiedRequestGraph createGraph(String requestId, String tenantId, String workspaceId) {
        UnifiedRequestGraph graph = UnifiedRequestGraph.create(requestId, tenantId, workspaceId);
        activeGraphs.put(requestId, graph);
        log.info("Created UEEG for request {}: {}", requestId, graph.graphId());
        return graph;
    }

    /**
     * Get the active graph for a request.
     */
    public UnifiedRequestGraph getGraph(String requestId) {
        return activeGraphs.get(requestId);
    }

    /**
     * Add an execution state node to the graph.
     */
    public UnifiedRequestGraph traceExecutionState(String requestId, String fromState, String toState,
                                                      String reason, String triggeredBy) {
        UnifiedRequestGraph graph = activeGraphs.get(requestId);
        if (graph == null) {
            log.warn("No active graph for request {}", requestId);
            return null;
        }

        String nodeId = generateNodeId("exec");
        GraphNode node = GraphNode.executionState(nodeId, fromState, toState, reason, triggeredBy);

        // Find parent node (previous execution state or root)
        String parentId = findLastNodeId(graph, UnifiedRequestGraph.NodeType.EXECUTION_STATE_NODE);
        if (parentId == null) {
            parentId = graph.rootNodeId();
        }

        UnifiedRequestGraph updated = parentId != null
                ? graph.addNodeWithParent(node, parentId, GraphEdge.TRIGGERS)
                : graph.addNode(node);

        activeGraphs.put(requestId, updated);
        log.debug("Traced execution state for request {}: {} -> {}", requestId, fromState, toState);
        return updated;
    }

    /**
     * Add a billing decision node to the graph.
     */
    public UnifiedRequestGraph traceBillingDecision(String requestId, String decision,
                                                       String reasonCode, String reasonMessage,
                                                       Double estimatedCost) {
        UnifiedRequestGraph graph = activeGraphs.get(requestId);
        if (graph == null) return null;

        String nodeId = generateNodeId("billing");
        GraphNode node = GraphNode.billingDecision(nodeId, decision, reasonCode, reasonMessage, estimatedCost);

        // Link to execution state
        String parentId = findLastNodeId(graph, UnifiedRequestGraph.NodeType.EXECUTION_STATE_NODE);

        UnifiedRequestGraph updated = parentId != null
                ? graph.addNodeWithParent(node, parentId, GraphEdge.DECIDES)
                : graph.addNode(node);

        activeGraphs.put(requestId, updated);
        return updated;
    }

    /**
     * Add a policy decision node to the graph.
     */
    public UnifiedRequestGraph tracePolicyDecision(String requestId, boolean allowed,
                                                      String denyReason, double discountPercent,
                                                      double multiplier) {
        UnifiedRequestGraph graph = activeGraphs.get(requestId);
        if (graph == null) return null;

        String nodeId = generateNodeId("policy");
        GraphNode node = GraphNode.policyDecision(nodeId, allowed, denyReason, discountPercent, multiplier);

        // Link to billing decision or execution state
        String parentId = findLastNodeId(graph, UnifiedRequestGraph.NodeType.BILLING_DECISION_NODE);
        if (parentId == null) {
            parentId = findLastNodeId(graph, UnifiedRequestGraph.NodeType.EXECUTION_STATE_NODE);
        }

        UnifiedRequestGraph updated = parentId != null
                ? graph.addNodeWithParent(node, parentId, GraphEdge.VALIDATES)
                : graph.addNode(node);

        activeGraphs.put(requestId, updated);
        return updated;
    }

    /**
     * Add a provider decision node to the graph.
     */
    public UnifiedRequestGraph traceProviderDecision(String requestId, String selectedProvider,
                                                        String provider, String reason,
                                                        boolean fallbackTriggered) {
        UnifiedRequestGraph graph = activeGraphs.get(requestId);
        if (graph == null) return null;

        String nodeId = generateNodeId("provider");
        GraphNode node = GraphNode.providerDecision(nodeId, selectedProvider, provider, reason, fallbackTriggered);

        // Link to billing decision
        String parentId = findLastNodeId(graph, UnifiedRequestGraph.NodeType.BILLING_DECISION_NODE);

        UnifiedRequestGraph updated = parentId != null
                ? graph.addNodeWithParent(node, parentId, GraphEdge.CONSUMES)
                : graph.addNode(node);

        activeGraphs.put(requestId, updated);
        return updated;
    }

    /**
     * Add an artifact node to the graph.
     */
    public UnifiedRequestGraph traceArtifact(String requestId, String artifactId,
                                                String artifactType, String uri, String hash) {
        UnifiedRequestGraph graph = activeGraphs.get(requestId);
        if (graph == null) return null;

        String nodeId = generateNodeId("artifact");
        GraphNode node = GraphNode.artifact(nodeId, artifactId, artifactType, uri, hash);

        // Link to provider decision
        String parentId = findLastNodeId(graph, UnifiedRequestGraph.NodeType.PROVIDER_DECISION_NODE);

        UnifiedRequestGraph updated = parentId != null
                ? graph.addNodeWithParent(node, parentId, GraphEdge.PRODUCES)
                : graph.addNode(node);

        activeGraphs.put(requestId, updated);
        return updated;
    }

    /**
     * Set the job ID on the graph.
     */
    public UnifiedRequestGraph setJobId(String requestId, String jobId) {
        UnifiedRequestGraph graph = activeGraphs.get(requestId);
        if (graph == null) return null;

        UnifiedRequestGraph updated = graph.withJobId(jobId);
        activeGraphs.put(requestId, updated);
        return updated;
    }

    /**
     * Complete the graph.
     */
    public UnifiedRequestGraph completeGraph(String requestId) {
        UnifiedRequestGraph graph = activeGraphs.get(requestId);
        if (graph == null) return null;

        UnifiedRequestGraph completed = graph.complete();
        activeGraphs.put(requestId, completed);
        log.info("Completed UEEG for request {}: {}", requestId, completed.getSummary());
        return completed;
    }

    /**
     * Fail the graph.
     */
    public UnifiedRequestGraph failGraph(String requestId, String reason) {
        UnifiedRequestGraph graph = activeGraphs.get(requestId);
        if (graph == null) return null;

        UnifiedRequestGraph failed = graph.fail(reason);
        activeGraphs.put(requestId, failed);
        log.warn("Failed UEEG for request {}: {}", requestId, reason);
        return failed;
    }

    /**
     * Remove a graph from active tracking.
     */
    public UnifiedRequestGraph removeGraph(String requestId) {
        return activeGraphs.remove(requestId);
    }

    /**
     * Get all active graphs.
     */
    public Map<String, UnifiedRequestGraph> getActiveGraphs() {
        return Map.copyOf(activeGraphs);
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private String findLastNodeId(UnifiedRequestGraph graph, UnifiedRequestGraph.NodeType type) {
        return graph.getNodesByType(type).stream()
                .reduce((a, b) -> b) // Get last
                .map(GraphNode::nodeId)
                .orElse(null);
    }

    private String generateNodeId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
