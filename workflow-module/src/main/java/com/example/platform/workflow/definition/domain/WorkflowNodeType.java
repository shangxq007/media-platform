package com.example.platform.workflow.definition.domain;

/**
 * The eight W2 V1 declaration node types. Vocabulary source: the
 * AutomationFlow.NodeType vocabulary (VOCABULARY_SOURCE_ONLY relationship,
 * verified 1:1 by UserWorkflowNodeTypeVocabularyTest / AR-W2-09). These are
 * declaration vocabulary only; runtime invocation is NOT_IMPLEMENTED_IN_W2_V1.
 */
public enum WorkflowNodeType {
    ACTION,
    EXTENSION_POINT,
    CONDITION,
    APPROVAL,
    DELAY,
    NOTIFICATION,
    WEBHOOK,
    HOOK
}
