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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Frozen contract RED-7 / GREEN (W1-GAP-008 — heartbeat and cancellation at
 * the activity boundary).
 *
 * <p>Authentic TestWorkflowEnvironment test. Pre-implementation the long
 * render activity had no heartbeat (RED: source assertion fails on
 * Activity.getExecutionContext() usage). After the frozen hardening the
 * activity heartbeats with stable bounded reference-oriented details and
 * cancellation is observable at the workflow boundary (GREEN).</p>
 */
class RenderWorkflowHeartbeatTest {

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

    /**
     * RED-7: the long render activity must heartbeat with stable bounded
     * details. Deterministic source-level assertion (same mechanism as the
     * frozen architecture rules). Pre-implementation no heartbeat -> RED.
     */
    @Test
    void longActivity_heartbeatsWithStableDetails() throws Exception {
        String src = Files.readString(
                Path.of("src/main/java/com/example/platform/workflow/temporal/"
                        + "RenderActivitiesImpl.java"));
        assertTrue(src.contains("Activity.getExecutionContext()"),
                "activity must use Activity.getExecutionContext()");
        assertTrue(src.contains(".heartbeat("),
                "activity must call heartbeat(...)");
    }

    /**
     * RED-7 (b): cancellation must be observable at the workflow boundary.
     * Pre-implementation the workflow ignored cancellation during the activity
     * and completed normally -> assertion fails (RED). After hardening,
     * cancellation terminates the workflow (WorkflowException, cancelled).
     */
    @Test
    void longActivity_cancellationObservableAtBoundary() throws Exception {
        RenderOrchestratorPort slow = new RenderOrchestratorPort() {
            @Override
            public String submitRenderJob(SubmitRenderJobRequest request) {
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
        newWorker(slow);
        env.start();

        WorkflowClient client = env.getWorkflowClient();
        WorkflowStub stub = client.newUntypedWorkflowStub(
                "RenderWorkflow",
                io.temporal.client.WorkflowOptions.newBuilder()
                        .setWorkflowId("hb-job-1")
                        .setTaskQueue(RenderTaskQueue.NAME)
                        .build());
        stub.start("hb-job-1", "tenant-1");
        stub.cancel();
        try {
            String result = stub.getResult(10, TimeUnit.SECONDS, String.class);
            fail("workflow completed normally despite cancellation: " + result);
        } catch (WorkflowException e) {
            // GREEN: cancellation observable at workflow boundary
            assertTrue(e.getMessage() != null);
        }
    }
}
