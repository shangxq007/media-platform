package com.example.platform.workerfabric.reuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.artifact.app.ArtifactPinService.ArtifactPin;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactCommitRequest;
import com.example.platform.artifact.domain.ArtifactCommitResult;
import com.example.platform.artifact.domain.ArtifactCommitService;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.domain.ArtifactReplicaBinding;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.domain.InMemoryArtifactCommitService;
import com.example.platform.artifact.domain.ProvenanceEdge;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.execution.taskgraph.ExecutionReuseKey;
import com.example.platform.execution.taskgraph.ExecutionReuseKeyDeriver;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.contract.memory.InMemoryStorageProvider;
import com.example.platform.storage.contract.namespace.DataClassification;
import com.example.platform.storage.contract.namespace.NamespaceClass;
import com.example.platform.storage.contract.namespace.RegionPolicy;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import com.example.platform.storage.contract.provider.StorageObjectMetadata;
import com.example.platform.storage.contract.provider.StorageProvider;
import com.example.platform.storage.contract.provider.StorageProviderCapabilities;
import com.example.platform.storage.contract.read.StorageDeletionRequest;
import com.example.platform.storage.contract.read.StorageDeletionResult;
import com.example.platform.storage.contract.read.StorageReadRequest;
import com.example.platform.storage.contract.write.StorageWriteSession;
import com.example.platform.storage.contract.write.WriteSessionResult;
import com.example.platform.workerfabric.domain.ArtifactCommitEvidence;
import com.example.platform.workerfabric.domain.CompletionAuthorityPort;
import com.example.platform.workerfabric.domain.CompletionDecision;
import com.example.platform.workerfabric.domain.CompletionEventId;
import com.example.platform.workerfabric.domain.CompletionEvidence;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import com.example.platform.workerfabric.domain.ExpectedOutputValidation;
import com.example.platform.workerfabric.domain.ObservedExecutionState;
import com.example.platform.workerfabric.domain.RemoteExecutionId;
import com.example.platform.workerfabric.domain.RemoteProviderExecutionHandle;
import com.example.platform.workerfabric.domain.providernative.ExecutionCommand;
import com.example.platform.workerfabric.domain.providernative.ProcessInvocationSpec;
import com.example.platform.workerfabric.domain.providernative.ProviderExecutionOutput;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionPlan;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding;
import com.example.platform.workerfabric.domain.providernative.RuntimeAdapter;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionBundle;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeClosedLoopConformanceTest {

    private static final String TENANT = "tenant-phase16";
    private static final Instant NOW = Instant.parse("2026-08-25T08:00:00Z");

    @TempDir Path temp;

    @Test
    void firstMissPublishesDurablyAndEquivalentHitSkipsRuntime() throws Exception {
        Harness harness = harness("provider-a");
        String sourceDigest = sourceDigest("unit-one");
        ProviderBoundExecutableTaskGraph graph = RuntimeClosedLoopGraphFixture.single(
                "unit-one", sourceDigest, "provider-a");
        harness.seedSource("unit-one", sourceDigest);

        RuntimeClosedLoopResult first = harness.execute(graph, allTaskIds(graph), "run-1");

        assertThat(first.pruningResult().tasksToExecute()).containsExactlyElementsOf(allTaskIds(graph));
        assertThat(first.executedTaskResults()).hasSize(1);
        RuntimeClosedLoopTaskResult taskResult = first.executedTaskResults().values().iterator().next();
        assertThat(harness.storage.stat(
                taskResult.durableArtifactCommit().storagePublication().objectId())).isPresent();
        assertThat(taskResult.durableArtifactCommit().artifactCommitResult().replicaBinding()
                .storageObjectId()).isEqualTo(
                        taskResult.durableArtifactCommit().storagePublication().objectId());
        assertThat(harness.runtime.executions()).isEqualTo(1);

        RuntimeClosedLoopResult hit = harness.execute(graph, allTaskIds(graph), "run-2");

        assertThat(hit.pruningResult().tasksToExecute()).isEmpty();
        assertThat(hit.pruningResult().reusedTasks()).containsExactlyElementsOf(allTaskIds(graph));
        assertThat(harness.runtime.executions()).isEqualTo(1);
        assertThat(harness.registry.get("media.worker_fabric.phase16.reuse.lookup")
                .tag("outcome", "MISS").counter().count()).isEqualTo(1.0);
        assertThat(harness.registry.get("media.worker_fabric.phase16.reuse.lookup")
                .tag("outcome", "VALIDATED_HIT").counter().count()).isEqualTo(1.0);
        for (String metric : List.of(
                "materialization", "staging", "durable.publish", "artifact.commit")) {
            assertThat(harness.registry.get("media.worker_fabric.phase16." + metric)
                    .tag("outcome", "SUCCESS").counter().count()).isPositive();
        }
    }

    @Test
    void sourceAndProviderSemanticChangesMissAndExecute() throws Exception {
        Harness harness = harness("provider-a", "provider-b");
        String originalDigest = sourceDigest("semantic-a");
        ProviderBoundExecutableTaskGraph original = RuntimeClosedLoopGraphFixture.single(
                "semantic-a", originalDigest, "provider-a");
        harness.seedSource("semantic-a", originalDigest);
        harness.execute(original, allTaskIds(original), "original");

        String changedDigest = sourceDigest("semantic-source-changed");
        ProviderBoundExecutableTaskGraph sourceChanged = RuntimeClosedLoopGraphFixture.single(
                "semantic-source-changed", changedDigest, "provider-a");
        harness.seedSource("semantic-source-changed", changedDigest);
        RuntimeClosedLoopResult sourceMiss = harness.execute(
                sourceChanged, allTaskIds(sourceChanged), "source-change");

        String providerChangedDigest = sourceDigest("semantic-provider-changed");
        ProviderBoundExecutableTaskGraph providerChanged = RuntimeClosedLoopGraphFixture.single(
                "semantic-provider-changed", providerChangedDigest, "provider-b");
        harness.seedSource("semantic-provider-changed", providerChangedDigest);
        RuntimeClosedLoopResult providerMiss = harness.execute(
                providerChanged, allTaskIds(providerChanged), "provider-change");

        assertThat(sourceMiss.reuseDecisions().values())
                .allMatch(value -> value.outcome() == ValidatedReuseDecision.Outcome.MISS);
        assertThat(providerMiss.reuseDecisions().values())
                .allMatch(value -> value.outcome() == ValidatedReuseDecision.Outcome.MISS);
        assertThat(harness.runtime.executions()).isEqualTo(3);
    }

    @Test
    void localCacheAndIndexLossAreSafeMissesThatRematerialize() throws Exception {
        Harness harness = harness("provider-a");
        String sourceDigest = sourceDigest("cache-loss");
        ProviderBoundExecutableTaskGraph graph = RuntimeClosedLoopGraphFixture.single(
                "cache-loss", sourceDigest, "provider-a");
        harness.seedSource("cache-loss", sourceDigest);
        harness.execute(graph, allTaskIds(graph), "first");
        int readsAfterFirst = harness.storage.reads();

        harness.index.clear();
        harness.index.throwLookupOnce = true;
        harness.replaceLocalCache(temp.resolve("replacement-cache"));
        RuntimeClosedLoopResult rerun = harness.execute(graph, allTaskIds(graph), "after-loss");

        assertThat(rerun.pruningResult().tasksToExecute()).containsExactlyElementsOf(allTaskIds(graph));
        assertThat(harness.runtime.executions()).isEqualTo(2);
        assertThat(harness.storage.reads()).isGreaterThan(readsAfterFirst);
    }

    @Test
    void staleAndCorruptArtifactsFailClosedInsteadOfPruning() throws Exception {
        Harness harness = harness("provider-a");
        String sourceDigest = sourceDigest("authority-fail-closed");
        ProviderBoundExecutableTaskGraph graph = RuntimeClosedLoopGraphFixture.single(
                "authority-fail-closed", sourceDigest, "provider-a");
        harness.seedSource("authority-fail-closed", sourceDigest);
        RuntimeClosedLoopResult first = harness.execute(graph, allTaskIds(graph), "first");
        ArtifactPin pin = first.outputArtifactPins().values().iterator().next();

        harness.authority.remove(pin.artifactId());
        RuntimeClosedLoopResult stale = harness.execute(graph, allTaskIds(graph), "stale");
        assertThat(stale.reuseDecisions().values()).allMatch(
                value -> value.outcome() == ValidatedReuseDecision.Outcome.STALE);

        ArtifactPin current = stale.outputArtifactPins().values().iterator().next();
        harness.authority.replaceState(current.artifactId(), ArtifactState.QUARANTINED);
        RuntimeClosedLoopResult corrupt = harness.execute(graph, allTaskIds(graph), "corrupt");
        assertThat(corrupt.reuseDecisions().values()).allMatch(
                value -> value.outcome() == ValidatedReuseDecision.Outcome.CORRUPT);
        assertThat(harness.runtime.executions()).isEqualTo(3);
    }

    @Test
    void storageFailureAbortsBeforeArtifactCompletionAndReuse() throws Exception {
        Harness harness = harness("provider-a");
        String sourceDigest = sourceDigest("storage-failure");
        ProviderBoundExecutableTaskGraph graph = RuntimeClosedLoopGraphFixture.single(
                "storage-failure", sourceDigest, "provider-a");
        harness.seedSource("storage-failure", sourceDigest);
        harness.storage.failWrites = true;

        assertThatThrownBy(() -> harness.execute(graph, allTaskIds(graph), "failure"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("storage write failure");
        assertThat(harness.authority.commitCount()).isZero();
        assertThat(harness.completions.completionCount()).isZero();
        assertThat(harness.index.winnerCount()).isZero();
        assertThat(harness.storage.aborts()).isEqualTo(1);
    }

    @Test
    void artifactFailureAfterDurablePublicationRetainsOrphanEvidence() throws Exception {
        Harness harness = harness("provider-a");
        String sourceDigest = sourceDigest("artifact-failure");
        ProviderBoundExecutableTaskGraph graph = RuntimeClosedLoopGraphFixture.single(
                "artifact-failure", sourceDigest, "provider-a");
        harness.seedSource("artifact-failure", sourceDigest);
        harness.authority.failCommits = true;

        assertThatThrownBy(() -> harness.execute(graph, allTaskIds(graph), "failure"))
                .isInstanceOfSatisfying(
                        ArtifactCommitAfterDurablePublicationException.class,
                        failure -> assertThat(harness.storage.stat(
                                failure.orphanedPublication().objectId())).isPresent());
        assertThat(harness.completions.completionCount()).isZero();
        assertThat(harness.index.winnerCount()).isZero();
    }

    @Test
    void staleGenerationAfterPublicationCannotCompleteOrActivateReuse() throws Exception {
        Harness harness = harness("provider-a");
        String sourceDigest = sourceDigest("stale-generation");
        ProviderBoundExecutableTaskGraph graph = RuntimeClosedLoopGraphFixture.single(
                "stale-generation", sourceDigest, "provider-a");
        harness.seedSource("stale-generation", sourceDigest);
        harness.forceStaleOwner = true;

        assertThatThrownBy(() -> harness.execute(graph, allTaskIds(graph), "stale"))
                .isInstanceOfSatisfying(
                        NonAuthoritativeRuntimeCompletionException.class,
                        failure -> {
                            assertThat(failure.fencedCompletion().publicationResult())
                                    .isEqualTo(ReusePublicationResult.STALE_OWNER_REJECTED);
                            assertThat(harness.storage.stat(failure.durableArtifactCommit()
                                    .storagePublication().objectId())).isPresent();
                        });
        assertThat(harness.completions.completionCount()).isZero();
        assertThat(harness.index.winnerCount()).isZero();
    }

    @Test
    void completionThenReuseActivationIsIdempotent() throws Exception {
        Harness harness = harness("provider-a");
        String sourceDigest = sourceDigest("idempotent-activation");
        ProviderBoundExecutableTaskGraph graph = RuntimeClosedLoopGraphFixture.single(
                "idempotent-activation", sourceDigest, "provider-a");
        harness.seedSource("idempotent-activation", sourceDigest);
        RuntimeClosedLoopResult first = harness.execute(graph, allTaskIds(graph), "same");
        harness.index.suppressLookupOnce = true;

        RuntimeClosedLoopResult retry = harness.execute(graph, allTaskIds(graph), "same");

        assertThat(first.executedTaskResults().values().iterator().next()
                .fencedCompletion().publicationResult())
                .isEqualTo(ReusePublicationResult.ACTIVATED_WINNER);
        assertThat(retry.executedTaskResults().values().iterator().next()
                .fencedCompletion().publicationResult())
                .isEqualTo(ReusePublicationResult.WINNER_IDEMPOTENT);
        assertThat(harness.completions.completionCount()).isEqualTo(1);
        assertThat(harness.index.winnerCount()).isEqualTo(1);
    }

    @Test
    void sharedDependencyValidatedHitPrunesOnceAndFeedsBothConsumers() throws Exception {
        Harness harness = harness("provider-a");
        String sourceDigest = sourceDigest("unit-a");
        ProviderBoundExecutableTaskGraph graph = RuntimeClosedLoopGraphFixture.sharedDependency(
                sourceDigest, "provider-a");
        harness.seedSource("unit-a", sourceDigest);
        ExecutableTaskId shared = graph.taskDependencies().getFirst().producerTaskId();
        Set<ExecutableTaskId> consumers = graph.taskDependencies().stream()
                .map(dependency -> dependency.consumerTaskId()).collect(java.util.stream.Collectors.toSet());
        harness.execute(graph, Set.of(shared), "shared");
        int beforeConsumers = harness.runtime.executions();

        RuntimeClosedLoopResult result = harness.execute(graph, consumers, "consumer");

        assertThat(result.pruningResult().reusedTasks()).containsExactly(shared);
        assertThat(result.pruningResult().tasksToExecute()).containsExactlyInAnyOrderElementsOf(consumers);
        assertThat(harness.runtime.executions() - beforeConsumers).isEqualTo(2);
        assertThat(harness.runtime.lastInputCounts()).containsOnly(1);
    }

    private Harness harness(String... providers) throws IOException {
        return new Harness(temp, List.of(providers));
    }

    private static Set<ExecutableTaskId> allTaskIds(ProviderBoundExecutableTaskGraph graph) {
        return graph.tasks().stream().map(ExecutableTask::id)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static ContentDigest digest(byte[] bytes) {
        try {
            return ContentDigest.sha256(HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static String sourceDigest(String unit) {
        return digest(("source-bytes-" + unit).getBytes(StandardCharsets.UTF_8)).canonicalValue();
    }

    private static final class Harness {
        private final Path root;
        private final RecordingStorageProvider storage;
        private final RecordingArtifactAuthority authority = new RecordingArtifactAuthority();
        private final CompletionState completionState = new CompletionState();
        private final InMemoryReuseIndex index = new InMemoryReuseIndex(completionState);
        private final RecordingCompletionAuthority completions =
                new RecordingCompletionAuthority(completionState);
        private final RecordingRuntimeAdapter runtime = new RecordingRuntimeAdapter();
        private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        private final Phase16RuntimeMetrics metrics = new Phase16RuntimeMetrics(registry);
        private final Map<ProviderBindingPin, ProviderNativeRuntimeBinding<?>> bindings =
                new HashMap<>();
        private ArtifactMaterializerPort materializer;
        private RuntimeClosedLoopOrchestrator orchestrator;
        private boolean forceStaleOwner;

        private Harness(Path root, List<String> providers) throws IOException {
            this.root = root;
            StorageProviderId providerId = new StorageProviderId("phase16-storage");
            this.storage = new RecordingStorageProvider(new InMemoryStorageProvider(
                    providerId, new StorageProviderCapabilities(providerId, Map.of())));
            for (String provider : providers) {
                ProviderBoundExecutableTaskGraph graph = RuntimeClosedLoopGraphFixture.single(
                        "binding-probe-" + provider,
                        sourceDigest("binding-probe-" + provider), provider);
                bindings.put(graph.tasks().getFirst().providerBindingPin(), binding());
            }
            replaceLocalCache(root.resolve("cache-initial"));
        }

        private ProviderNativeRuntimeBinding<TestNativePlan> binding() {
            return new ProviderNativeRuntimeBinding<>(
                    (task, context) -> new TestNativePlan(task.id(), task.providerBindingPin()),
                    runtime);
        }

        private void replaceLocalCache(Path cacheRoot) throws IOException {
            Files.createDirectories(cacheRoot);
            this.materializer = new DirectStorageArtifactMaterializer(
                    authority,
                    Map.of(storage.providerId(), storage),
                    new WorkerLocalMaterializationCache(cacheRoot, 1024 * 1024));
            this.orchestrator = new RuntimeClosedLoopOrchestrator(
                    new ArtifactReuseResolver(index, authority),
                    materializer,
                    new OutputStagingArea(root.resolve("staging")),
                    new ArtifactOutputCommitOrchestrator(
                            authority, Map.of(storage.providerId(), storage), metrics),
                    new FencedReuseCompletionOrchestrator(index, completions),
                    bindings,
                    metrics);
        }

        private void seedSource(String unit, String canonicalDigest) throws IOException {
            ArtifactId id = new ArtifactId("source-" + unit);
            if (authority.getArtifact(TENANT, id).isPresent()) return;
            byte[] bytes = ("source-bytes-" + unit).getBytes(StandardCharsets.UTF_8);
            ContentDigest actual = ContentDigest.sha256(canonicalDigest);
            StorageWriteSession session = storage.beginWrite(
                    "seed-" + unit,
                    namespace(),
                    actual,
                    bytes.length);
            storage.write(session, bytes, 0, bytes.length);
            WriteSessionResult stored = storage.completeWrite(session, actual);
            Artifact artifact = new Artifact(
                    id, TENANT, actual, bytes.length, ArtifactMediaType.VIDEO,
                    ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE,
                    Artifact.CURRENT_SCHEMA_VERSION, NOW);
            authority.addExisting(artifact, new ArtifactReplicaBinding(
                    "seed-binding-" + unit, id, stored.objectId(), stored.replicaId(),
                    storage.providerId(), ReplicaRole.PRIMARY, "local", NOW));
        }

        private RuntimeClosedLoopResult execute(
                ProviderBoundExecutableTaskGraph graph,
                Set<ExecutableTaskId> requested,
                String run) throws IOException {
            Map<ExecutableTaskId, TaskRuntimeExecution> executions = new LinkedHashMap<>();
            Map<ExecutableTaskId, Cacheability> cacheability = new LinkedHashMap<>();
            for (ExecutableTask task : graph.tasks()) {
                bindings.putIfAbsent(task.providerBindingPin(), binding());
                ExecutionAttemptId attempt = new ExecutionAttemptId("attempt-" + task.id().sha256Hex());
                ExecutionOwnershipGeneration generation = ExecutionOwnershipGeneration.first();
                RemoteProviderExecutionHandle handle = RemoteProviderExecutionHandle.forRemoteExecution(
                        attempt, generation, new RemoteExecutionId("remote-" + task.id().sha256Hex()));
                CompletionEvidence completion = new CompletionEvidence(
                        new CompletionEventId("completion-" + task.id().sha256Hex()),
                        handle,
                        task.id(),
                        ObservedExecutionState.SUCCEEDED,
                        new ExpectedOutputValidation(
                                "validation-" + task.id().sha256Hex(),
                                ExpectedOutputValidation.Status.VALID));
                completionState.current.put(task.id(), new Owner(attempt,
                        forceStaleOwner ? generation.next() : generation));
                executions.put(task.id(), new TaskRuntimeExecution(
                        new RuntimeExecutionContext(
                                task.id(), task.providerBindingPin(), attempt, generation),
                        completion,
                        new DurableOutputTarget(
                                storage.providerId(), namespace(),
                                "write-" + run + "-" + task.id().sha256Hex()),
                        new ArtifactCommitMetadata(
                                new ArtifactId("output-" + run + "-" + task.id().sha256Hex()),
                                TENANT,
                                ArtifactMediaType.VIDEO,
                                ArtifactKind.RENDER_MASTER,
                                Artifact.CURRENT_SCHEMA_VERSION,
                                ReplicaRole.PRIMARY,
                                "local",
                                List.of(), NOW, NOW, null, null)));
                cacheability.put(task.id(), Cacheability.CACHEABLE);
            }
            // Bindings may have been added after the current immutable orchestrator was built.
            this.orchestrator = new RuntimeClosedLoopOrchestrator(
                    new ArtifactReuseResolver(index, authority), materializer,
                    new OutputStagingArea(root.resolve("staging")),
                    new ArtifactOutputCommitOrchestrator(
                            authority, Map.of(storage.providerId(), storage), metrics),
                    new FencedReuseCompletionOrchestrator(index, completions), bindings, metrics);
            return orchestrator.execute(new RuntimeClosedLoopRequest(
                    TENANT, graph, requested, cacheability, executions));
        }

        private static StorageNamespace namespace() {
            return new StorageNamespace(
                    TENANT, "project-phase16", NamespaceClass.DERIVED,
                    RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        }
    }

    private record TestNativePlan(
            ExecutableTaskId executableTaskId,
            ProviderBindingPin providerBindingPin) implements ProviderNativeExecutionPlan {}

    private static final class RecordingRuntimeAdapter implements RuntimeAdapter<TestNativePlan> {
        private final AtomicInteger executions = new AtomicInteger();
        private final List<Integer> inputCounts = new ArrayList<>();

        @Override
        public RuntimeExecutionBundle adapt(
                TestNativePlan plan,
                RuntimeExecutionContext context) {
            plan.requireTaskAndBinding(
                    context.executableTaskId(), context.providerBindingPin(),
                    ProviderNativeFailureCode.RUNTIME_BINDING_MISMATCH);
            ExecutionCommand command = new ExecutionCommand(
                    plan.executableTaskId(), plan.providerBindingPin(),
                    context.platformExecutionAttemptId(), context.platformOwnershipGeneration(),
                    0, ProcessInvocationSpec.of("recording-external-executable", List.of()));
            return new RuntimeExecutionBundle(
                    plan.executableTaskId(), plan.providerBindingPin(),
                    context.platformExecutionAttemptId(), context.platformOwnershipGeneration(),
                    List.of(command));
        }

        @Override
        public synchronized ProviderExecutionOutput execute(
                RuntimeExecutionBundle bundle,
                List<MaterializedArtifact> runtimeLocalInputs) {
            executions.incrementAndGet();
            inputCounts.add(runtimeLocalInputs.size());
            byte[] output = ("provider-output-" + bundle.executableTaskId().sha256Hex())
                    .getBytes(StandardCharsets.UTF_8);
            return new ProviderExecutionOutput(new ByteArrayInputStream(output));
        }

        int executions() { return executions.get(); }
        List<Integer> lastInputCounts() { return List.copyOf(inputCounts); }
    }

    private static final class RecordingArtifactAuthority
            implements ArtifactCommitService, ArtifactQueryService {
        private final InMemoryArtifactCommitService delegate = new InMemoryArtifactCommitService();
        private final Map<ArtifactId, Artifact> artifacts = new HashMap<>();
        private final Map<ArtifactId, List<ArtifactReplicaBinding>> replicas = new HashMap<>();
        private int commits;
        private boolean failCommits;

        @Override
        public synchronized ArtifactCommitResult commit(ArtifactCommitRequest request) {
            if (failCommits) throw new IllegalStateException("artifact commit failure");
            ArtifactCommitResult result = delegate.commit(request);
            commits++;
            addExisting(result.artifact(), result.replicaBinding());
            return result;
        }

        @Override
        public Optional<ArtifactCommitResult> findByIdempotencyKey(
                String tenantId, String idempotencyKey) {
            return delegate.findByIdempotencyKey(tenantId, idempotencyKey);
        }

        synchronized void addExisting(Artifact artifact, ArtifactReplicaBinding binding) {
            artifacts.put(artifact.artifactId(), artifact);
            replicas.put(artifact.artifactId(), List.of(binding));
        }

        synchronized void remove(ArtifactId artifactId) { artifacts.remove(artifactId); }

        synchronized void replaceState(ArtifactId artifactId, ArtifactState state) {
            Artifact current = artifacts.get(artifactId);
            artifacts.put(artifactId, current.withState(state));
        }

        int commitCount() { return commits; }

        @Override
        public synchronized Optional<Artifact> getArtifact(String tenantId, ArtifactId artifactId) {
            Artifact artifact = artifacts.get(artifactId);
            return artifact != null && artifact.tenantId().equals(tenantId)
                    ? Optional.of(artifact) : Optional.empty();
        }

        @Override
        public synchronized List<ArtifactReplicaBinding> listReplicas(
                String tenantId, ArtifactId artifactId) {
            return getArtifact(tenantId, artifactId).isPresent()
                    ? replicas.getOrDefault(artifactId, List.of()) : List.of();
        }

        @Override
        public Optional<ArtifactReplicaBinding> findReplica(
                String tenantId, ArtifactId artifactId, StorageReplicaId replicaId) {
            return listReplicas(tenantId, artifactId).stream()
                    .filter(value -> value.storageReplicaId().equals(replicaId)).findFirst();
        }

        @Override public List<ArtifactId> listParents(String tenantId, ArtifactId artifactId) { return List.of(); }
        @Override public List<ArtifactId> listChildren(String tenantId, ArtifactId artifactId) { return List.of(); }
        @Override public List<ProvenanceEdge> getDirectProvenance(String tenantId, ArtifactId artifactId) { return List.of(); }
        @Override public List<ArtifactId> boundedAncestorTraversal(String tenantId, ArtifactId artifactId, int maxDepth) { return List.of(); }
        @Override public List<ArtifactId> boundedDescendantTraversal(String tenantId, ArtifactId artifactId, int maxDepth) { return List.of(); }
        @Override public List<Artifact> findByContentDigest(String tenantId, ContentDigest digest, int limit) {
            return artifacts.values().stream().filter(value -> value.tenantId().equals(tenantId))
                    .filter(value -> value.contentDigest().matches(digest)).limit(limit).toList();
        }
    }

    private record Owner(
            ExecutionAttemptId attemptId,
            ExecutionOwnershipGeneration generation) {}

    private static final class CompletionState {
        private final Map<ExecutableTaskId, Owner> current = new HashMap<>();
        private final Set<CompletionEventId> completed = new HashSet<>();
    }

    private static final class RecordingCompletionAuthority implements CompletionAuthorityPort {
        private final CompletionState state;
        private int completionCount;

        private RecordingCompletionAuthority(CompletionState state) { this.state = state; }

        @Override
        public synchronized CompletionDecision completeIfCurrent(
                CompletionEvidence evidence,
                ArtifactCommitEvidence artifactCommitEvidence) {
            if (state.completed.contains(evidence.completionEventId())) {
                return CompletionDecision.DUPLICATE_NOOP;
            }
            Owner owner = state.current.get(evidence.expectedExecutableTaskId());
            if (!owner.attemptId().equals(evidence.backendExecutionHandle().executionAttemptId())) {
                return CompletionDecision.STALE_ATTEMPT_REJECTED;
            }
            if (!owner.generation().equals(evidence.backendExecutionHandle().ownershipGeneration())) {
                return CompletionDecision.STALE_GENERATION_REJECTED;
            }
            if (!evidence.expectedOutputValidation().isValid()) {
                return CompletionDecision.EXPECTED_OUTPUT_INVALID_REJECTED;
            }
            state.completed.add(evidence.completionEventId());
            completionCount++;
            return CompletionDecision.COMPLETED;
        }

        int completionCount() { return completionCount; }
    }

    private static final class InMemoryReuseIndex implements ArtifactReuseIndexPort {
        private final CompletionState completions;
        private final Map<String, ReusableArtifactPublication> pending = new HashMap<>();
        private final Map<String, ReusableArtifactPublication> winners = new HashMap<>();
        private boolean suppressLookupOnce;
        private boolean throwLookupOnce;

        private InMemoryReuseIndex(CompletionState completions) { this.completions = completions; }

        @Override
        public synchronized Optional<ReusableArtifactRecord> lookup(
                String tenantId, ExecutionReuseKey key) {
            if (throwLookupOnce) {
                throwLookupOnce = false;
                throw new IllegalStateException("simulated index loss");
            }
            if (suppressLookupOnce) {
                suppressLookupOnce = false;
                return Optional.empty();
            }
            ReusableArtifactPublication winner = winners.get(id(tenantId, key));
            return winner == null ? Optional.empty() : Optional.of(winner.record());
        }

        @Override
        public synchronized ReusePublicationResult stageWinningPublication(
                ReusableArtifactPublication publication) {
            ReusableArtifactRecord record = publication.record();
            Owner owner = completions.current.get(record.executableTaskId());
            if (owner == null || !owner.attemptId().equals(record.executionAttemptId())
                    || !owner.generation().equals(record.ownershipGeneration())) {
                return ReusePublicationResult.STALE_OWNER_REJECTED;
            }
            String id = id(record.tenantId(), record.executionReuseKey());
            ReusableArtifactPublication winner = winners.get(id);
            if (winner != null) {
                return winner.equals(publication)
                        ? ReusePublicationResult.WINNER_IDEMPOTENT
                        : ReusePublicationResult.CONFLICT_REJECTED;
            }
            ReusableArtifactPublication prior = pending.putIfAbsent(id, publication);
            return prior == null ? ReusePublicationResult.STAGED_PENDING
                    : prior.equals(publication) ? ReusePublicationResult.PENDING_IDEMPOTENT
                    : ReusePublicationResult.CONFLICT_REJECTED;
        }

        @Override
        public synchronized ReusePublicationResult activateWinningPublication(
                ReusableArtifactPublication publication,
                CompletionEvidence evidence) {
            if (!completions.completed.contains(evidence.completionEventId())) {
                return ReusePublicationResult.COMPLETION_NOT_AUTHORITATIVE_REJECTED;
            }
            String id = id(publication.record().tenantId(), publication.record().executionReuseKey());
            ReusableArtifactPublication winner = winners.get(id);
            if (winner != null) {
                return winner.equals(publication)
                        ? ReusePublicationResult.WINNER_IDEMPOTENT
                        : ReusePublicationResult.CONFLICT_REJECTED;
            }
            if (!publication.equals(pending.get(id))) {
                return ReusePublicationResult.CONFLICT_REJECTED;
            }
            winners.put(id, publication);
            pending.remove(id);
            return ReusePublicationResult.ACTIVATED_WINNER;
        }

        @Override public synchronized boolean evict(String tenantId, ExecutionReuseKey key) {
            return winners.remove(id(tenantId, key)) != null;
        }
        @Override public synchronized int purgePendingBefore(Instant cutoff) {
            int size = pending.size(); pending.clear(); return size;
        }
        synchronized void clear() { pending.clear(); winners.clear(); }
        synchronized int winnerCount() { return winners.size(); }
        private static String id(String tenant, ExecutionReuseKey key) {
            return tenant + "\u0000" + key.version() + "\u0000" + key.stableDigest();
        }
    }

    private static final class RecordingStorageProvider implements StorageProvider {
        private final StorageProvider delegate;
        private int reads;
        private int aborts;
        private boolean failWrites;

        private RecordingStorageProvider(StorageProvider delegate) { this.delegate = delegate; }
        int reads() { return reads; }
        int aborts() { return aborts; }
        @Override public StorageProviderId providerId() { return delegate.providerId(); }
        @Override public StorageProviderCapabilities capabilities() { return delegate.capabilities(); }
        @Override public StorageWriteSession beginWrite(String id, StorageNamespace namespace, ContentDigest digest, long length) { return delegate.beginWrite(id, namespace, digest, length); }
        @Override public void write(StorageWriteSession session, byte[] data, int offset, int length) {
            if (failWrites) throw new IllegalStateException("storage write failure");
            delegate.write(session, data, offset, length);
        }
        @Override public WriteSessionResult completeWrite(StorageWriteSession session, ContentDigest digest) { return delegate.completeWrite(session, digest); }
        @Override public void abortWrite(StorageWriteSession session) { aborts++; delegate.abortWrite(session); }
        @Override public Optional<InputStream> openRead(StorageReadRequest request) { reads++; return delegate.openRead(request); }
        @Override public Optional<StorageObjectMetadata> stat(StorageObjectId id) { return delegate.stat(id); }
        @Override public StorageReplicaId copy(StorageObjectId source, StorageObjectId target, StorageNamespace namespace) { return delegate.copy(source, target, namespace); }
        @Override public StorageDeletionResult delete(StorageDeletionRequest request) { return delegate.delete(request); }
        @Override public HealthStatus health() { return delegate.health(); }
    }
}
