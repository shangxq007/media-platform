package com.example.platform.workerfabric.reuse;

import com.example.platform.artifact.domain.ArtifactCommitRequest;
import com.example.platform.artifact.domain.ArtifactCommitResult;
import com.example.platform.artifact.domain.ArtifactCommitService;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.provider.StorageProvider;
import com.example.platform.storage.contract.write.StorageWriteSession;
import com.example.platform.storage.contract.write.WriteSessionResult;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.Objects;

/** Publishes staged bytes durably, then binds and performs the Artifact-owned commit. */
public final class ArtifactOutputCommitOrchestrator {

    private final ArtifactCommitService artifactCommitService;
    private final Map<StorageProviderId, StorageProvider> storageProviders;
    private final Phase16RuntimeMetrics metrics;

    public ArtifactOutputCommitOrchestrator(
            ArtifactCommitService artifactCommitService,
            Map<StorageProviderId, StorageProvider> storageProviders,
            Phase16RuntimeMetrics metrics) {
        this.artifactCommitService = Objects.requireNonNull(
                artifactCommitService, "artifactCommitService");
        Objects.requireNonNull(storageProviders, "storageProviders");
        this.storageProviders = Map.copyOf(storageProviders);
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public DurableArtifactCommitResult commit(
            StagedExecutionOutput stagedOutput,
            DurableOutputTarget outputTarget,
            ArtifactCommitMetadata metadata) throws IOException {
        Objects.requireNonNull(stagedOutput, "stagedOutput");
        Objects.requireNonNull(outputTarget, "outputTarget");
        Objects.requireNonNull(metadata, "metadata");
        StorageProvider provider = storageProviders.get(outputTarget.providerId());
        if (provider == null || !provider.providerId().equals(outputTarget.providerId())) {
            metrics.durablePublish(Phase16RuntimeMetrics.OperationOutcome.FAILURE);
            throw new IllegalArgumentException("durable output target has no exact StorageProvider");
        }
        StorageWriteSession session = null;
        boolean durable = false;
        DurableStoragePublication publication;
        try {
            session = provider.beginWrite(
                    outputTarget.writeSessionId(),
                    outputTarget.namespace(),
                    stagedOutput.contentDigest(),
                    stagedOutput.byteLength());
            requireSessionMatches(provider, stagedOutput, session);
            try (InputStream input = Files.newInputStream(stagedOutput.path())) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        provider.write(session, buffer, 0, read);
                    }
                }
            }
            WriteSessionResult completed = provider.completeWrite(
                    session, stagedOutput.contentDigest());
            durable = true;
            if (!completed.idempotencyKey().equals(session.idempotencyKey())) {
                throw new IllegalStateException(
                        "completed storage identity does not match write session idempotency");
            }
            publication = new DurableStoragePublication(
                    provider.providerId(),
                    completed.objectId(),
                    completed.replicaId(),
                    stagedOutput.contentDigest(),
                    stagedOutput.byteLength(),
                    completed.idempotencyKey(),
                    completed.alreadyCommitted());
            metrics.durablePublish(Phase16RuntimeMetrics.OperationOutcome.SUCCESS);
        } catch (IOException | RuntimeException failure) {
            metrics.durablePublish(Phase16RuntimeMetrics.OperationOutcome.FAILURE);
            if (session != null && !durable) {
                try {
                    provider.abortWrite(session);
                } catch (RuntimeException abortFailure) {
                    failure.addSuppressed(abortFailure);
                }
            }
            throw failure;
        }

        ArtifactCommitRequest commitRequest = bindRequest(metadata, publication);
        ArtifactCommitResult result;
        try {
            result = artifactCommitService.commit(commitRequest);
            requireCommitMatches(stagedOutput, publication, result);
            metrics.artifactCommit(Phase16RuntimeMetrics.OperationOutcome.SUCCESS);
        } catch (RuntimeException failure) {
            metrics.artifactCommit(Phase16RuntimeMetrics.OperationOutcome.FAILURE);
            throw new ArtifactCommitAfterDurablePublicationException(publication, failure);
        }
        return new DurableArtifactCommitResult(publication, result);
    }

    private static void requireSessionMatches(
            StorageProvider provider,
            StagedExecutionOutput stagedOutput,
            StorageWriteSession session) {
        Objects.requireNonNull(session, "StorageProvider.beginWrite result");
        if (!session.providerSelection().equals(provider.providerId())
                || !session.expectedDigest().matches(stagedOutput.contentDigest())
                || session.expectedLength() != stagedOutput.byteLength()) {
            throw new IllegalStateException("StorageProvider returned a mismatched write session");
        }
    }

    private static ArtifactCommitRequest bindRequest(
            ArtifactCommitMetadata metadata,
            DurableStoragePublication publication) {
        return new ArtifactCommitRequest(
                metadata.artifactId(),
                metadata.tenantId(),
                publication.contentDigest(),
                publication.byteLength(),
                metadata.mediaType(),
                metadata.artifactKind(),
                metadata.schemaVersion(),
                publication.objectId(),
                publication.replicaId(),
                publication.providerId(),
                metadata.replicaRole(),
                metadata.region(),
                publication.idempotencyKey(),
                metadata.provenanceDeclarations(),
                metadata.evaluatedAt(),
                metadata.createdAt(),
                metadata.renderJobId(),
                metadata.projectId());
    }

    private static void requireCommitMatches(
            StagedExecutionOutput stagedOutput,
            DurableStoragePublication publication,
            ArtifactCommitResult result) {
        Objects.requireNonNull(result, "ArtifactCommitService result");
        if (result.artifact().state() != ArtifactState.AVAILABLE
                || !result.artifact().contentDigest().matches(stagedOutput.contentDigest())
                || result.artifact().byteLength() != stagedOutput.byteLength()
                || !result.replicaBinding().storageObjectId().equals(publication.objectId())
                || !result.replicaBinding().storageReplicaId().equals(publication.replicaId())
                || !result.replicaBinding().providerId().equals(publication.providerId())) {
            throw new IllegalStateException(
                    "Artifact authority commit result does not match durable staged output");
        }
    }
}
