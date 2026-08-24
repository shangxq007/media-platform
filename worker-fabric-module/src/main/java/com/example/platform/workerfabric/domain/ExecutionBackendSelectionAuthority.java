package com.example.platform.workerfabric.domain;

import com.example.platform.execution.taskgraph.ExecutableTaskId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** PostgreSQL-backed ONE_WORKLOAD_ONE_ACTIVE_PLACEMENT_AUTHORITY_V1 boundary. */
public interface ExecutionBackendSelectionAuthority {

    ActivationResult activate(ExecutionBackendSelection selection, Instant selectedAt);

    boolean markTerminal(ExecutionBackendSelectionId selectionId, Instant terminalAt);

    Optional<DurableExecutionBackendSelection> findActive(ExecutableTaskId executableTaskId);

    record ActivationResult(
            ActivationStatus status,
            DurableExecutionBackendSelection authoritativeSelection) {

        public ActivationResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(authoritativeSelection, "authoritativeSelection");
        }

        public boolean activated() {
            return status == ActivationStatus.ACTIVATED;
        }
    }

    enum ActivationStatus {
        ACTIVATED,
        REJECTED_ACTIVE_SELECTION
    }
}
