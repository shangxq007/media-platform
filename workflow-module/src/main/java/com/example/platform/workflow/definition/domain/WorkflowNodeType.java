package com.example.platform.workflow.definition.domain;

/** Declaration vocabulary. Schema 1 retains its eight historical declaration types.
 * Frozen executable controls are compiled by WorkflowPlanCompiler; schema 2 publication
 * is supported through the durable Workflow run application path. */
public enum WorkflowNodeType {
    ACTION,
    EXTENSION_POINT,
    CONDITION,
    APPROVAL,
    DELAY,
    NOTIFICATION,
    WEBHOOK,
    HOOK,
    SEQUENCE, PARALLEL, CHOICE, WAIT, LOOP, FOREACH, SUBWORKFLOW, OPERATION_INVOCATION
}
