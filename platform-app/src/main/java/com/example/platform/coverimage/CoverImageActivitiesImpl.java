package com.example.platform.coverimage;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.workerfabric.reuse.ArtifactMaterializerPort;
import io.temporal.spring.boot.ActivityImpl;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Temporal activity adapter. The worker owns execution: it validates the subject Artifact, reads its
 * bytes through the canonical digest-verified materializer, runs the registered provider and commits
 * the cover through the Artifact single write path. No FFmpeg mechanics live here.
 */
@Component
@ConditionalOnProperty(name = "platform.runtime.role", havingValue = "WORKER")
@ActivityImpl(taskQueues = "media-platform-tasks")
public class CoverImageActivitiesImpl implements CoverImageActivities {

    private final CoverImageTaskStore tasks;
    private final ArtifactQueryService artifacts;
    private final ArtifactMaterializerPort materializer;
    private final CoverImageCapabilityRegistry capabilities;
    private final CoverImageCommitService commitService;
    private final Path root;

    public CoverImageActivitiesImpl(
            CoverImageTaskStore tasks,
            ArtifactQueryService artifacts,
            ArtifactMaterializerPort materializer,
            CoverImageCapabilityRegistry capabilities,
            CoverImageCommitService commitService,
            @Value("${app.cover-image.work-root:./.data/cover-image-work}") String root) {
        this.tasks = tasks;
        this.artifacts = artifacts;
        this.materializer = materializer;
        this.capabilities = capabilities;
        this.commitService = commitService;
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public String renderAndCommit(String taskId, String tenant, String project) {
        if (!tasks.statusIfActive(taskId, CoverImageContracts.Status.RUNNING, null, null)) {
            return null;
        }
        var task = tasks.find(tenant, project, taskId)
                .orElseThrow(() -> new IllegalArgumentException("cover-image task not found"));
        ArtifactId subjectId = new ArtifactId(task.subjectArtifactId());
        var subject = artifacts.getArtifact(tenant, subjectId)
                .orElseThrow(() -> fail(taskId, "SUBJECT_ARTIFACT_UNAVAILABLE",
                        "subject artifact is unavailable in tenant scope"));
        if (subject.state() != ArtifactState.AVAILABLE) {
            throw fail(taskId, "SUBJECT_ARTIFACT_UNAVAILABLE", "subject artifact is not available");
        }
        Path input;
        try {
            input = materializer
                    .materialize(tenant, new ArtifactPin(subjectId, subject.contentDigest()))
                    .materializedArtifact()
                    .path();
        } catch (java.io.IOException failure) {
            throw fail(taskId, "SUBJECT_MATERIALIZATION_FAILED", "subject artifact could not be materialized");
        }
        CoverImageCapabilityProvider.Result produced;
        try {
            produced = capabilities.invoke(task.toRequest(), input,
                    () -> Thread.currentThread().isInterrupted()
                            || tasks.isCancelled(tenant, project, taskId));
        } catch (RuntimeException failure) {
            tasks.statusIfActive(taskId, CoverImageContracts.Status.FAILED, null, "PROVIDER_FAILED");
            throw failure;
        }
        if (!produced.succeeded()) {
            tasks.statusIfActive(taskId,
                    "CANCELLED".equals(produced.failureCode())
                            ? CoverImageContracts.Status.CANCELLED
                            : CoverImageContracts.Status.FAILED,
                    null,
                    produced.failureCode());
            return null;
        }
        if (!tasks.statusIfActive(taskId, CoverImageContracts.Status.COMMITTING, null, null)) {
            return null;
        }
        Path output = root.resolve(task.idempotencyKey()).resolve("provider-output").normalize();
        try {
            return commitService.commit(tenant, project, taskId, task.subjectArtifactId(),
                    produced.contentType(), produced.bytes(),
                    output.resolve("cover." + task.imageFormat()), root.toString());
        } catch (Exception failure) {
            tasks.statusIfActive(taskId, CoverImageContracts.Status.FAILED, null, "OUTPUT_COMMIT_FAILED");
            throw new IllegalStateException("cover-image commit failed", failure);
        }
    }

    private IllegalArgumentException fail(String taskId, String code, String message) {
        tasks.statusIfActive(taskId, CoverImageContracts.Status.FAILED, null, code);
        return new IllegalArgumentException(message);
    }
}
