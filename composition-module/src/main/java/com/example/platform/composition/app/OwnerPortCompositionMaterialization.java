package com.example.platform.composition.app;

import com.example.platform.artifact.domain.*;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.storage.api.*;
import com.example.platform.workerfabric.domain.providernative.ProviderExecutionOutput;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.security.MessageDigest;

/** Concrete Composition output binding over Storage, Artifact and Media owner ports. */
public final class OwnerPortCompositionMaterialization implements CompositionMaterializationPort {
    private final StorageOutputPort storage;
    private final ArtifactCommitService artifacts;
    private final Path storageRoot;
    private final CompositionResultRepository results;

    public OwnerPortCompositionMaterialization(StorageOutputPort storage, ArtifactCommitService artifacts,
                                               Path storageRoot, CompositionResultRepository results) {
        this.storage = Objects.requireNonNull(storage); this.artifacts = Objects.requireNonNull(artifacts);
        this.storageRoot = storageRoot.toAbsolutePath().normalize();
        this.results = Objects.requireNonNull(results);
    }

    @Override public java.util.Optional<CompositionMaterializationAdapter.Result> findCommitted(CompositionExecutionRequest request) {
        return results.findMaterialized(request.tenantId(), request.idempotencyKey(), request.idempotencyKey())
                .map(r -> new CompositionMaterializationAdapter.Result(request,
                        new IssuedOutput(request.tenantId(), request.workspaceId(), r.placementId(), r.digest(), r.length()),
                        new CommittedArtifact(request.tenantId(), request.workspaceId(), r.artifactId(), r.digest())));
    }

    @Override public IssuedOutput issue(CompositionExecutionRequest request, ProviderExecutionOutput output) {
        try {
            Files.createDirectories(storageRoot);
            String relative = "composition/" + request.idempotencyKey() + ".mp4";
            Path target = storageRoot.resolve(relative).normalize();
            if (!target.startsWith(storageRoot)) throw new IllegalArgumentException("output path escapes storage root");
            Files.createDirectories(target.getParent());
            try (var input = output.content()) { Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING); }
            var owner = new StorageOwnershipScope(request.tenantId(), request.workspaceId());
            var written = storage.write(new StorageOutputPort.OutputCommand(owner,
                    new IssuanceIdempotencyKey(request.idempotencyKey()),
                    relative, "video/mp4"));
            var i = written.issuance();
            return new IssuedOutput(request.tenantId(), request.workspaceId(), i.objectId().value(),
                    i.placement().committedDigest().canonicalValue(), i.placement().committedLength());
        } catch (IOException e) { throw new IllegalStateException("provider output staging failed", e); }
    }

    @Override public CommittedArtifact commitArtifact(CompositionExecutionRequest request, IssuedOutput output) {
        var now = Instant.now();
        var result = artifacts.commit(new ArtifactCommitRequest(
                new ArtifactId("composition-" + request.idempotencyKey()), request.tenantId(),
                ContentDigest.sha256(output.digest()), output.length(), ArtifactMediaType.VIDEO,
                ArtifactKind.GENERATED_MEDIA, 1,
                new com.example.platform.storage.contract.StorageObjectId(output.placementId()),
                new com.example.platform.storage.contract.StorageReplicaId("composition-" + request.idempotencyKey()),
                new com.example.platform.storage.contract.StorageProviderId("localFsProvider"), ReplicaRole.PRIMARY,
                "local", request.idempotencyKey(), java.util.List.of(), now, now, null, request.workspaceId()));
        return new CommittedArtifact(request.tenantId(), request.workspaceId(), result.artifact().artifactId().value(), output.digest());
    }

    @Override public void compensate(IssuedOutput output) { /* Storage owner recovery reconciles disposable intents. */ }

    @Override public void recordCommitted(CompositionExecutionRequest request, IssuedOutput issued,
            CommittedArtifact artifact) {
        results.recordMaterialized(request.tenantId(), request.idempotencyKey(), request.idempotencyKey(),
                new CompositionResultRepository.MaterializedResult(
                        CompositionExecutionIds.of(request.tenantId(), request.workspaceId(), request.idempotencyKey()),
                        "attempt-0", issued.placementId(), issued.digest(), issued.length(),
                        artifact.artifactId()), 0);
    }

}
