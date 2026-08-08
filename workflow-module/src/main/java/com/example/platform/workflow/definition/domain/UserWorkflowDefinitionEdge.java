package com.example.platform.workflow.definition.domain;

/**
 * Typed edge declaration (graph-model-contract.json). conditionRef is a typed
 * condition declaration reference (no scripting); empty string = unconditional.
 */
public record UserWorkflowDefinitionEdge(
        String edgeId,
        String sourceNodeId,
        String targetNodeId,
        String conditionRef,
        int sortOrder) {

    public UserWorkflowDefinitionEdge {
        if (edgeId == null || edgeId.isBlank()) {
            throw new IllegalArgumentException("edge id must not be blank");
        }
        if (sourceNodeId == null || sourceNodeId.isBlank()) {
            throw new IllegalArgumentException("source node id must not be blank");
        }
        if (targetNodeId == null || targetNodeId.isBlank()) {
            throw new IllegalArgumentException("target node id must not be blank");
        }
    }

    public static UserWorkflowDefinitionEdge unconditional(
            String edgeId, String sourceNodeId, String targetNodeId, int sortOrder) {
        return new UserWorkflowDefinitionEdge(edgeId, sourceNodeId, targetNodeId, "", sortOrder);
    }
}
