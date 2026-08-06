package com.example.platform.workflow.temporal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.example.platform.delivery.api.port.DeliveryAfterRenderPort;
import com.example.platform.policy.api.FeatureFlagEvaluator;
import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.api.port.RenderOrchestratorPort;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowException;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Frozen contract RED-3 / GREEN (W1-GAP-003 — retry ownership).
 *
 * <p>Authentic TestWorkflowEnvironment test. Pre-implementation the wired
 * RenderWorkflowImpl activity stubs had NO RetryOptions (Temporal default =
 * unbounded retry), so a permanently failing activity retried forever and the
 * workflow never failed within the wait window (RED: TimeoutException). After
 * the frozen hardening the stubs carry explicit bounded RetryOptions
 * (maximumAttempts=3) and the workflow fails after the budget is exhausted
 * (GREEN).</p>
 */
class RenderWorkflowRetryPolicyTest {

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

    private Worker newWorker(RenderOrchestratorPort orchestrator) {
        Worker worker = env.newWorker(RenderTaskQueue.NAME);
        worker.registerWorkflowImplementationTypes(RenderWorkflowImpl.class);
        worker.registerActivitiesImplementations(renderActivities(orchestrator));
        return worker;
    }

    private RenderActivitiesImpl renderActivities(RenderOrchestratorPort orchestrator) {
        FeatureFlagEvaluator flags = (key, targetingKey, attributes, defaultValue) -> false;
        DeliveryAfterRenderPort delivery = jobId -> 0;
        return new RenderActivitiesImpl(flags, orchestrator, delivery, false);
    }

    private WorkflowStub startWorkflow(String jobId) {
        WorkflowClient client = env.getWorkflowClient();
        WorkflowStub stub = client.newUntypedWorkflowStub(
                "RenderWorkflow",
                io.temporal.client.WorkflowOptions.newBuilder()
                        .setWorkflowId(jobId)
                        .setTaskQueue(RenderTaskQueue.NAME)
                        .build());
        stub.start(jobId, "tenant-1");
        return stub;
    }

    @Test
    void activityPermanentFailure_boundedRetries() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        RenderOrchestratorPort failing = new RenderOrchestratorPort() {
            @Override
            public String submitRenderJob(SubmitRenderJobRequest request) {
                return request.tenantId();
            }

            @Override
            public String executeExistingRenderJob(String tenantId, String jobId) {
                attempts.incrementAndGet();
                throw new IllegalStateException("permanent render failure");
            }

            @Override
            public String finishRenderPhase(String tenantId, String jobId) {
                return jobId;
            }

            @Override
            public List<com.example.platform.render.app.dto.ArtifactInfoResponse> getArtifactsByJob(
                    String jobId) {
                return List.of();
            }

            @Override
            public byte[] getArtifactContent(String artifactId) {
                return new byte[0];
            }

            @Override
            public String loadJobTimelineJson(String tenantId, String jobId) {
                return "{}";
            }
        };
        newWorker(failing);
        env.start();

        WorkflowStub stub = startWorkflow("retry-job-1");
        try {
            stub.getResult(10, TimeUnit.SECONDS, String.class);
            fail("expected bounded retry to exhaust and fail the workflow");
        } catch (WorkflowException e) {
            // GREEN: workflow failed after bounded retries (maximumAttempts=3)
            assertTrue(attempts.get() <= 4, "attempts=" + attempts.get());
        }
    }

    @Test
    void nonRetryableFailure_attemptedOnce() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        RenderOrchestratorPort nonRetryable = new RenderOrchestratorPort() {
            @Override
            public String submitRenderJob(SubmitRenderJobRequest request) {
                return request.tenantId();
            }

            @Override
            public String executeExistingRenderJob(String tenantId, String jobId) {
                attempts.incrementAndGet();
                throw new IllegalArgumentException("job not found — non-retryable");
            }

            @Override
            public String finishRenderPhase(String tenantId, String jobId) {
                return jobId;
            }

            @Override
            public List<com.example.platform.render.app.dto.ArtifactInfoResponse> getArtifactsByJob(
                    String jobId) {
                return List.of();
            }

            @Override
            public byte[] getArtifactContent(String artifactId) {
                return new byte[0];
            }

            @Override
            public String loadJobTimelineJson(String tenantId, String jobId) {
                return "{}";
            }
        };
        newWorker(nonRetryable);
        env.start();

        WorkflowStub stub = startWorkflow("retry-job-2");
        try {
            stub.getResult(10, TimeUnit.SECONDS, String.class);
            fail("expected workflow failure");
        } catch (WorkflowException e) {
            // Frozen policy: IllegalArgumentException is non-retryable -> single attempt
            assertTrue(attempts.get() <= 1, "attempts=" + attempts.get());
        }
    }
}
