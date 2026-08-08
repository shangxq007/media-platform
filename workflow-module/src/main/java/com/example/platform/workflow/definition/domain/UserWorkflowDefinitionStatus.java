package com.example.platform.workflow.definition.domain;

/**
 * Definition lifecycle status. PUBLISHED versions are immutable; ARCHIVED is
 * terminal. Transitions are frozen in lifecycle-contract.tsv and enforced by
 * the aggregate.
 */
public enum UserWorkflowDefinitionStatus {
    DRAFT,
    VALIDATED,
    PUBLISHED,
    ARCHIVED
}
