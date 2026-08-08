package com.example.platform.workflow.definition.validation;

import com.example.platform.workflow.definition.domain.UserWorkflowDefinition.UserWorkflowErrorCode;

/**
 * Stable validation codes (G-001..G-012 + configuration codes), each mapped
 * to the frozen WORKFLOW error code for HTTP/API mapping
 * (graph-validation-contract.tsv, error-contract.tsv).
 */
public enum UserWorkflowValidationCode {

    G_001_NODE_COUNT_LIMIT(UserWorkflowErrorCode.Code.GRAPH_TOO_LARGE),
    G_002_EDGE_COUNT_LIMIT(UserWorkflowErrorCode.Code.GRAPH_TOO_LARGE),
    G_003_NODE_IDS_UNIQUE(UserWorkflowErrorCode.Code.DUPLICATE_NODE),
    G_004_EDGE_SOURCE_EXISTS(UserWorkflowErrorCode.Code.MISSING_EDGE_ENDPOINT),
    G_005_EDGE_TARGET_EXISTS(UserWorkflowErrorCode.Code.MISSING_EDGE_ENDPOINT),
    G_006_SELF_EDGE_PROHIBITED(UserWorkflowErrorCode.Code.SELF_EDGE),
    G_007_EXACTLY_ONE_ENTRY(UserWorkflowErrorCode.Code.MULTIPLE_ENTRY_NODES),
    G_008_GRAPH_CONNECTED(UserWorkflowErrorCode.Code.DISCONNECTED_GRAPH),
    G_009_GRAPH_ACYCLIC(UserWorkflowErrorCode.Code.CYCLE_DETECTED),
    G_010_ALL_NODES_REACHABLE(UserWorkflowErrorCode.Code.UNREACHABLE_NODE),
    G_011_DUPLICATE_EDGE_PROHIBITED(UserWorkflowErrorCode.Code.DUPLICATE_EDGE),
    G_012_TERMINAL_NODE_REQUIRED(UserWorkflowErrorCode.Code.NO_TERMINAL_NODE),
    CONFIG_INVALID_NODE_TYPE(UserWorkflowErrorCode.Code.INVALID_NODE_TYPE_CONFIGURATION),
    CONFIG_UNKNOWN_FIELD(UserWorkflowErrorCode.Code.INVALID_NODE_TYPE_CONFIGURATION),
    CONFIG_NULL_FIELD(UserWorkflowErrorCode.Code.INVALID_NODE_TYPE_CONFIGURATION),
    CONFIG_SECRET_LIKE_VALUE(UserWorkflowErrorCode.Code.SECRET_LIKE_VALUE_PROHIBITED),
    CONFIG_TOO_LARGE(UserWorkflowErrorCode.Code.CONFIGURATION_TOO_LARGE),
    CONFIG_INVALID_SCHEMA_VERSION(UserWorkflowErrorCode.Code.INVALID_SCHEMA_VERSION);

    private final UserWorkflowErrorCode errorCode;

    UserWorkflowValidationCode(UserWorkflowErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public UserWorkflowErrorCode errorCode() {
        return errorCode;
    }
}
