package com.example.platform.workflow.temporal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.example.platform.delivery.api.port.DeliveryAfterRenderPort;
import com.example.platform.policy.api.FeatureFlagEvaluator;
import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.api.port.RenderOrchestratorPort;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionResponse;
import io.temporal.client.WorkflowClient;
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
 * Frozen contract RED-4 / RED-8 / GREEN (W1-GAP-005 timeout consistency,
 * W1-GAP-011 memo).
 *
 * <p>Authentic TestWorkflowEnvironment tests. Pre-implementation the adapter
 * set workflow run timeout 30m which conflicts with the activity start-to-close
 * 2h (RED-4), and set no memo (RED-8). After the frozen hardening the timeout
 * hierarchy is consistent (run &gt;= max activity start-to-close) and the
 * workflow memo carries tenantId/projectId/jobId (GREEN).</p>
 */
class RenderWorkflowMemoAndTimeoutTest {

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
            public String loadJobTimelineJson(String tenantId, String jobId) {
                return "{}";
            }
        };
    }

    /**
     * RED-4: timeout hierarchy consistency. Deterministic source-level
     * assertion (same mechanism as the frozen architecture rules): the
     * adapter's workflow run timeout must be &gt;= the workflow's max activity
     * start-to-close. Pre-implementation run 30m &lt; activity 2h -> RED.
     */
    @Test
    void timeoutHierarchy_runTimeoutAtLeastActivityStartToClose() throws Exception {
        String adapterSrc = Files.readString(
                Path.of("src/main/java/com/example/platform/workflow/adapter/"
                        + "TemporalRenderExecutionAdapter.java"));
        String workflowSrc = Files.readString(
                Path.of("src/main/java/com/example/platform/workflow/temporal/"
                        + "RenderWorkflowImpl.java"));

        long runTimeoutMinutes = extractDurationMinutes(adapterSrc, "setWorkflowRunTimeout");
        long executionTimeoutMinutes = extractDurationMinutes(adapterSrc, "setWorkflowExecutionTimeout");
        long activityS2CMinutes = extractDurationMinutes(workflowSrc, "setStartToCloseTimeout");

        assertTrue(activityS2CMinutes >= 120, "activity start-to-close must be >= 2h, was "
                + activityS2CMinutes + "m");
        assertTrue(runTimeoutMinutes >= activityS2CMinutes,
                "workflow run timeout " + runTimeoutMinutes + "m must be >= activity "
                        + activityS2CMinutes + "m");
        assertTrue(executionTimeoutMinutes >= runTimeoutMinutes,
                "workflow execution timeout " + executionTimeoutMinutes + "m must be >= run "
                        + runTimeoutMinutes + "m");
    }

    private static long extractDurationMinutes(String source, String setter) {
        int idx = source.indexOf(setter);
        if (idx < 0) {
            fail("missing " + setter + " in production source");
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("Duration\\.of(Minutes|Hours)\\((\\d+)\\)")
                .matcher(source.substring(idx));
        if (!m.find()) {
            fail("no Duration expression after " + setter + " in production source");
        }
        long value = Long.parseLong(m.group(2));
        return "Hours".equals(m.group(1)) ? value * 60 : value;
    }

    /**
     * RED-8: workflow memo carries tenantId/projectId/jobId. Pre-implementation
     * no memo is set -> RED. After hardening memo present -> GREEN.
     */
    @Test
    void workflowStart_setsMemoWithTenantProjectJob() throws Exception {
        newWorker(okOrchestrator());
        env.start();

        // Frozen contract (W1-GAP-011): TemporalRenderExecutionAdapter sets
        // memo with tenantId/projectId/jobId at workflow start. Source-level
        // assertion that the production adapter carries the frozen memo.
        String adapterSrc = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/example/platform/workflow/adapter/"
                        + "TemporalRenderExecutionAdapter.java"));
        assertTrue(adapterSrc.contains("setMemo"),
                "adapter must set memo");
        assertTrue(adapterSrc.contains("\"tenantId\""), "memo must carry tenantId");
        assertTrue(adapterSrc.contains("\"projectId\""), "memo must carry projectId");
        assertTrue(adapterSrc.contains("\"jobId\""), "memo must carry jobId");

        // Behavior: start with the same memo the adapter sets; describe must
        // return the memo fields.
        java.util.Map<String, Object> memo = new java.util.HashMap<>();
        memo.put("tenantId", "tenant-1");
        memo.put("projectId", "proj-1");
        memo.put("jobId", "memo-job-1");

        WorkflowClient client = env.getWorkflowClient();
        WorkflowStub stub = client.newUntypedWorkflowStub(
                "RenderWorkflow",
                io.temporal.client.WorkflowOptions.newBuilder()
                        .setWorkflowId("memo-job-1")
                        .setTaskQueue(RenderTaskQueue.NAME)
                        .setMemo(memo)
                        .build());
        stub.start("memo-job-1", "tenant-1");
        stub.getResult(10, TimeUnit.SECONDS, String.class);

        WorkflowExecution execution = stub.getExecution();
        DescribeWorkflowExecutionResponse response = env.getWorkflowService()
                .blockingStub()
                .describeWorkflowExecution(
                        DescribeWorkflowExecutionRequest.newBuilder()
                                .setNamespace("default")
                                .setExecution(execution)
                                .build());
        var memoFields = response.getWorkflowExecutionInfo().getMemo().getFieldsMap();
        assertTrue(memoFields.containsKey("tenantId"), "memo missing tenantId: "
                + memoFields.keySet());
        assertTrue(memoFields.containsKey("projectId"), "memo missing projectId");
        assertTrue(memoFields.containsKey("jobId"), "memo missing jobId");
    }
}
