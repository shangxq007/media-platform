package com.example.platform.workerfabric.infrastructure;

import com.example.platform.workerfabric.domain.*;
import com.example.platform.workerfabric.reuse.*;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import java.io.IOException;
import java.time.Instant;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Native ownership linearizes Artifact commit, completion and proven physical reservation release. */
@Component
public class NativeOutputPublicationTransaction implements RuntimePublicationTransaction {
    private final DSLContext db;
    private final AtomicAssignmentGrantBoundary grants;
    private final ExecutionLifecycleBoundary lifecycle;
    public NativeOutputPublicationTransaction(DSLContext db,AtomicAssignmentGrantBoundary grants,ExecutionLifecycleBoundary lifecycle) {
        this.db=db;this.grants=grants;this.lifecycle=lifecycle;
    }
    @Override @Transactional(rollbackFor=Exception.class)
    public RuntimeClosedLoopTaskResult commit(ExecutableTaskId task,TaskRuntimeExecution execution,CommitAction action) throws IOException {
        if(db.fetchOne("select task_id from wf_task_ownership where task_id=? for update",task.sha256Hex())==null) {
            throw new IllegalStateException("native output ownership is absent");
        }
        var grant=grants.findCurrentGrant(task).orElseThrow(()->new IllegalStateException("native output ownership is no longer current"));
        if(!grant.attempt().id().equals(execution.runtimeContext().platformExecutionAttemptId())
                || !grant.attempt().ownershipGeneration().equals(execution.runtimeContext().platformOwnershipGeneration())) {
            throw new IllegalArgumentException("stale native output attempt or generation");
        }
        // This boundary is reached only after the runtime has returned/closed and output staging
        // has succeeded. Unknown sandbox cleanup fails before reaching publication.
        var result=action.commit();
        var released=lifecycle.confirmPhysicalRelease(new ExecutionLifecycleBoundary.PhysicalReleaseConfirmation(
                "native-output-release:"+grant.attempt().id().value(),ExecutionLifecycleBoundary.LeaseOwnershipFence.from(grant.lease()),
                grant.assignment().reservationIds(),Instant.now()));
        if(released!=ExecutionLifecycleBoundary.PhysicalReleaseResult.RELEASED
                && released!=ExecutionLifecycleBoundary.PhysicalReleaseResult.ALREADY_RELEASED_NOOP) {
            throw new IllegalStateException("native completion lacks physical release confirmation");
        }
        return result;
    }
}
