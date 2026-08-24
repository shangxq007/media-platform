package com.example.platform.workerfabric.domain;

import com.example.platform.execution.taskgraph.ExecutableTaskId;

/**
 * Read-only Task D grant projection returned to Task C.
 *
 * <p>Task D owns the actual assignment, reservation, lease, attempt, and generation types.
 */
public interface AssignmentGrantReference {

    RequestWorkId requestWorkId();

    ExecutableTaskId executableTaskId();
}
