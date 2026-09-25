package com.example.platform.coverimage;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactCommitRequest;
import com.example.platform.artifact.domain.ArtifactCommitResult;
import com.example.platform.artifact.domain.ArtifactCommitService;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ProvenanceRelationType;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.storage.api.IssuanceIdempotencyKey;
import com.example.platform.storage.api.StorageOutputPort;
import com.example.platform.storage.api.StorageOwnershipScope;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One transactional fence for cover storage publication, Artifact commitment and task completion.
 *
 * <p>The cover is committed exclusively through {@link ArtifactCommitService} (the single canonical
 * Artifact write path) as an {@link ArtifactMediaType#IMAGE} Artifact related to its subject Artifact
 * by {@link ProvenanceRelationType#COVER_OF}. No ArtifactKind is added and no relation row is written
 * directly.
 */
@Service
public class CoverImageCommitService {

    private final CoverImageTaskStore tasks;
    private final StorageOutputPort outputs;
    private final ArtifactCommitService commits;

    public CoverImageCommitService(
            CoverImageTaskStore tasks, StorageOutputPort outputs, ArtifactCommitService commits) {
        this.tasks = tasks;
        this.outputs = outputs;
        this.commits = commits;
    }

    @Transactional
    public String commit(
            String tenant,
            String project,
            String taskId,
            String subjectArtifactId,
            String contentType,
            byte[] bytes,
            Path output,
            String root) {
        if (!tasks.lockForCommit(tenant, project, taskId)) {
            return null;
        }
        try {
            try {
                Files.createDirectories(output.getParent());
                Files.write(output, bytes);
            } catch (IOException failure) {
                throw new IllegalStateException("cover-image output staging failed", failure);
            }
            String relative = Path.of(root).toAbsolutePath().normalize()
                    .relativize(output).toString().replace(java.io.File.separatorChar, '/');
            var written = outputs.write(new StorageOutputPort.OutputCommand(
                    new StorageOwnershipScope(tenant, project),
                    new IssuanceIdempotencyKey("cover-image:" + taskId),
                    relative,
                    contentType));
            var issue = written.issuance();
            var coverArtifactId = new ArtifactId("art-cover-" + UUID.nameUUIDFromBytes(
                    (tenant + "\0" + project + "\0" + taskId + "\0" + issue.objectId().value())
                            .getBytes(StandardCharsets.UTF_8)));
            String pin = issue.placement().committedDigest().canonicalValue();
            ArtifactCommitResult accepted = commits.commit(new ArtifactCommitRequest(
                    coverArtifactId,
                    tenant,
                    issue.placement().committedDigest(),
                    issue.placement().committedLength(),
                    ArtifactMediaType.IMAGE,
                    ArtifactKind.DERIVED_MEDIA,
                    Artifact.CURRENT_SCHEMA_VERSION,
                    issue.objectId(),
                    issue.placement().replicaId(),
                    issue.placement().location().providerId(),
                    ReplicaRole.PRIMARY,
                    issue.placement().location().region(),
                    "cover-image:" + taskId,
                    List.of(new ArtifactCommitRequest.ProvenanceEdgeDeclaration(
                            new ArtifactId(subjectArtifactId),
                            ProvenanceRelationType.COVER_OF,
                            CoverImageContracts.OPERATION_ID,
                            1,
                            taskId,
                            pin,
                            pin)),
                    Instant.now(),
                    Instant.now(),
                    taskId,
                    project));
            return tasks.completeLocked(tenant, project, taskId,
                    accepted.artifact().artifactId().value())
                    ? accepted.artifact().artifactId().value()
                    : null;
        } finally {
            try {
                Files.deleteIfExists(output);
                Files.deleteIfExists(output.getParent());
            } catch (Exception ignored) {
                // staging cleanup is best effort; the committed Artifact is the durable record
            }
        }
    }
}
