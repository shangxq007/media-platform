package com.example.platform.workflow.definition.domain;

/**
 * Monotonic integer definition version (1, 2, 3, ...) following the platform
 * convention (config_item.value_version, ai.prompt_versions.version_no).
 * Deterministic ordering; published versions are permanently addressable by
 * (definitionId, versionNumber).
 */
public record UserWorkflowDefinitionVersion(int versionNumber)
        implements Comparable<UserWorkflowDefinitionVersion> {

    public UserWorkflowDefinitionVersion {
        if (versionNumber < 1) {
            throw new IllegalArgumentException("version number must be >= 1");
        }
    }

    public static UserWorkflowDefinitionVersion of(int versionNumber) {
        return new UserWorkflowDefinitionVersion(versionNumber);
    }

    public UserWorkflowDefinitionVersion next() {
        return new UserWorkflowDefinitionVersion(versionNumber + 1);
    }

    @Override
    public int compareTo(UserWorkflowDefinitionVersion other) {
        return Integer.compare(versionNumber, other.versionNumber);
    }

    @Override
    public String toString() {
        return String.valueOf(versionNumber);
    }
}
