package com.example.platform.artifact.domain;

/**
 * Lifecycle state of an Artifact.
 *
 * <p>Transitions are governed by {@link ArtifactStateMachine}. The DELETED state is
 * terminal. Reaching DELETING/DELETED is a logical operation only — it does NOT trigger
 * physical storage deletion in this task (deletion/GC boundary).
 */
public enum ArtifactState {
    REGISTERING,
    AVAILABLE,
    QUARANTINED,
    DELETING,
    DELETED,
    FAILED
}
