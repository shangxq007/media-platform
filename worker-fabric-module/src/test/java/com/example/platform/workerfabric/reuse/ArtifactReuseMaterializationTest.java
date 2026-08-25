package com.example.platform.workerfabric.reuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.domain.ArtifactReplicaBinding;
import com.example.platform.artifact.domain.InMemoryArtifactCommitService;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.execution.taskgraph.ExecutionReuseKey;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.contract.provider.StorageProvider;
import com.example.platform.storage.contract.memory.InMemoryStorageProvider;
import com.example.platform.storage.contract.namespace.DataClassification;
import com.example.platform.storage.contract.namespace.NamespaceClass;
import com.example.platform.storage.contract.namespace.RegionPolicy;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import com.example.platform.storage.contract.provider.StorageProviderCapabilities;
import com.example.platform.workerfabric.domain.ArtifactCommitEvidence;
import com.example.platform.workerfabric.domain.CompletionAuthorityPort;
import com.example.platform.workerfabric.domain.CompletionDecision;
import com.example.platform.workerfabric.domain.CompletionEvidence;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactReuseMaterializationTest {

    private static final Instant NOW = Instant.parse("2026-08-25T00:00:00Z");
    private static final ExecutionReuseKey KEY = key("task-semantics");
    private static final ArtifactId ARTIFACT_ID = new ArtifactId("artifact-reuse-1");

    @TempDir
    Path temp;

    @Test
    void lookupIsOnlyAValidatedHitAfterTenantStateAndDigestAuthorityChecks() {
        ArtifactReuseIndexPort index = mock(ArtifactReuseIndexPort.class);
        ArtifactQueryService artifacts = mock(ArtifactQueryService.class);
        ContentDigest digest = digest("available bytes".getBytes());
        ReusableArtifactRecord record = record("tenant-a", digest);
        when(index.lookup("tenant-a", KEY)).thenReturn(Optional.of(record));
        when(artifacts.getArtifact("tenant-a", ARTIFACT_ID))
                .thenReturn(Optional.of(artifact("tenant-a", digest, ArtifactState.AVAILABLE)));

        ValidatedReuseDecision decision =
                new ArtifactReuseResolver(index, artifacts).resolve(
                        "tenant-a", KEY, Cacheability.CACHEABLE);

        assertThat(decision.outcome()).isEqualTo(ValidatedReuseDecision.Outcome.VALIDATED_HIT);
        assertThat(decision.record()).contains(record);
    }

    @Test
    void tenantMismatchDigestMismatchAndQuarantineFailClosed() {
        ArtifactReuseIndexPort index = mock(ArtifactReuseIndexPort.class);
        ArtifactQueryService artifacts = mock(ArtifactQueryService.class);
        ContentDigest expected = digest("expected".getBytes());
        ReusableArtifactRecord wrongTenant = record("tenant-b", expected);
        when(index.lookup("tenant-a", KEY)).thenReturn(Optional.of(wrongTenant));
        ArtifactReuseResolver resolver = new ArtifactReuseResolver(index, artifacts);

        assertThat(resolver.resolve("tenant-a", KEY, Cacheability.CACHEABLE).outcome())
                .isEqualTo(ValidatedReuseDecision.Outcome.UNAUTHORIZED);
        verify(artifacts, never()).getArtifact("tenant-a", ARTIFACT_ID);

        ReusableArtifactRecord scoped = record("tenant-a", expected);
        when(index.lookup("tenant-a", KEY)).thenReturn(Optional.of(scoped));
        when(artifacts.getArtifact("tenant-a", ARTIFACT_ID)).thenReturn(Optional.of(
                artifact("tenant-a", digest("different".getBytes()), ArtifactState.AVAILABLE)));
        assertThat(resolver.resolve("tenant-a", KEY, Cacheability.CACHEABLE).outcome())
                .isEqualTo(ValidatedReuseDecision.Outcome.CORRUPT);

        when(artifacts.getArtifact("tenant-a", ARTIFACT_ID)).thenReturn(Optional.of(
                artifact("tenant-a", expected, ArtifactState.QUARANTINED)));
        assertThat(resolver.resolve("tenant-a", KEY, Cacheability.CACHEABLE).outcome())
                .isEqualTo(ValidatedReuseDecision.Outcome.CORRUPT);
    }

    @Test
    void notCacheableAndMissingIndexNeverConsultArtifactAuthority() {
        ArtifactReuseIndexPort index = mock(ArtifactReuseIndexPort.class);
        ArtifactQueryService artifacts = mock(ArtifactQueryService.class);
        ArtifactReuseResolver resolver = new ArtifactReuseResolver(index, artifacts);

        assertThat(resolver.resolve("tenant-a", KEY, Cacheability.NOT_CACHEABLE).outcome())
                .isEqualTo(ValidatedReuseDecision.Outcome.NOT_CACHEABLE);
        assertThat(resolver.resolve("tenant-a", KEY, Cacheability.CACHEABLE).outcome())
                .isEqualTo(ValidatedReuseDecision.Outcome.MISS);
        verify(artifacts, never()).getArtifact("tenant-a", ARTIFACT_ID);
    }

    @Test
    void localCachePublishesAtomicallyRecoversCorruptionAndRejectsPathTraversalByConstruction()
            throws Exception {
        byte[] bytes = "immutable-materialization".getBytes();
        ArtifactPin pin = new ArtifactPin(ARTIFACT_ID, digest(bytes));
        WorkerLocalMaterializationCache cache =
                new WorkerLocalMaterializationCache(temp.resolve("cache"), 1024);
        AtomicInteger downloads = new AtomicInteger();

        MaterializedArtifact first = cache.getOrMaterialize(pin, () -> {
            downloads.incrementAndGet();
            return new ByteArrayInputStream(bytes);
        });
        MaterializedArtifact hit = cache.getOrMaterialize(pin, () -> {
            downloads.incrementAndGet();
            return new ByteArrayInputStream(bytes);
        });

        assertThat(first.path()).isEqualTo(hit.path());
        assertThat(first.path()).startsWith(temp.resolve("cache").toAbsolutePath().normalize());
        assertThat(first.path().getFileName().toString()).matches("[0-9a-f]{64}");
        assertThat(downloads).hasValue(1);
        Files.writeString(first.path(), "corrupt");

        MaterializedArtifact repaired = cache.getOrMaterialize(pin, () -> {
            downloads.incrementAndGet();
            return new ByteArrayInputStream(bytes);
        });
        assertThat(Files.readAllBytes(repaired.path())).containsExactly(bytes);
        assertThat(downloads).hasValue(2);
        assertThat(Files.list(repaired.path().getParent())
                .noneMatch(path -> path.getFileName().toString().contains(".tmp"))).isTrue();
        assertThatThrownBy(() -> ContentDigest.sha256("../../outside"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentMaterializationDownloadsOnceAndEvictionDeletesOnlyLocalCopies() throws Exception {
        byte[] bytes = "concurrent".getBytes();
        ArtifactPin pin = new ArtifactPin(ARTIFACT_ID, digest(bytes));
        WorkerLocalMaterializationCache cache =
                new WorkerLocalMaterializationCache(temp.resolve("cache"), bytes.length + 1L);
        AtomicInteger downloads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                return cache.getOrMaterialize(pin, () -> {
                    downloads.incrementAndGet();
                    return new ByteArrayInputStream(bytes);
                });
            });
            var second = executor.submit(() -> {
                start.await();
                return cache.getOrMaterialize(pin, () -> {
                    downloads.incrementAndGet();
                    return new ByteArrayInputStream(bytes);
                });
            });
            start.countDown();
            assertThat(first.get().path()).isEqualTo(second.get().path());
            assertThat(downloads).hasValue(1);
        } finally {
            executor.shutdownNow();
        }

        byte[] other = "other-data".getBytes();
        ArtifactPin otherPin = new ArtifactPin(new ArtifactId("artifact-reuse-2"), digest(other));
        cache.getOrMaterialize(otherPin, () -> new ByteArrayInputStream(other));
        assertThat(cache.usedBytes()).isLessThanOrEqualTo(bytes.length + 1L);
    }

    @Test
    void digestMismatchNeverPublishesMaterializedOrStagedOutput() throws Exception {
        ArtifactPin pin = new ArtifactPin(ARTIFACT_ID, digest("expected".getBytes()));
        WorkerLocalMaterializationCache cache =
                new WorkerLocalMaterializationCache(temp.resolve("cache"), 1024);

        assertThatThrownBy(() -> cache.getOrMaterialize(
                pin, () -> new ByteArrayInputStream("wrong".getBytes())))
                .isInstanceOf(ArtifactMaterializationException.class)
                .hasMessageContaining("digest");
        assertThat(cache.usedBytes()).isZero();
    }

    @Test
    void directMaterializerUsesOnlyStorageProviderBoundaryAndPreservesArtifactPin() throws Exception {
        byte[] bytes = "storage-provider-bytes".getBytes();
        ContentDigest digest = digest(bytes);
        ArtifactPin pin = new ArtifactPin(ARTIFACT_ID, digest);
        ArtifactQueryService artifacts = mock(ArtifactQueryService.class);
        StorageProviderId providerId = new StorageProviderId("provider-local");
        StorageObjectId objectId = new StorageObjectId("object-1");
        ArtifactReplicaBinding replica = new ArtifactReplicaBinding(
                "binding-1",
                ARTIFACT_ID,
                objectId,
                new StorageReplicaId("replica-1"),
                providerId,
                ReplicaRole.PRIMARY,
                "local",
                NOW);
        StorageProvider provider = mock(StorageProvider.class);
        when(artifacts.getArtifact("tenant-a", ARTIFACT_ID))
                .thenReturn(Optional.of(artifact("tenant-a", digest, ArtifactState.AVAILABLE)));
        when(artifacts.listReplicas("tenant-a", ARTIFACT_ID)).thenReturn(List.of(replica));
        when(provider.openRead(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(new ByteArrayInputStream(bytes)));
        DirectStorageArtifactMaterializer materializer = new DirectStorageArtifactMaterializer(
                artifacts,
                Map.of(providerId, provider),
                new WorkerLocalMaterializationCache(temp.resolve("direct-cache"), 1024));

        MaterializedArtifact result = materializer.materialize("tenant-a", pin);

        assertThat(result.artifactPin()).isEqualTo(pin);
        assertThat(Files.readAllBytes(result.path())).containsExactly(bytes);
        verify(provider).openRead(org.mockito.ArgumentMatchers.argThat(
                request -> request.objectId().equals(objectId)));
    }

    @Test
    void stagedOutputCalculatesDigestBeforeArtifactCommit() throws Exception {
        byte[] bytes = "provider-output".getBytes();
        OutputStagingArea staging = new OutputStagingArea(temp.resolve("staging"));

        StagedExecutionOutput output = staging.stage(new ByteArrayInputStream(bytes));

        assertThat(output.contentDigest()).isEqualTo(digest(bytes));
        assertThat(output.byteLength()).isEqualTo(bytes.length);
        assertThat(Files.readAllBytes(output.path())).containsExactly(bytes);
    }

    @Test
    void stagedBytesAreDurablyPublishedBeforeBoundArtifactAuthorityCommit() throws Exception {
        byte[] bytes = "provider-output-to-commit".getBytes();
        StagedExecutionOutput staged = new OutputStagingArea(temp.resolve("commit-staging"))
                .stage(new ByteArrayInputStream(bytes));
        StorageProviderId providerId = new StorageProviderId("durable-memory");
        InMemoryStorageProvider storage = new InMemoryStorageProvider(
                providerId, new StorageProviderCapabilities(providerId, Map.of()));
        DurableArtifactCommitResult result = new ArtifactOutputCommitOrchestrator(
                new InMemoryArtifactCommitService(),
                Map.of(providerId, storage),
                new Phase16RuntimeMetrics(new SimpleMeterRegistry()))
                .commit(
                        staged,
                        new DurableOutputTarget(
                                providerId,
                                new StorageNamespace(
                                        "tenant-a", "project-a", NamespaceClass.DERIVED,
                                        RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL),
                                "write-session-1"),
                        new ArtifactCommitMetadata(
                                ARTIFACT_ID, "tenant-a", ArtifactMediaType.VIDEO,
                                ArtifactKind.RENDER_MASTER, Artifact.CURRENT_SCHEMA_VERSION,
                                ReplicaRole.PRIMARY, "local", List.of(), NOW, NOW, null, null));

        assertThat(result.storagePublication().objectId())
                .isEqualTo(new StorageObjectId("obj-write-session-1"));
        assertThat(result.artifactCommitResult().replicaBinding().storageObjectId())
                .isEqualTo(result.storagePublication().objectId());
        assertThat(result.artifactCommitResult().artifact().contentDigest())
                .isEqualTo(staged.contentDigest());
        assertThat(storage.stat(result.storagePublication().objectId())).isPresent();
    }

    @Test
    void reusePublicationBecomesVisibleOnlyAfterFencedCompletion() {
        ArtifactReuseIndexPort index = mock(ArtifactReuseIndexPort.class);
        CompletionAuthorityPort completion = mock(CompletionAuthorityPort.class);
        CompletionEvidence completionEvidence = mock(CompletionEvidence.class);
        ArtifactCommitEvidence commitEvidence = mock(ArtifactCommitEvidence.class);
        ReusableArtifactPublication publication = publication();
        when(index.stageWinningPublication(publication))
                .thenReturn(ReusePublicationResult.STAGED_PENDING);
        when(completion.completeIfCurrent(completionEvidence, commitEvidence))
                .thenReturn(CompletionDecision.COMPLETED);
        when(index.activateWinningPublication(publication, completionEvidence))
                .thenReturn(ReusePublicationResult.ACTIVATED_WINNER);

        FencedReuseCompletionResult result = new FencedReuseCompletionOrchestrator(index, completion)
                .complete(publication, completionEvidence, commitEvidence);

        assertThat(result.completionDecision()).isEqualTo(CompletionDecision.COMPLETED);
        assertThat(result.publicationResult()).isEqualTo(ReusePublicationResult.ACTIVATED_WINNER);
        verify(index).activateWinningPublication(publication, completionEvidence);
    }

    @Test
    void staleGenerationCannotPublishWinnerOrCompleteTask() {
        ArtifactReuseIndexPort index = mock(ArtifactReuseIndexPort.class);
        CompletionAuthorityPort completion = mock(CompletionAuthorityPort.class);
        CompletionEvidence completionEvidence = mock(CompletionEvidence.class);
        ArtifactCommitEvidence commitEvidence = mock(ArtifactCommitEvidence.class);
        ReusableArtifactPublication publication = publication();
        when(index.stageWinningPublication(publication))
                .thenReturn(ReusePublicationResult.STALE_OWNER_REJECTED);

        FencedReuseCompletionResult result = new FencedReuseCompletionOrchestrator(index, completion)
                .complete(publication, completionEvidence, commitEvidence);

        assertThat(result.publicationResult()).isEqualTo(ReusePublicationResult.STALE_OWNER_REJECTED);
        verify(completion, never()).completeIfCurrent(completionEvidence, commitEvidence);
        verify(index, never()).activateWinningPublication(publication, completionEvidence);
    }

    private static ExecutionReuseKey key(String semantic) {
        String canonical = "roadmap22.execution-reuse-key.v1" + semantic;
        return new ExecutionReuseKey(
                ExecutionReuseKey.VERSION, canonical, sha256(canonical.getBytes()));
    }

    private static ReusableArtifactRecord record(String tenantId, ContentDigest digest) {
        return new ReusableArtifactRecord(
                tenantId,
                KEY,
                new ArtifactPin(ARTIFACT_ID, digest),
                new ExecutableTaskId("1".repeat(64)),
                new ExecutionAttemptId("attempt-1"),
                ExecutionOwnershipGeneration.first(),
                NOW);
    }

    private static ReusableArtifactPublication publication() {
        return new ReusableArtifactPublication(record("tenant-a", digest("bytes".getBytes())));
    }

    private static Artifact artifact(String tenantId, ContentDigest digest, ArtifactState state) {
        return new Artifact(
                ARTIFACT_ID,
                tenantId,
                digest,
                10,
                ArtifactMediaType.VIDEO,
                ArtifactKind.RENDER_MASTER,
                state,
                Artifact.CURRENT_SCHEMA_VERSION,
                NOW);
    }

    private static ContentDigest digest(byte[] bytes) {
        return ContentDigest.sha256(sha256(bytes));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
