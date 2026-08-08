package com.example.platform.workflow.definition.domain;

/**
 * Display metadata (name/description) for a workflow definition version.
 */
public record UserWorkflowDisplayMetadata(String name, String description) {

    public UserWorkflowDisplayMetadata {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (name.length() > 255) {
            throw new IllegalArgumentException("name exceeds 255 chars");
        }
        if (description != null && description.length() > 4000) {
            throw new IllegalArgumentException("description exceeds 4000 chars");
        }
    }
}
