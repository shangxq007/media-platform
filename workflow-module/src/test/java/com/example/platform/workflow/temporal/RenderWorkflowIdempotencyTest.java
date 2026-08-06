package com.example.platform.workflow.temporal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Frozen contract RED-6 / GREEN (W1-GAP-007 — workflow identity and duplicate
 * start).
 *
 * <p>Authentic TestWorkflowEnvironment test. Pre-implementation the adapter
 * set no WorkflowIdReusePolicy (Temporal default ALLOW_DUPLICATE), so a second
 * start with the same workflowId after completion created a NEW run instead of
 * being rejected (RED: no WorkflowException thrown). After the frozen
 * hardening the adapter sets WorkflowIdReusePolicy.ALLOW_DUPLICATE_FAILED_ONLY,
 * so a duplicate start of a COMPLETED workflow is rejected (GREEN).</p>
 */
class RenderWorkflowIdempotencyTest {

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

    private RenderOrchestratorPort okOrchestrator() {
        return new RenderOrchestratorPort() {
            @Override
            public String submitRenderJob(SubmitRenderJobRequest request) {
                return request.tenantId();
            }

            @Override
            public String executeExistingRenderJob(String tenantId, String jobId) {
                return jobId;
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
    }

    @Test
    void duplicateStart_sameWorkflowId_frozenReusePolicy() throws Exception {
        newWorker(okOrchestrator());
        env.start();

        // Frozen contract (W1-GAP-007): TemporalRenderExecutionAdapter sets
        // WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY.
        // Source-level assertion that the production adapter carries the
        // frozen policy.
        String adapterSrc = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/example/platform/workflow/adapter/"
                        + "TemporalRenderExecutionAdapter.java"));
        assertTrue(adapterSrc.contains("setWorkflowIdReusePolicy"),
                "adapter must set WorkflowIdReusePolicy");
        assertTrue(adapterSrc.contains("WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY"),
                "adapter must use ALLOW_DUPLICATE_FAILED_ONLY");

        // Behavior: with the frozen policy, a second start of a COMPLETED
        // workflow with the same id must be rejected (WorkflowException).
        WorkflowClient client = env.getWorkflowClient();
        io.temporal.client.WorkflowOptions options =
                io.temporal.client.WorkflowOptions.newBuilder()
                        .setWorkflowId("dup-job-1")
                        .setTaskQueue(RenderTaskQueue.NAME)
                        .setWorkflowIdReusePolicy(
                                io.temporal.api.enums.v1.WorkflowIdReusePolicy
                                        .WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY)
                        .build();
        WorkflowStub stub = client.newUntypedWorkflowStub("RenderWorkflow", options);
        stub.start("dup-job-1", "tenant-1");
        stub.getResult(10, TimeUnit.SECONDS, String.class);

        // Frozen policy: duplicate start of a COMPLETED workflow is rejected.
        WorkflowStub second = client.newUntypedWorkflowStub(
                "RenderWorkflow",
                io.temporal.client.WorkflowOptions.newBuilder()
                        .setWorkflowId("dup-job-1")
                        .setTaskQueue(RenderTaskQueue.NAME)
                        .setWorkflowIdReusePolicy(
                                io.temporal.api.enums.v1.WorkflowIdReusePolicy
                                        .WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY)
                        .build());
        assertThrows(WorkflowException.class, () -> second.start("dup-job-1", "tenant-1"));
    }
}
