package com.example.platform.workflow.definition.domain;

import java.util.UUID;

/**
 * Definition identity for a user workflow definition lineage.
 * Stable for the lineage; version identity is (definitionId, versionNumber).
 * Never reused as an execution identity.
 */
public record UserWorkflowDefinitionId(String value) {

    public UserWorkflowDefinitionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("definition id must not be blank");
        }
    }

    public static UserWorkflowDefinitionId generate() {
        return new UserWorkflowDefinitionId(UUID.randomUUID().toString());
    }

    public static UserWorkflowDefinitionId of(String value) {
        return new UserWorkflowDefinitionId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
