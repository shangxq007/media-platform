package com.example.platform.workerfabric.reuse;

import com.example.platform.execution.taskgraph.ExecutableTaskId;
import java.io.IOException;

/** The Execution owner serializes cancellation against Artifact commit and fenced completion. */
@FunctionalInterface
public interface RuntimePublicationTransaction {
    RuntimeClosedLoopTaskResult commit(ExecutableTaskId taskId, TaskRuntimeExecution execution,
            CommitAction action) throws IOException;
    @FunctionalInterface interface CommitAction { RuntimeClosedLoopTaskResult commit() throws IOException; }
}
