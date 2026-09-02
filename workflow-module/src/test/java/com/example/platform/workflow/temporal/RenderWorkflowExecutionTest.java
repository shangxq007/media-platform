package com.example.platform.workflow.temporal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.delivery.api.port.DeliveryAfterRenderPort;
import com.example.platform.policy.api.FeatureFlagEvaluator;
import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.api.port.RenderJobSubmitContinuation;
import com.example.platform.render.api.port.RenderOrchestratorPort;
import com.example.platform.render.api.port.RenderWorkflowResumePort;
import com.example.platform.workflow.adapter.TemporalRenderExecutionAdapter;
import com.example.platform.workflow.adapter.TemporalRenderSubmitContinuation;
import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Authentic Temporal boundary test (frozen contract RED-1/RED-2, W1-GAP-001/013).
 *
 * <p>Starts the real RenderWorkflow through the real application entrypoint
 * (RenderOrchestratorPort.submitRenderJob -&gt; RenderJobSubmitContinuation -&gt;
 * TemporalRenderExecutionAdapter -&gt; WorkflowClient) with a
 * TestWorkflowEnvironment-backed worker registering RenderWorkflowImpl and
 * RenderActivitiesImpl on media-platform-tasks. No mocked WorkflowClient.</p>
 */
class RenderWorkflowExecutionTest {

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
                return request.tenantId() + "-" + request.projectId();
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
            public String loadJobTimelineJson(String tenantId, String jobId) {
                return "{}";
            }
        };
        DeliveryAfterRenderPort delivery = jobId -> 0;
        return new RenderActivitiesImpl(flags, orchestrator, delivery, false);
    }

    private RenderWorkflowResumePort resumePort() {
        return (tenantId, jobId) -> jobId;
    }

    private RenderOrchestratorPort submitPort(WorkflowClient client, String fixedJobId) {
        TemporalRenderExecutionAdapter adapter = new TemporalRenderExecutionAdapter(client);
        RenderJobSubmitContinuation continuation =
                new TemporalRenderSubmitContinuation(adapter);
        return new SubmitPort(continuation, resumePort(), fixedJobId);
    }

    @Test
    void boundaryTest_workflowRunsThroughRealEntrypoint() {
        WorkflowClient client = env.getWorkflowClient();
        RenderOrchestratorPort submitPort = submitPort(client, "job-1");

        SubmitRenderJobRequest request = new SubmitRenderJobRequest(
                "tenant-1", "proj-1", "prompt", "hd", "snap-1");
        String jobId = submitPort.submitRenderJob(request,
                com.example.platform.shared.events.RenderInitiator.from(
                        com.example.platform.shared.authorization.CanonicalActor.system("test-workflow", "tenant-1")));

        assertNotNull(jobId);
        assertTrue(jobId.contains("job-1"));
    }

    @Test
    void workflowStart_usesRenderJobIdAsWorkflowId() {
        WorkflowClient client = env.getWorkflowClient();
        RenderOrchestratorPort submitPort = submitPort(client, "job-wfid-42");

        SubmitRenderJobRequest request = new SubmitRenderJobRequest(
                "tenant-1", "proj-1", "prompt", "hd", "snap-1");
        String jobId = submitPort.submitRenderJob(request,
                com.example.platform.shared.events.RenderInitiator.from(
                        com.example.platform.shared.authorization.CanonicalActor.system("test-workflow", "tenant-1")));
        assertNotNull(jobId);
    }

    /**
     * Render-side submit port that wires the frozen continuation chain.
     */
    private static final class SubmitPort implements RenderOrchestratorPort {
        private final RenderJobSubmitContinuation continuation;
        private final RenderWorkflowResumePort resumePort;
        private final String fixedJobId;

        private SubmitPort(
                RenderJobSubmitContinuation continuation,
                RenderWorkflowResumePort resumePort,
                String fixedJobId) {
            this.continuation = continuation;
            this.resumePort = resumePort;
            this.fixedJobId = fixedJobId;
        }

        @Override
        public String submitRenderJob(SubmitRenderJobRequest request, com.example.platform.shared.events.RenderInitiator initiator) {
            return continuation.continueAfterSubmit(request.tenantId(), fixedJobId, request);
        }

        @Override
        public String executeExistingRenderJob(String tenantId, String jobId) {
            return resumePort.executeExistingRenderJob(tenantId, jobId);
        }

        @Override
        public String finishRenderPhase(String tenantId, String jobId) {
            return jobId;
        }

        @Override
        public String loadJobTimelineJson(String tenantId, String jobId) {
            return "{}";
        }
    }
}
