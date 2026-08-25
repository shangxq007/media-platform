package com.example.platform.workerfabric.reuse;

import com.example.platform.execution.taskgraph.ExecutionReuseKey;
import com.example.platform.workerfabric.domain.CompletionEvidence;
import java.time.Instant;
import java.util.Optional;

/** Persistent execution-key to Artifact-pin index; never Artifact existence authority. */
public interface ArtifactReuseIndexPort {

    Optional<ReusableArtifactRecord> lookup(String tenantId, ExecutionReuseKey executionReuseKey);

    ReusePublicationResult stageWinningPublication(ReusableArtifactPublication publication);

    ReusePublicationResult activateWinningPublication(
            ReusableArtifactPublication publication,
            CompletionEvidence completionEvidence);

    /** Removes index metadata only. Implementations must never delete Artifact or storage bytes. */
    boolean evict(String tenantId, ExecutionReuseKey executionReuseKey);

    /** Deletes abandoned pending metadata only; winning entries and Artifacts are untouched. */
    int purgePendingBefore(Instant cutoff);
}
