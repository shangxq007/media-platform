package com.example.platform.workerfabric.domain;

import com.example.platform.execution.taskgraph.ExecutableTaskId;
import java.time.Instant;
import java.util.Objects;

/** Reloadable active placement-authority record for one ExecutableTask. */
public record DurableExecutionBackendSelection(
        ExecutionBackendSelectionId id,
        ExecutableTaskId executableTaskId,
        ExecutionBackend backend,
        PlacementAuthorityScope placementAuthorityScope,
        Instant selectedAt) {

    public DurableExecutionBackendSelection {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(executableTaskId, "executableTaskId");
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(placementAuthorityScope, "placementAuthorityScope");
        Objects.requireNonNull(selectedAt, "selectedAt");
        if (placementAuthorityScope != backend.placementAuthorityScope()) {
            throw new IllegalArgumentException(
                    "placement-authority scope must be derived from the selected backend");
        }
    }
}
