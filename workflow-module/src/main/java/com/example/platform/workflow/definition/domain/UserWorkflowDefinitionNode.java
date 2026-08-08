package com.example.platform.workflow.definition.domain;

import java.util.List;

/**
 * Typed node declaration (graph-model-contract.json). Declaration vocabulary
 * only — no runtime invocation (AR-W2-10). Configuration is a bounded,
 * versioned canonical JSON document.
 */
public record UserWorkflowDefinitionNode(
        String nodeId,
        WorkflowNodeType nodeType,
        String name,
        String configSchemaRef,
        VersionedJsonDocument configValues,
        List<UserWorkflowParameterDeclaration> inputDeclarations,
        List<UserWorkflowParameterDeclaration> outputDeclarations,
        ErrorPolicy errorPolicy) {

    public UserWorkflowDefinitionNode {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("node id must not be blank");
        }
        if (nodeType == null) {
            throw new IllegalArgumentException("node type must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("node name must not be blank");
        }
        if (configSchemaRef == null || configSchemaRef.isBlank()) {
            throw new IllegalArgumentException("config schema ref must not be blank");
        }
        if (configValues == null) {
            throw new IllegalArgumentException("config values must not be null");
        }
        inputDeclarations = List.copyOf(inputDeclarations == null ? List.of() : inputDeclarations);
        outputDeclarations = List.copyOf(outputDeclarations == null ? List.of() : outputDeclarations);
        errorPolicy = errorPolicy == null ? ErrorPolicy.FAIL : errorPolicy;
    }

    /** Declaration-level failure policy; no runtime retry semantics in V1. */
    public enum ErrorPolicy {
        FAIL,
        SKIP,
        RETRY
    }

    /** Bounded, versioned canonical JSON configuration document. */
    public record VersionedJsonDocument(int schemaVersion, String canonicalJson) {

        public VersionedJsonDocument {
            if (schemaVersion < 1) {
                throw new IllegalArgumentException("schema version must be >= 1");
            }
            if (canonicalJson == null) {
                throw new IllegalArgumentException("canonical json must not be null");
            }
        }
    }
}
