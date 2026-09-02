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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Frozen contract RED-5 (W1-GAP-006 — cancellation).
 *
 * <p>Authentic TestWorkflowEnvironment test. Uses untyped WorkflowStub
 * signal/query (SDK APIs that compile in the pre-implementation state) to
 * drive the frozen cancellation surface. In the pre-implementation state
 * RenderWorkflow has NO cancel signal handler and NO status query handler,
 * so the signal/query FAILS at runtime (WorkflowException: unknown
 * signal/query) — the runtime failure IS the RED demonstration. After the
 * frozen hardening (conditional path 3: @SignalMethod cancel + @QueryMethod
 * status) the signal is handled and cancellation is distinguishable from
 * failure.</p>
 */
class RenderWorkflowCancellationTest {

    private TestWorkflowEnvironment env;

    @BeforeEach
    void setUp() {
        env = TestWorkflowEnvironment.newInstance();
        Worker worker = env.newWorker(RenderTaskQueue.NAME);
        worker.registerWorkflowImplementationTypes(RenderWorkflowImpl.class);
        worker.registerActivitiesImplementations(renderActivities());
        env.start();
    }

    @AfterEach
    void tearDown() {
        if (env != null) {
            env.close();
        }
    }

    private RenderActivitiesImpl renderActivities() {
        FeatureFlagEvaluator flags = (key, targetingKey, attributes, defaultValue) -> false;
        RenderOrchestratorPort orchestrator = new RenderOrchestratorPort() {
            @Override
            public String submitRenderJob(SubmitRenderJobRequest request, com.example.platform.shared.events.RenderInitiator initiator) {
                return request.tenantId();
            }

            @Override
            public String executeExistingRenderJob(String tenantId, String jobId) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return jobId;
            }

            @Override
            public String finishRenderPhase(String tenantId, String jobId) {
                return jobId;
            }

            @Override
            public String loadJobTimelineJson(String tenantId, String jobId) {
                return "{}";
            }
        };
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

    /**
     * RED-5: cancel signal must reach the workflow and prevent normal completion.
     * Pre-implementation the workflow has no cancel signal handler, so the
     * signal is ignored and the workflow completes normally -> assertion fails
     * (RED). After the frozen hardening the handler marks the workflow
     * cancelled and it terminates as cancelled (WorkflowException), not with a
     * normal result.
     */
    @Test
    void cancelSignal_reachesWorkflow() throws Exception {
        WorkflowStub stub = startWorkflow("cancel-job-1");
        stub.signal("cancel", "tenant-1", "cancel-job-1", "user-cancelled");
        try {
            String result = stub.getResult(10, java.util.concurrent.TimeUnit.SECONDS, String.class);
            // Pre-implementation: signal ignored, workflow completed normally -> RED
            fail("workflow completed normally despite cancel signal: " + result);
        } catch (io.temporal.client.WorkflowException e) {
            // GREEN: workflow terminated as cancelled (not a normal result)
            assertTrue(e.getMessage() != null);
        }
    }

    /**
     * RED-5 (b): status query must distinguish CANCELLED from failure.
     * Pre-implementation: no status query handler -> runtime failure -> RED.
     */
    @Test
    void statusQuery_distinguishesCancellationFromFailure() throws Exception {
        WorkflowStub stub = startWorkflow("cancel-job-2");
        try {
            stub.signal("cancel", "tenant-1", "cancel-job-2", "user-cancelled");
            String status = stub.query("status", String.class, "tenant-1", "cancel-job-2");
            assertTrue(status != null && status.startsWith("CANCELLED"),
                    "status must reflect cancellation, was: " + status);
        } catch (WorkflowException e) {
            // Pre-implementation: unknown query "status" -> RED (no status
            // surface). After hardening the query returns CANCELLED state.
            fail("status query failed: " + e.getMessage());
        }
    }
}
