package com.example.platform.render.infrastructure.renderplan;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * RenderPlan IR (Intermediate Representation) - deterministic DAG for render execution.
 * 
 * <p>Each node includes:
 * <ul>
 *   <li>id - unique identifier</li>
 *   <li>type - node type</li>
 *   <li>tool - execution tool (FFmpeg, MLT, Remotion)</li>
 *   <li>params - execution parameters</li>
 *   <li>input/output hash - for caching</li>
 *   <li>cacheable flag - whether output can be cached</li>
 * </ul>
 */
public record RenderPlan(
        String planId,
        String jobId,
        List<RenderNode> nodes,
        List<RenderEdge> edges,
        String rootId,
        Map<String, Object> metadata,
        Instant createdAt
) {
    /**
     * Create a new render plan.
     */
    public static RenderPlan create(String jobId) {
        return new RenderPlan(
                "plan-" + jobId,
                jobId,
                List.of(),
                List.of(),
                null,
                Map.of(),
                Instant.now()
        );
    }

    /**
     * Add a node to the plan.
     */
    public RenderPlan addNode(RenderNode node) {
        List<RenderNode> newNodes = new java.util.ArrayList<>(nodes);
        newNodes.add(node);
        String newRootId = rootId == null ? node.id() : rootId;
        return new RenderPlan(planId, jobId, List.copyOf(newNodes), edges, newRootId, metadata, createdAt);
    }

    /**
     * Add an edge to the plan.
     */
    public RenderPlan addEdge(RenderEdge edge) {
        List<RenderEdge> newEdges = new java.util.ArrayList<>(edges);
        newEdges.add(edge);
        return new RenderPlan(planId, jobId, nodes, List.copyOf(newEdges), rootId, metadata, createdAt);
    }

    /**
     * Get a node by ID.
     */
    public RenderNode getNode(String nodeId) {
        return nodes.stream()
                .filter(n -> n.id().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get children of a node.
     */
    public List<RenderNode> getChildren(String nodeId) {
        return edges.stream()
                .filter(e -> e.sourceId().equals(nodeId))
                .map(e -> getNode(e.targetId()))
                .filter(n -> n != null)
                .toList();
    }

    /**
     * Get parents of a node.
     */
    public List<RenderNode> getParents(String nodeId) {
        return edges.stream()
                .filter(e -> e.targetId().equals(nodeId))
                .map(e -> getNode(e.sourceId()))
                .filter(n -> n != null)
                .toList();
    }

    /**
     * Get all nodes in topological order.
     */
    public List<RenderNode> getTopologicalOrder() {
        List<RenderNode> result = new java.util.ArrayList<>();
        java.util.Set<String> visited = new java.util.HashSet<>();

        for (RenderNode node : nodes) {
            if (!visited.contains(node.id())) {
                topologicalSort(node, visited, result);
            }
        }

        return result;
    }

    private void topologicalSort(RenderNode node, java.util.Set<String> visited, List<RenderNode> result) {
        visited.add(node.id());

        for (RenderNode child : getChildren(node.id())) {
            if (!visited.contains(child.id())) {
                topologicalSort(child, visited, result);
            }
        }

        result.add(0, node);
    }

    /**
     * Get the number of nodes.
     */
    public int size() {
        return nodes.size();
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record RenderNode(
            String id,
            NodeType type,
            ToolType tool,
            Map<String, Object> params,
            String inputHash,
            String outputHash,
            boolean cacheable,
            Instant createdAt
    ) {
        /**
         * Create a clip node.
         */
        public static RenderNode clip(String id, String sourceUri, Map<String, Object> params) {
            return new RenderNode(
                    id,
                    NodeType.CLIP,
                    ToolType.FFMPEG,
                    params,
                    hashInput(sourceUri),
                    null,
                    true,
                    Instant.now()
            );
        }

        /**
         * Create a transition node.
         */
        public static RenderNode transition(String id, String inputHash, Map<String, Object> params) {
            return new RenderNode(
                    id,
                    NodeType.TRANSITION,
                    ToolType.MLT,
                    params,
                    inputHash,
                    null,
                    true,
                    Instant.now()
            );
        }

        /**
         * Create a scene node.
         */
        public static RenderNode scene(String id, String inputHash, Map<String, Object> params) {
            return new RenderNode(
                    id,
                    NodeType.SCENE,
                    ToolType.REMOTION,
                    params,
                    inputHash,
                    null,
                    true,
                    Instant.now()
            );
        }

        /**
         * Create an audio node.
         */
        public static RenderNode audio(String id, String sourceUri, Map<String, Object> params) {
            return new RenderNode(
                    id,
                    NodeType.AUDIO,
                    ToolType.FFMPEG,
                    params,
                    hashInput(sourceUri),
                    null,
                    true,
                    Instant.now()
            );
        }

        /**
         * Create an output node.
         */
        public static RenderNode output(String id, String inputHash, Map<String, Object> params) {
            return new RenderNode(
                    id,
                    NodeType.OUTPUT,
                    ToolType.FFMPEG,
                    params,
                    inputHash,
                    null,
                    false, // Output is not cacheable
                    Instant.now()
            );
        }

        /**
         * Set the output hash (after execution).
         */
        public RenderNode withOutputHash(String hash) {
            return new RenderNode(id, type, tool, params, inputHash, hash, cacheable, createdAt);
        }

        private static String hashInput(String input) {
            return "hash-" + Integer.toHexString(input.hashCode());
        }
    }

    public record RenderEdge(
            String sourceId,
            String targetId,
            EdgeType type
    ) {
        public static RenderEdge data(String sourceId, String targetId) {
            return new RenderEdge(sourceId, targetId, EdgeType.DATA);
        }

        public static RenderEdge control(String sourceId, String targetId) {
            return new RenderEdge(sourceId, targetId, EdgeType.CONTROL);
        }
    }

    public enum NodeType {
        CLIP,
        TRANSITION,
        SCENE,
        AUDIO,
        OUTPUT
    }

    public enum ToolType {
        FFMPEG,
        MLT,
        REMOTION
    }

    public enum EdgeType {
        DATA,
        CONTROL
    }
}
