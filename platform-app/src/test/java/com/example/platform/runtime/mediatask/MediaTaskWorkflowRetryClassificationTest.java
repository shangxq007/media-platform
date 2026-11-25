package com.example.platform.runtime.mediatask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.execution.binding.BoundGraphReference;
import com.example.platform.execution.binding.BoundGraphDigestMismatchException;
import com.example.platform.workflow.temporal.RenderTaskQueue;
import com.example.platform.workflow.temporal.mediatask.MediaTaskActivities;
import com.example.platform.workflow.temporal.mediatask.MediaTaskWorkflowImpl;
import com.example.platform.workflow.temporal.mediatask.MediaTaskWorkflowResult;
import com.example.platform.workflow.temporal.mediatask.PreparedTaskRef;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.reuse.TaskRuntimeExecutionConstructionException;
import com.example.platform.workerfabric.reuse.TaskOutputPublicationPlanningException;
import io.temporal.client.WorkflowException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Deterministic-vs-transient activity failure classification for {@link MediaTaskWorkflowImpl}.
 *
 * <p>Runs the real workflow against the in-process Temporal test environment with a stubbed
 * activity, so the retry policy is exercised, not asserted from source. No Temporal server,
 * database or container is involved.
 */
class MediaTaskWorkflowRetryClassificationTest {

    private static final String TENANT = "tenant-retry";
    private static final String PLAN_DIGEST = "a".repeat(64);
    private static final String GRAPH_DIGEST = "b".repeat(64);

    private TestWorkflowEnvironment env;

    @BeforeEach
    void setUp() {
        env = TestWorkflowEnvironment.newInstance();
    }

    @AfterEach
    void tearDown() {
        if (env != null) {
            env.close();
        }
    }

    @Test
    void grantAbsentPreconditionFailureIsNotRetried() {
        AtomicInteger attempts = new AtomicInteger();

        startWith(failingActivities(attempts, new TaskRuntimeExecutionConstructionException(
                "GRANT_ABSENT", "no current grant exists for this task")));

        assertThat(attempts).as("a deterministic grant precondition must not be retried").hasValue(1);
    }

    @Test
    void grantMintRejectedPreconditionFailureIsNotRetried() {
        AtomicInteger attempts = new AtomicInteger();

        startWith(failingActivities(attempts, new TaskRuntimeExecutionConstructionException(
                "GRANT_MINT_REJECTED", "ADMISSION_REJECTED: REGISTRATION_STALE")));

        assertThat(attempts).hasValue(1);
    }

    @Test
    void missingPublicationScopeIsNotRetriedAndStaysFailClosed() {
        AtomicInteger attempts = new AtomicInteger();

        startWith(failingActivities(attempts, new IllegalArgumentException(
                "projectId must not be blank")));

        assertThat(attempts).hasValue(1);
    }

    @Test
    void transientDatabaseFailureIsStillRetried() {
        AtomicInteger attempts = new AtomicInteger();

        startWith(failingActivities(attempts, new DataAccessException("temporary database fault")));

        // Bounded retry budget (20 attempts) is unchanged for recoverable infrastructure faults.
        assertThat(attempts).as("transient database faults must remain retryable").hasValue(20);
    }

    @Test
    void unplannablePublicationIntentIsNotRetried() {
        AtomicInteger attempts = new AtomicInteger();

        startWith(failingActivities(attempts, new TaskOutputPublicationPlanningException(
                "PUBLICATION_INTENT_UNDECLARED", "no terminal output for this task")));

        assertThat(attempts).hasValue(1);
    }

    @Test
    void nonReproducibleBoundGraphIsNotRetriedInPrepare() {
        AtomicInteger prepareAttempts = new AtomicInteger();
        MediaTaskActivities failingPrepare = new MediaTaskActivities() {
            @Override public void completeTask(PreparedTaskRef prepared, MediaTaskWorkflowResult result,String tenantId) {}
            @Override public void failTask(BoundGraphReference reference,String tenantId,boolean cancelled) {}

            @Override
            public PreparedTaskRef prepareTask(BoundGraphReference reference, String tenantId) {
                prepareAttempts.incrementAndGet();
                throw new BoundGraphDigestMismatchException("expected", "actual");
            }

            @Override
            public MediaTaskWorkflowResult executeTask(PreparedTaskRef prepared, String tenantId) {
                throw new IllegalStateException("executeTask must not be reached");
            }
        };

        startWith(failingPrepare);

        assertThat(prepareAttempts).hasValue(1);
    }

    @Test
    void providerExecutionFailureRemainsRetryable() {
        AtomicInteger attempts = new AtomicInteger();

        startWith(failingActivities(attempts, new ProviderNativeExecutionFailure(
                ProviderNativeFailureCode.PROCESS_NONZERO_EXIT, "provider exited non-zero")));

        // Provider runtime failures keep their existing retry semantics (not reclassified here).
        assertThat(attempts).hasValue(20);
    }

    private void startWith(MediaTaskActivities activities) {
        Worker worker = env.newWorker(RenderTaskQueue.NAME);
        worker.registerWorkflowImplementationTypes(MediaTaskWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        env.start();
        WorkflowStub stub = env.getWorkflowClient().newUntypedWorkflowStub(
                "MediaTaskWorkflow",
                WorkflowOptions.newBuilder()
                        .setWorkflowId("wf-" + UUID.randomUUID())
                        .setTaskQueue(RenderTaskQueue.NAME)
                        .build());

        stub.start(reference(), TENANT);

        assertThatThrownBy(() -> stub.getResult(30, TimeUnit.SECONDS, MediaTaskWorkflowResult.class))
                .isInstanceOf(WorkflowException.class);
    }

    private static MediaTaskActivities failingActivities(
            AtomicInteger executeAttempts, RuntimeException failure) {
        return new MediaTaskActivities() {
            @Override public void completeTask(PreparedTaskRef prepared, MediaTaskWorkflowResult result,String tenantId) {}
            @Override public void failTask(BoundGraphReference reference,String tenantId,boolean cancelled) {}

            @Override
            public PreparedTaskRef prepareTask(BoundGraphReference reference, String tenantId) {
                return new PreparedTaskRef(
                        reference.tenantId(),
                        reference.renderJobId(),
                        reference.planRef(),
                        reference.planDigest(),
                        reference.expectedExecutableTaskGraphDigest(),
                        List.of());
            }

            @Override
            public MediaTaskWorkflowResult executeTask(PreparedTaskRef prepared, String tenantId) {
                executeAttempts.incrementAndGet();
                throw failure;
            }
        };
    }

    private static BoundGraphReference reference() {
        return new BoundGraphReference(
                TENANT, "render-job-retry", "plan-ref-retry", PLAN_DIGEST, GRAPH_DIGEST);
    }
}
