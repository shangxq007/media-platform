package com.example.platform.timeline.revisioncommand;

/**
 * REVISION_COMMAND_MODEL_V1 (RC6): typed stable identity of a static Revision
 * Command definition. Namespaced; independent of OperationDefinitionId.
 * V1 set: CREATE_REF, DELETE_REF, RESTORE_REVISION_STATE, MERGE_REVISIONS.
 */
public record RevisionCommandDefinitionId(String value) {

    public RevisionCommandDefinitionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("command definition id required");
        }
    }

    public static final String CREATE_REF = "revision.create-ref";
    public static final String DELETE_REF = "revision.delete-ref";
    public static final String RESTORE_REVISION_STATE = "revision.restore-revision-state";
    public static final String MERGE_REVISIONS = "revision.merge-revisions";
}
