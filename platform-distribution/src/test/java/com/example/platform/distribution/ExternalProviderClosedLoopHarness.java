package com.example.platform.distribution;

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
import com.example.platform.execution.compatibility.CompatibilityRequest;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderFeasibilityView;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.BoundaryContractId;
import com.example.platform.execution.composition.ExecutableTaskMembership;
import com.example.platform.execution.composition.ProviderCompositionDeclaration;
import com.example.platform.execution.composition.ProviderLocalCompositionEvaluator;
import com.example.platform.execution.composition.ProviderLocalCompositionRequest;
import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.domain.ExecutionPlanSchemaVersion;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.planning.PhysicalExecutionPlanDigest;
import com.example.platform.execution.taskgraph.BoundaryAction;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutionReuseKey;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import com.example.platform.render.domain.renderplan.LogicalArtifactId;
import com.example.platform.render.domain.renderplan.RenderArtifactReference.IntermediateArtifactExpectation;
import com.example.platform.render.domain.renderplan.RenderArtifactReference.SourceArtifact;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.providerplugin.ProviderPluginContribution;
import com.example.platform.providerplugin.ProviderPluginRuntimeContext;
import com.example.platform.sandbox.SandboxCancellation;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import com.example.platform.storage.contract.memory.InMemoryStorageProvider;
import com.example.platform.storage.contract.namespace.DataClassification;
import com.example.platform.storage.contract.namespace.NamespaceClass;
import com.example.platform.storage.contract.namespace.RegionPolicy;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import com.example.platform.storage.contract.provider.StorageProviderCapabilities;
import com.example.platform.storage.contract.read.IntegrityRequirement;
import com.example.platform.storage.contract.read.StorageReadRequest;
import com.example.platform.storage.contract.write.StorageWriteSession;
import com.example.platform.storage.contract.write.WriteSessionResult;
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
import com.example.platform.workerfabric.domain.providernative.ProviderNativeExecutionFailure;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeFailureCode;
import com.example.platform.workerfabric.domain.providernative.ProviderNativeRuntimeBinding;
import com.example.platform.workerfabric.domain.providernative.RuntimeExecutionContext;
import com.example.platform.workerfabric.reuse.ArtifactCommitMetadata;
import com.example.platform.workerfabric.reuse.ArtifactOutputCommitOrchestrator;
import com.example.platform.workerfabric.reuse.ArtifactReuseIndexPort;
import com.example.platform.workerfabric.reuse.ArtifactReuseResolver;
import com.example.platform.workerfabric.reuse.Cacheability;
import com.example.platform.workerfabric.reuse.DirectStorageArtifactMaterializer;
import com.example.platform.workerfabric.reuse.DurableOutputTarget;
import com.example.platform.workerfabric.reuse.FencedReuseCompletionOrchestrator;
import com.example.platform.workerfabric.reuse.OutputStagingArea;
import com.example.platform.workerfabric.reuse.Phase16RuntimeMetrics;
import com.example.platform.workerfabric.reuse.ReusableArtifactPublication;
import com.example.platform.workerfabric.reuse.ReusableArtifactRecord;
import com.example.platform.workerfabric.reuse.ReusePublicationResult;
import com.example.platform.workerfabric.reuse.RuntimeClosedLoopOrchestrator;
import com.example.platform.workerfabric.reuse.RuntimeClosedLoopRequest;
import com.example.platform.workerfabric.reuse.RuntimeClosedLoopResult;
import com.example.platform.workerfabric.reuse.TaskRuntimeExecution;
import com.example.platform.workerfabric.reuse.WorkerLocalMaterializationCache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ExternalProviderClosedLoopHarness {

    private static final String TENANT = "tenant-distribution";
    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");
    private static final BoundaryContractId DIRECT_CONTRACT =
            BoundaryContractId.of("phase19-external-plugin-direct.v1");

    private ExternalProviderClosedLoopHarness() {}

    static Outcome success(
            ProviderPluginContribution contribution,
            Path executable,
            Path root,
            byte[] source,
            String run) throws Exception {
        Harness harness = new Harness(root);
        ProviderBoundExecutableTaskGraph graph = harness.seedSource(source, contribution);
        ProviderNativeRuntimeBinding<?> binding = contribution.createRuntimeBinding(
                context(executable, harness.workspace, false));
        RuntimeClosedLoopResult result = harness.execute(graph, binding, run);
        var committed = result.executedTaskResults().values().iterator().next()
                .durableArtifactCommit();
        byte[] output = harness.storage.openRead(new StorageReadRequest(
                        committed.storagePublication().objectId(), Optional.empty(),
                        IntegrityRequirement.VERIFY_DIGEST))
                .orElseThrow().readAllBytes();
        return new Outcome(
                output,
                committed.artifactCommitResult().artifact().contentDigest(),
                harness.authority.commitCount,
                harness.completionCount,
                harness.index.winners.size());
    }

    static FailureOutcome failure(
            ProviderPluginContribution contribution,
            Path executable,
            Path root,
            byte[] source,
            boolean cancelled,
            String run) throws Exception {
        Harness harness = new Harness(root);
        ProviderBoundExecutableTaskGraph graph = harness.seedSource(source, contribution);
        ProviderNativeRuntimeBinding<?> binding = contribution.createRuntimeBinding(
                context(executable, harness.workspace, cancelled));
        ProviderNativeFailureCode code;
        try {
            harness.execute(graph, binding, run);
            throw new AssertionError("provider execution unexpectedly succeeded");
        } catch (ProviderNativeExecutionFailure expected) {
            code = expected.code();
        }
        return new FailureOutcome(
                code, harness.authority.commitCount, harness.completionCount,
                harness.index.winners.size());
    }

    private static ProviderPluginRuntimeContext context(
            Path executable, Path workspace, boolean cancelled) {
        SandboxCancellation cancellation = cancelled ? () -> true : SandboxCancellation.never();
        return new ProviderPluginRuntimeContext(
                executable, workspace, Duration.ofSeconds(20), 8 * 1024 * 1024, cancellation);
    }

    record Outcome(
            byte[] outputBytes,
            ContentDigest contentDigest,
            int commitCount,
            int completionCount,
            int publicationCount) {}

    record FailureOutcome(
            ProviderNativeFailureCode code,
            int commitCount,
            int completionCount,
            int publicationCount) {}

    private static final class Harness {
        private final Path root;
        private final Path workspace;
        private final InMemoryStorageProvider storage;
        private final Authority authority = new Authority();
        private final ReuseIndex index = new ReuseIndex();
        private int completionCount;

        private Harness(Path root) throws IOException {
            this.root = root.toAbsolutePath().normalize();
            this.workspace = Files.createDirectories(this.root.resolve("runtime-workspace"));
            StorageProviderId storageId = new StorageProviderId("distribution-storage");
            this.storage = new InMemoryStorageProvider(
                    storageId, new StorageProviderCapabilities(storageId, Map.of()));
        }

        private ProviderBoundExecutableTaskGraph seedSource(
                byte[] bytes, ProviderPluginContribution contribution) throws Exception {
            ContentDigest digest = digest(bytes);
            StorageWriteSession session = storage.beginWrite(
                    "seed-source", namespace(), digest, bytes.length);
            storage.write(session, bytes, 0, bytes.length);
            WriteSessionResult stored = storage.completeWrite(session, digest);
            Artifact source = new Artifact(
                    new ArtifactId("source-media"), TENANT, digest, bytes.length,
                    ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE,
                    Artifact.CURRENT_SCHEMA_VERSION, NOW);
            authority.add(source, new ArtifactReplicaBinding(
                    "seed-binding", source.artifactId(), stored.objectId(), stored.replicaId(),
                    storage.providerId(), ReplicaRole.PRIMARY, "local", NOW));
            return graph(digest.canonicalValue(), contribution);
        }

        private RuntimeClosedLoopResult execute(
                ProviderBoundExecutableTaskGraph graph,
                ProviderNativeRuntimeBinding<?> binding,
                String run) throws IOException {
            ExecutableTask task = graph.tasks().getFirst();
            ExecutionAttemptId attempt = ExecutionAttemptId.of("attempt-" + run);
            ExecutionOwnershipGeneration generation = ExecutionOwnershipGeneration.first();
            CompletionEvidence completion = new CompletionEvidence(
                    new CompletionEventId("completion-" + run),
                    RemoteProviderExecutionHandle.forRemoteExecution(
                            attempt, generation, new RemoteExecutionId("runtime-" + run)),
                    task.id(), ObservedExecutionState.SUCCEEDED,
                    new ExpectedOutputValidation(
                            "validation-" + run, ExpectedOutputValidation.Status.VALID));
            TaskRuntimeExecution execution = new TaskRuntimeExecution(
                    new RuntimeExecutionContext(task.id(), task.providerBindingPin(), attempt, generation),
                    completion,
                    new DurableOutputTarget(storage.providerId(), namespace(), "write-" + run),
                    new ArtifactCommitMetadata(
                            new ArtifactId("output-" + run), TENANT,
                            ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER,
                            Artifact.CURRENT_SCHEMA_VERSION, ReplicaRole.PRIMARY, "local",
                            List.of(), NOW, NOW, null, null));
            var metrics = new Phase16RuntimeMetrics(new SimpleMeterRegistry());
            var orchestrator = new RuntimeClosedLoopOrchestrator(
                    new ArtifactReuseResolver(index, authority),
                    new DirectStorageArtifactMaterializer(
                            authority, Map.of(storage.providerId(), storage),
                            new WorkerLocalMaterializationCache(
                                    Files.createDirectories(root.resolve("materialization-cache")),
                                    16 * 1024 * 1024)),
                    new OutputStagingArea(root.resolve("staging")),
                    new ArtifactOutputCommitOrchestrator(
                            authority, Map.of(storage.providerId(), storage), metrics),
                    new FencedReuseCompletionOrchestrator(index, completionAuthority()),
                    Map.of(contributionPin(graph), binding),
                    metrics);
            return orchestrator.execute(new RuntimeClosedLoopRequest(
                    TENANT, graph, Set.of(task.id()),
                    Map.of(task.id(), Cacheability.CACHEABLE),
                    Map.of(task.id(), execution)));
        }

        private static com.example.platform.execution.domain.provider.ProviderBindingPin contributionPin(
                ProviderBoundExecutableTaskGraph graph) {
            return graph.tasks().getFirst().providerBindingPin();
        }

        private CompletionAuthorityPort completionAuthority() {
            return (evidence, artifactCommitEvidence) -> {
                completionCount++;
                return CompletionDecision.COMPLETED;
            };
        }

        private static StorageNamespace namespace() {
            return new StorageNamespace(
                    TENANT, "distribution-project", NamespaceClass.DERIVED,
                    RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        }
    }

    private static ProviderBoundExecutableTaskGraph graph(
            String sourceDigest, ProviderPluginContribution contribution) {
        PhysicalPlanUnit unit = new PhysicalPlanUnit(
                new ExecutionStepId("external-transcode"),
                "logical-external-transcode",
                new RenderNodeId("render-external-transcode"),
                new RenderNodeKind.Decode(),
                "transcode",
                List.of(sourceInput(sourceDigest)),
                List.of(output()),
                List.of(), null, null, List.of(), List.of(), null, true);
        PhysicalExecutionPlan plan = new PhysicalExecutionPlan(
                "1", new ExecutionPlanId("external-provider-plan"),
                ExecutionPlanSchemaVersion.V1,
                new RenderPlanFingerprint("external-provider-fingerprint"),
                List.of(unit), null,
                new PhysicalExecutionPlanDigest("external-provider-plan-digest"));
        ProviderStaticCompatibility staticCompatibility = new ProviderStaticCompatibility(
                ProviderStaticCompatibility.Knowledge.DECLARED,
                List.of(
                        ProviderStaticCompatibility.ArtifactRequirementKind.PINNED_SOURCE_INPUT,
                        ProviderStaticCompatibility.ArtifactRequirementKind.INTERMEDIATE_OUTPUT),
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(DIRECT_CONTRACT),
                ProviderStaticCompatibility.LoweringSupport.SUPPORTED);
        ProviderCandidate candidate = new ProviderCandidate(
                contribution.providerBindingPin(),
                contribution.providerDescriptor(),
                contribution.providerExecutionContract(),
                contribution.providerCapabilityProfile(),
                staticCompatibility);
        ProviderFeasibilityView feasibilityView = ProviderFeasibilityView.build(
                plan, List.of(CompatibilityRequest.forUnit(unit)), List.of(candidate), List.of());
        var membership = ExecutableTaskMembership.canonicalForUnits(List.of(unit));
        var composition = ProviderLocalCompositionEvaluator.evaluate(
                ProviderLocalCompositionRequest.of(
                        membership, feasibilityView, candidate,
                        new ProviderCompositionDeclaration(
                                contribution.providerBindingPin(),
                                ProviderCompositionDeclaration.NativePipelineSupport.SUPPORTED),
                        List.of()));
        OutputDeclaration output = unit.typedOutputs().getFirst();
        ExecutableTask task = ExecutableTask.create(
                composition,
                List.of(new BoundaryAction(
                        BoundaryAction.Phase.POST_EXECUTION, 0,
                        new BoundaryAction.IntermediateArtifactTarget(
                                unit.stepId(), output,
                                output.intermediateArtifactExpectations().getFirst()))));
        return ProviderBoundExecutableTaskGraph.derive(
                plan, feasibilityView, List.of(task), List.of());
    }

    private static InputBinding sourceInput(String digest) {
        return new InputBinding(
                new ExecutionInputId("input-media"),
                "logical-external-transcode",
                new ExecutionStepId("external-transcode"),
                new RenderNodeId("render-external-transcode"),
                null, null, null, null,
                new SourceArtifact(new ArtifactId("source-media"), ContentDigest.sha256(digest)),
                null);
    }

    private static OutputDeclaration output() {
        return new OutputDeclaration(
                new ExecutionOutputId("output-media"),
                "logical-external-transcode",
                new RenderNodeId("render-external-transcode"),
                List.of(), List.of(),
                List.of(new IntermediateArtifactExpectation(
                        new LogicalArtifactId("logical-output-media"),
                        RenderOutputRole.RENDER_MASTER)),
                List.of());
    }

    private static ContentDigest digest(byte[] bytes) throws Exception {
        return ContentDigest.sha256(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)));
    }

    private static final class Authority implements ArtifactCommitService, ArtifactQueryService {
        private final InMemoryArtifactCommitService delegate = new InMemoryArtifactCommitService();
        private final Map<ArtifactId, Artifact> artifacts = new HashMap<>();
        private final Map<ArtifactId, List<ArtifactReplicaBinding>> replicas = new HashMap<>();
        private int commitCount;

        @Override
        public synchronized ArtifactCommitResult commit(ArtifactCommitRequest request) {
            ArtifactCommitResult result = delegate.commit(request);
            commitCount++;
            add(result.artifact(), result.replicaBinding());
            return result;
        }

        @Override
        public Optional<ArtifactCommitResult> findByIdempotencyKey(
                String tenantId, String idempotencyKey) {
            return delegate.findByIdempotencyKey(tenantId, idempotencyKey);
        }

        private synchronized void add(Artifact artifact, ArtifactReplicaBinding binding) {
            artifacts.put(artifact.artifactId(), artifact);
            replicas.put(artifact.artifactId(), List.of(binding));
        }

        @Override
        public synchronized Optional<Artifact> getArtifact(String tenant, ArtifactId id) {
            Artifact value = artifacts.get(id);
            return value != null && value.tenantId().equals(tenant)
                    ? Optional.of(value) : Optional.empty();
        }

        @Override
        public synchronized List<ArtifactReplicaBinding> listReplicas(String tenant, ArtifactId id) {
            return getArtifact(tenant, id).isPresent()
                    ? replicas.getOrDefault(id, List.of()) : List.of();
        }

        @Override
        public Optional<ArtifactReplicaBinding> findReplica(
                String tenant, ArtifactId id, StorageReplicaId replicaId) {
            return listReplicas(tenant, id).stream()
                    .filter(value -> value.storageReplicaId().equals(replicaId)).findFirst();
        }

        @Override public List<ArtifactId> listParents(String tenant, ArtifactId id) { return List.of(); }
        @Override public List<ArtifactId> listChildren(String tenant, ArtifactId id) { return List.of(); }
        @Override public List<ProvenanceEdge> getDirectProvenance(String tenant, ArtifactId id) { return List.of(); }
        @Override public List<ArtifactId> boundedAncestorTraversal(String tenant, ArtifactId id, int depth) { return List.of(); }
        @Override public List<ArtifactId> boundedDescendantTraversal(String tenant, ArtifactId id, int depth) { return List.of(); }

        @Override
        public List<Artifact> findByContentDigest(String tenant, ContentDigest digest, int limit) {
            return artifacts.values().stream()
                    .filter(value -> value.tenantId().equals(tenant))
                    .filter(value -> value.contentDigest().matches(digest))
                    .limit(limit).toList();
        }
    }

    private static final class ReuseIndex implements ArtifactReuseIndexPort {
        private final Map<String, ReusableArtifactPublication> pending = new HashMap<>();
        private final Map<String, ReusableArtifactPublication> winners = new HashMap<>();

        @Override
        public Optional<ReusableArtifactRecord> lookup(String tenant, ExecutionReuseKey key) {
            return Optional.ofNullable(winners.get(id(tenant, key)))
                    .map(ReusableArtifactPublication::record);
        }

        @Override
        public ReusePublicationResult stageWinningPublication(ReusableArtifactPublication publication) {
            pending.put(id(publication.record().tenantId(),
                    publication.record().executionReuseKey()), publication);
            return ReusePublicationResult.STAGED_PENDING;
        }

        @Override
        public ReusePublicationResult activateWinningPublication(
                ReusableArtifactPublication publication, CompletionEvidence evidence) {
            String id = id(publication.record().tenantId(),
                    publication.record().executionReuseKey());
            winners.put(id, publication);
            pending.remove(id);
            return ReusePublicationResult.ACTIVATED_WINNER;
        }

        @Override
        public boolean evict(String tenant, ExecutionReuseKey key) {
            return winners.remove(id(tenant, key)) != null;
        }

        @Override
        public int purgePendingBefore(Instant cutoff) {
            int size = pending.size();
            pending.clear();
            return size;
        }

        private static String id(String tenant, ExecutionReuseKey key) {
            return tenant + "\0" + key.version() + "\0" + key.stableDigest();
        }
    }
}
