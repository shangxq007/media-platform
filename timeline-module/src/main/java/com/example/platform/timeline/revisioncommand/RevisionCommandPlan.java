package com.example.platform.timeline.revisioncommand;

/**
 * REVISION_COMMAND_MODEL_V1 (RC7/RC9/RC16/RC17/RC18/RC26/RC28/RC39): sealed
 * immutable RevisionCommandPlan variants — history/ref transitions, NOT media
 * edit. Context transitions are explicitly OUTSIDE RevisionCommand (RCI1).
 */
public sealed interface RevisionCommandPlan permits
        RevisionCommandPlan.CreateRefPlan,
        RevisionCommandPlan.DeleteRefPlan,
        RevisionCommandPlan.RestoreRevisionPlan,
        RevisionCommandPlan.MergeRevisionPlan {

    String planDigest();

    record CreateRefPlan(
            String projectId,
            RevisionRef newRef,
            String sourceRevisionId,
            String planDigest) implements RevisionCommandPlan {
    }

    record DeleteRefPlan(
            String projectId,
            RevisionRef ref,
            String expectedHeadRevisionId,
            String planDigest) implements RevisionCommandPlan {
    }

    record RestoreRevisionPlan(
            String projectId,
            String historicalSourceRevisionId,
            RevisionRef targetRef,
            String expectedTargetHeadRevisionId,
            String candidateContentHash,
            String planDigest) implements RevisionCommandPlan {
    }

    record MergeRevisionPlan(
            String projectId,
            String sourceRevisionId,
            RevisionRef targetRef,
            String targetOursRevisionId,
            String mergeBaseRevisionId,
            String candidateContentHash,
            boolean conflict,
            String mergedPayloadJson,
            String planDigest) implements RevisionCommandPlan {
    }
}
