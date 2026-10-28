package com.example.platform.workerfabric.domain;

import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.workerfabric.reuse.*;
import java.io.IOException;

/** Execution-owned publication transaction; Artifact/native owners still perform their own commits. */
public interface CapabilityExecutionCompletionPort {
    RuntimeClosedLoopTaskResult publish(String tenant,String project,String execution,long generation,
            ExecutableTaskId task,TaskRuntimeExecution nativeExecution,RuntimePublicationTransaction.CommitAction action) throws IOException;
    void fail(String tenant,String project,String execution,long generation,String code,boolean uncertain);
    final class AlreadyCompleted extends RuntimeException {}
}
