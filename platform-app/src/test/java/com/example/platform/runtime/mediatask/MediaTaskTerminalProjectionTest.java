package com.example.platform.runtime.mediatask;

import com.example.platform.execution.binding.BoundGraphReference;
import com.example.platform.workflow.temporal.mediatask.*;
import com.example.platform.workflow.temporal.RenderTaskQueue;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.client.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real Temporal workflow/retry engine. Projection retries never repeat successful media execution. */
class MediaTaskTerminalProjectionTest {
    static final BoundGraphReference REF=new BoundGraphReference("tenant","job","ref","a".repeat(64),"b".repeat(64));
    static class Activities implements MediaTaskActivities {
        final AtomicInteger execute=new AtomicInteger(),complete=new AtomicInteger(),failed=new AtomicInteger();
        boolean failExecution;
        public PreparedTaskRef prepareTask(BoundGraphReference ref,String tenant){return new PreparedTaskRef(tenant,ref.renderJobId(),ref.planRef(),ref.planDigest(),ref.expectedExecutableTaskGraphDigest(),List.of());}
        public MediaTaskWorkflowResult executeTask(PreparedTaskRef prepared,String tenant){execute.incrementAndGet();if(failExecution)throw new IllegalArgumentException("synthetic invalid execution");return new MediaTaskWorkflowResult(tenant,prepared.renderJobId(),prepared.planDigest(),prepared.expectedExecutableTaskGraphDigest(),List.of(),MediaTaskWorkflowResult.STATUS_EXECUTED);}
        public void completeTask(PreparedTaskRef prepared,MediaTaskWorkflowResult result,String tenant){if(complete.incrementAndGet()<3)throw new org.jooq.exception.DataAccessException("synthetic projection connection fault");}
        public void failTask(BoundGraphReference ref,String tenant,boolean cancelled){failed.incrementAndGet();}
    }
    @Test void transientProjectionFaultRetriesProjectionAndCompletesWithoutRenderingAgain() {
        try(var env=TestWorkflowEnvironment.newInstance()) {
            var activities=new Activities();var worker=env.newWorker(RenderTaskQueue.NAME);worker.registerWorkflowImplementationTypes(MediaTaskWorkflowImpl.class);worker.registerActivitiesImplementations(activities);env.start();
            var workflow=env.getWorkflowClient().newWorkflowStub(MediaTaskWorkflow.class,WorkflowOptions.newBuilder().setWorkflowId("projection-retry").setTaskQueue(RenderTaskQueue.NAME).build());
            assertEquals(MediaTaskWorkflowResult.STATUS_EXECUTED,workflow.run(REF,"tenant").status());
            assertEquals(1,activities.execute.get());assertEquals(3,activities.complete.get());assertEquals(0,activities.failed.get());
        }
    }
    @Test void executionFailureIsProjectedAndWorkflowNeverClaimsSuccess() {
        try(var env=TestWorkflowEnvironment.newInstance()) {
            var activities=new Activities();activities.failExecution=true;var worker=env.newWorker(RenderTaskQueue.NAME);worker.registerWorkflowImplementationTypes(MediaTaskWorkflowImpl.class);worker.registerActivitiesImplementations(activities);env.start();
            var workflow=env.getWorkflowClient().newWorkflowStub(MediaTaskWorkflow.class,WorkflowOptions.newBuilder().setWorkflowId("execution-failure").setTaskQueue(RenderTaskQueue.NAME).build());
            assertThrows(WorkflowFailedException.class,()->workflow.run(REF,"tenant"));assertEquals(1,activities.execute.get());assertEquals(0,activities.complete.get());assertEquals(1,activities.failed.get());
        }
    }
}
