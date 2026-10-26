package com.example.platform.config.firstrender;

import com.example.platform.execution.binding.BoundGraphReference;
import com.example.platform.workflow.temporal.mediatask.MediaTaskWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Temporal implementation of {@link FirstRenderWorkflowStarter}.
 *
 * <p>Uses the existing dynamic typed-stub pattern (as {@code ThumbnailService} / {@code
 * CoverImageService}): {@code newWorkflowStub(MediaTaskWorkflow.class, options)} on the canonical
 * task queue, then {@code WorkflowClient.start(workflow::run, reference, tenantId)}. The client is
 * obtained lazily and fails closed when absent.
 *
 * <p>{@link WorkflowExecutionAlreadyStarted} is the ONLY condition treated as "started" (the target
 * workflow already exists); every other failure propagates unchanged.
 */
@Component
public class TemporalFirstRenderWorkflowStarter implements FirstRenderWorkflowStarter {

    /** Canonical task queue (Phase 2 confirmed). */
    static final String TASK_QUEUE = "media-platform-tasks";

    private final ObjectProvider<WorkflowClient> clients;

    public TemporalFirstRenderWorkflowStarter(ObjectProvider<WorkflowClient> clients) {
        this.clients = Objects.requireNonNull(clients, "clients");
    }

    @Override
    public boolean start(BoundGraphReference reference, String tenantId, String workflowId) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(workflowId, "workflowId");
        if(!tenantId.equals(reference.tenantId()) || !workflowId.equals(com.example.platform.workflow.temporal.RenderWorkflowIdentity.forJob(tenantId,reference.renderJobId())))throw new IllegalArgumentException("Render workflow identity mismatch");
        WorkflowClient client = requireClient();
        MediaTaskWorkflow workflow = client.newWorkflowStub(
                MediaTaskWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue(TASK_QUEUE)
                        .build());
        return classifyStart(() -> WorkflowClient.start(workflow::run, reference, tenantId));
    }

    /**
     * Classifies the raw start invocation: a fresh start and {@link WorkflowExecutionAlreadyStarted}
     * both mean the target workflow exists; any other failure is propagated (never swallowed).
     */
    static boolean classifyStart(Runnable startCall) {
        try {
            startCall.run();
            return true;
        } catch (WorkflowExecutionAlreadyStarted alreadyStarted) {
            return true;
        }
    }

    private WorkflowClient requireClient() {
        WorkflowClient client = clients.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException(
                    "Temporal client unavailable; first render requires an enabled Temporal client");
        }
        return client;
    }
}
