package com.example.platform.coverimage;

import com.example.platform.artifact.app.ArtifactCatalogService;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.shared.identity.ArtifactId;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-side cover-image entry point. The API admits the durable task and starts the workflow; it never
 * executes provider runtime and never touches FFmpeg or storage bytes.
 */
@Service
public class CoverImageService {

    private final CoverImageTaskStore tasks;
    private final ArtifactQueryService artifacts;
    private final ArtifactCatalogService catalog;
    private final WorkflowClient client;

    public CoverImageService(
            CoverImageTaskStore tasks,
            ArtifactQueryService artifacts,
            ArtifactCatalogService catalog,
            WorkflowClient client) {
        this.tasks = tasks;
        this.artifacts = artifacts;
        this.catalog = catalog;
        this.client = client;
    }

    @Transactional
    public CoverImageContracts.Result submit(CoverImageContracts.Request request) {
        ArtifactId subjectId = new ArtifactId(request.subjectArtifactId());
        var entry = catalog.findArtifact(request.tenantId(), subjectId.value())
                .filter(found -> request.projectId().equals(found.projectId()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "subject artifact is outside the requested project scope"));
        var subject = artifacts.getArtifact(request.tenantId(), subjectId)
                .filter(found -> found.state() == ArtifactState.AVAILABLE)
                .orElseThrow(() -> new IllegalArgumentException(
                        "subject artifact is not available"));
        if (!subject.contentDigest().canonicalValue().equals(entry.checksum())) {
            throw new IllegalArgumentException("subject artifact digest does not match the catalog pin");
        }
        var admission = tasks.admit(request);
        if (!admission.created()) {
            return tasks.find(request.tenantId(), request.projectId(), admission.taskId())
                    .orElseThrow()
                    .toResult();
        }
        CoverImageWorkflow workflow = client.newWorkflowStub(
                CoverImageWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue("media-platform-tasks")
                        .setWorkflowId("cover-image:" + request.tenantId() + ":" + admission.taskId())
                        .build());
        try {
            WorkflowClient.start(workflow::run,
                    admission.taskId(), request.tenantId(), request.projectId());
        } catch (WorkflowExecutionAlreadyStarted alreadyStarted) {
            // idempotent admission: the existing workflow owns this task
        }
        return tasks.find(request.tenantId(), request.projectId(), admission.taskId())
                .orElseThrow()
                .toResult();
    }

    public Optional<CoverImageContracts.Result> status(String tenant, String project, String taskId) {
        return tasks.find(tenant, project, taskId).map(CoverImageTaskStore.Task::toResult);
    }
}
