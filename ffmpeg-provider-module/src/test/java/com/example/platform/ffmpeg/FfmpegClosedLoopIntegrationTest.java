package com.example.platform.ffmpeg;

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
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import com.example.platform.sandbox.SandboxCancellation;
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
import com.example.platform.storage.contract.provider.StorageProviderCapabilities;
import com.example.platform.storage.contract.read.StorageReadRequest;
import com.example.platform.storage.contract.read.IntegrityRequirement;
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
import com.example.platform.workerfabric.domain.ProviderProbeResult;
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
import java.io.InputStream;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FfmpegClosedLoopIntegrationTest {
    private static final String TENANT = "tenant-phase19";
    private static final Instant NOW = Instant.parse("2026-08-27T08:00:00Z");

    @TempDir Path temp;

    @Test
    void real_ffmpeg_stdout_flows_through_staging_platform_artifact_commit_and_completion()
            throws Exception {
        Path ffmpeg = binary("ffmpeg");
        Path ffprobe = binary("ffprobe");
        Path input = generateInput(ffmpeg, temp.resolve("fixture-input.mp4"));
        byte[] sourceBytes = Files.readAllBytes(input);
        Harness harness = new Harness(temp.resolve("success"));
        ProviderBoundExecutableTaskGraph graph = harness.seedSource(sourceBytes);
        ProviderNativeRuntimeBinding<FfmpegCpuTranscodePlan> binding =
                FfmpegCpuRuntimeBindingFactory.create(
                        ffmpeg,
                        FfmpegSandboxWorkspace.under(harness.workspace),
                        Duration.ofSeconds(20),
                        8 * 1024 * 1024,
                        SandboxCancellation.never());

        RuntimeClosedLoopResult result = harness.execute(graph, binding, "success");

        assertThat(result.executedTaskResults()).hasSize(1);
        assertThat(harness.authority.commitCount).isEqualTo(1);
        assertThat(harness.completionCount).isEqualTo(1);
        assertThat(harness.index.winners).hasSize(1);
        var committed = result.executedTaskResults().values().iterator().next()
                .durableArtifactCommit();
        assertThat(committed.artifactCommitResult().artifact().state())
                .isEqualTo(ArtifactState.AVAILABLE);
        byte[] outputBytes = harness.storage.openRead(new StorageReadRequest(
                        committed.storagePublication().objectId(), Optional.empty(),
                        IntegrityRequirement.VERIFY_DIGEST))
                .orElseThrow().readAllBytes();
        Path output = Files.write(temp.resolve("verified-output.mp4"), outputBytes);

        Harness repeatedHarness = new Harness(temp.resolve("repeated-success"));
        ProviderBoundExecutableTaskGraph repeatedGraph = repeatedHarness.seedSource(sourceBytes);
        var repeatedBinding = FfmpegCpuRuntimeBindingFactory.create(
                ffmpeg,
                FfmpegSandboxWorkspace.under(repeatedHarness.workspace),
                Duration.ofSeconds(20),
                8 * 1024 * 1024,
                SandboxCancellation.never());
        var repeated = repeatedHarness.execute(repeatedGraph, repeatedBinding, "repeat")
                .executedTaskResults().values().iterator().next().durableArtifactCommit();
        byte[] repeatedBytes = repeatedHarness.storage.openRead(new StorageReadRequest(
                        repeated.storagePublication().objectId(), Optional.empty(),
                        IntegrityRequirement.VERIFY_DIGEST))
                .orElseThrow().readAllBytes();
        assertThat(repeatedBytes).isEqualTo(outputBytes);
        assertThat(repeated.artifactCommitResult().artifact().contentDigest())
                .isEqualTo(committed.artifactCommitResult().artifact().contentDigest());

        ProbeJson probe = ffprobe(ffprobe, output);
        assertThat(probe.codecName()).isEqualTo("h264");
        assertThat(probe.pixelFormat()).isEqualTo("yuv420p");
        assertThat(probe.width()).isEqualTo(64);
        assertThat(probe.height()).isEqualTo(48);
        assertThat(probe.durationSeconds()).isGreaterThan(0.0);
    }

    @Test
    void bounded_probe_returns_exact_version_build_evidence_without_eligibility_authority() {
        Path ffmpeg = binary("ffmpeg");
        Path workspaceRoot = temp.resolve("probe-workspace").toAbsolutePath().normalize();
        FfmpegRuntimeProbeResult result = FfmpegRuntimeProbe.probe(
                ffmpeg, FfmpegSandboxWorkspace.under(workspaceRoot), Duration.ofSeconds(10));

        assertThat(result.providerProbeResult().status()).isEqualTo(ProviderProbeResult.Status.HEALTHY);
        assertThat(result.runtimeVersion()).isPresent();
        assertThat(result.exactVersionLine()).hasValueSatisfying(
                line -> assertThat(line).startsWith("ffmpeg version "));
        assertThat(result.exactBuildConfigurationLine()).hasValueSatisfying(
                line -> assertThat(line).startsWith("configuration:"));
        assertThat(FfmpegRuntimeProbeResult.class.getRecordComponents())
                .extracting(component -> component.getName())
                .noneMatch(name -> name.matches("(?i).*(capacity|availability|eligible|canRun).*"));
    }

    @Test
    void nonzero_and_cancellation_publish_no_artifact_or_completion() throws Exception {
        Path ffmpeg = binary("ffmpeg");
        Harness invalidHarness = new Harness(temp.resolve("invalid"));
        ProviderBoundExecutableTaskGraph invalidGraph = invalidHarness.seedSource(
                "not-media".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var normalBinding = FfmpegCpuRuntimeBindingFactory.create(
                ffmpeg, FfmpegSandboxWorkspace.under(invalidHarness.workspace),
                Duration.ofSeconds(10), 1024 * 1024, SandboxCancellation.never());

        assertThatThrownBy(() -> invalidHarness.execute(invalidGraph, normalBinding, "invalid"))
                .isInstanceOfSatisfying(ProviderNativeExecutionFailure.class,
                        failure -> assertThat(failure.code())
                                .isEqualTo(ProviderNativeFailureCode.PROCESS_NONZERO_EXIT));
        assertThat(invalidHarness.authority.commitCount).isZero();
        assertThat(invalidHarness.completionCount).isZero();
        assertThat(invalidHarness.index.winners).isEmpty();

        Path input = generateInput(ffmpeg, temp.resolve("cancel-input.mp4"));
        Harness cancelledHarness = new Harness(temp.resolve("cancelled"));
        ProviderBoundExecutableTaskGraph cancelledGraph = cancelledHarness.seedSource(
                Files.readAllBytes(input));
        Set<Long> processesBeforeCancellation = ProcessHandle.allProcesses()
                .filter(ProcessHandle::isAlive)
                .map(ProcessHandle::pid)
                .collect(java.util.stream.Collectors.toSet());
        var cancelledBinding = FfmpegCpuRuntimeBindingFactory.create(
                ffmpeg, FfmpegSandboxWorkspace.under(cancelledHarness.workspace),
                Duration.ofSeconds(10), 8 * 1024 * 1024, () -> true);

        assertThatThrownBy(() -> cancelledHarness.execute(
                cancelledGraph, cancelledBinding, "cancelled"))
                .isInstanceOfSatisfying(ProviderNativeExecutionFailure.class,
                        failure -> assertThat(failure.code())
                                .isEqualTo(ProviderNativeFailureCode.PROCESS_CANCELLED));
        assertThat(cancelledHarness.authority.commitCount).isZero();
        assertThat(cancelledHarness.completionCount).isZero();
        assertThat(cancelledHarness.index.winners).isEmpty();
        assertThat(ProcessHandle.allProcesses()
                .filter(ProcessHandle::isAlive)
                .filter(handle -> !processesBeforeCancellation.contains(handle.pid()))
                .map(handle -> handle.info().command().orElse("") + " "
                        + String.join(" ", handle.info().arguments().orElse(new String[0]))))
                .noneMatch(command -> command.contains("ffmpeg") || command.contains("bwrap"));
    }

    private Path generateInput(Path ffmpeg, Path output) throws Exception {
        Process process = new ProcessBuilder(
                ffmpeg.toString(), "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "testsrc=size=64x48:rate=5",
                "-t", "1", "-c:v", "mpeg4", "-pix_fmt", "yuv420p", output.toString())
                .redirectErrorStream(true)
                .start();
        byte[] diagnostic = process.getInputStream().readAllBytes();
        assertThat(process.waitFor()).as(new String(diagnostic)).isZero();
        return output;
    }

    private static ProbeJson ffprobe(Path ffprobe, Path output) throws Exception {
        Process process = new ProcessBuilder(
                ffprobe.toString(), "-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=codec_name,pix_fmt,width,height:format=duration",
                "-of", "default=noprint_wrappers=1", output.toString())
                .redirectErrorStream(true)
                .start();
        String text = new String(process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as(text).isZero();
        Map<String, String> values = text.lines()
                .filter(line -> line.contains("="))
                .map(line -> line.split("=", 2))
                .collect(java.util.stream.Collectors.toMap(parts -> parts[0], parts -> parts[1]));
        return new ProbeJson(
                values.get("codec_name"), values.get("pix_fmt"),
                Integer.parseInt(values.get("width")), Integer.parseInt(values.get("height")),
                Double.parseDouble(values.get("duration")));
    }

    private static Path binary(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            throw new IllegalStateException("PATH is absent; cannot locate " + name);
        }
        for (String directory : path.split(
                java.util.regex.Pattern.quote(java.io.File.pathSeparator), -1)) {
            if (directory.isBlank()) {
                continue;
            }
            Path candidate = Path.of(directory).resolve(name).toAbsolutePath().normalize();
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(name + " unavailable on PATH");
    }

    private record ProbeJson(
            String codecName, String pixelFormat, int width, int height, double durationSeconds) {}

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
            StorageProviderId storageId = new StorageProviderId("phase19-storage");
            this.storage = new InMemoryStorageProvider(
                    storageId, new StorageProviderCapabilities(storageId, Map.of()));
        }

        private ProviderBoundExecutableTaskGraph seedSource(byte[] bytes) throws Exception {
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
            return FfmpegCanonicalGraphFixture.single(digest.canonicalValue());
        }

        private RuntimeClosedLoopResult execute(
                ProviderBoundExecutableTaskGraph graph,
                ProviderNativeRuntimeBinding<FfmpegCpuTranscodePlan> binding,
                String run) throws IOException {
            ExecutableTask task = graph.tasks().getFirst();
            ExecutionAttemptId attempt = ExecutionAttemptId.of("attempt-" + run);
            ExecutionOwnershipGeneration generation = ExecutionOwnershipGeneration.first();
            CompletionEvidence completion = new CompletionEvidence(
                    new CompletionEventId("completion-" + run),
                    RemoteProviderExecutionHandle.forRemoteExecution(
                            attempt, generation, new RemoteExecutionId("runtime-" + run)),
                    task.id(),
                    ObservedExecutionState.SUCCEEDED,
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
                    Map.of(FfmpegCpuProvider.BINDING, binding),
                    metrics);
            return orchestrator.execute(new RuntimeClosedLoopRequest(
                    TENANT, graph, Set.of(task.id()),
                    Map.of(task.id(), Cacheability.CACHEABLE),
                    Map.of(task.id(), execution)));
        }

        private CompletionAuthorityPort completionAuthority() {
            return (evidence, artifactCommitEvidence) -> {
                completionCount++;
                return CompletionDecision.COMPLETED;
            };
        }

        private static StorageNamespace namespace() {
            return new StorageNamespace(
                    TENANT, "phase19-project", NamespaceClass.DERIVED,
                    RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL);
        }
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

        @Override public synchronized ArtifactCommitResult commit(ArtifactCommitRequest request) {
            ArtifactCommitResult result = delegate.commit(request);
            commitCount++;
            add(result.artifact(), result.replicaBinding());
            return result;
        }
        @Override public Optional<ArtifactCommitResult> findByIdempotencyKey(
                String tenantId, String idempotencyKey) {
            return delegate.findByIdempotencyKey(tenantId, idempotencyKey);
        }
        private synchronized void add(Artifact artifact, ArtifactReplicaBinding binding) {
            artifacts.put(artifact.artifactId(), artifact);
            replicas.put(artifact.artifactId(), List.of(binding));
        }
        @Override public synchronized Optional<Artifact> getArtifact(String tenant, ArtifactId id) {
            Artifact value = artifacts.get(id);
            return value != null && value.tenantId().equals(tenant) ? Optional.of(value) : Optional.empty();
        }
        @Override public synchronized List<ArtifactReplicaBinding> listReplicas(String tenant, ArtifactId id) {
            return getArtifact(tenant, id).isPresent() ? replicas.getOrDefault(id, List.of()) : List.of();
        }
        @Override public Optional<ArtifactReplicaBinding> findReplica(
                String tenant, ArtifactId id, StorageReplicaId replicaId) {
            return listReplicas(tenant, id).stream()
                    .filter(value -> value.storageReplicaId().equals(replicaId)).findFirst();
        }
        @Override public List<ArtifactId> listParents(String tenant, ArtifactId id) { return List.of(); }
        @Override public List<ArtifactId> listChildren(String tenant, ArtifactId id) { return List.of(); }
        @Override public List<ProvenanceEdge> getDirectProvenance(String tenant, ArtifactId id) { return List.of(); }
        @Override public List<ArtifactId> boundedAncestorTraversal(String tenant, ArtifactId id, int depth) { return List.of(); }
        @Override public List<ArtifactId> boundedDescendantTraversal(String tenant, ArtifactId id, int depth) { return List.of(); }
        @Override public List<Artifact> findByContentDigest(String tenant, ContentDigest digest, int limit) {
            return artifacts.values().stream().filter(value -> value.tenantId().equals(tenant))
                    .filter(value -> value.contentDigest().matches(digest)).limit(limit).toList();
        }
    }

    private static final class ReuseIndex implements ArtifactReuseIndexPort {
        private final Map<String, ReusableArtifactPublication> pending = new HashMap<>();
        private final Map<String, ReusableArtifactPublication> winners = new HashMap<>();
        @Override public Optional<ReusableArtifactRecord> lookup(String tenant, ExecutionReuseKey key) {
            return Optional.ofNullable(winners.get(id(tenant, key))).map(ReusableArtifactPublication::record);
        }
        @Override public ReusePublicationResult stageWinningPublication(ReusableArtifactPublication publication) {
            pending.put(id(publication.record().tenantId(), publication.record().executionReuseKey()), publication);
            return ReusePublicationResult.STAGED_PENDING;
        }
        @Override public ReusePublicationResult activateWinningPublication(
                ReusableArtifactPublication publication, CompletionEvidence evidence) {
            String id = id(publication.record().tenantId(), publication.record().executionReuseKey());
            winners.put(id, publication);
            pending.remove(id);
            return ReusePublicationResult.ACTIVATED_WINNER;
        }
        @Override public boolean evict(String tenant, ExecutionReuseKey key) {
            return winners.remove(id(tenant, key)) != null;
        }
        @Override public int purgePendingBefore(Instant cutoff) {
            int size = pending.size(); pending.clear(); return size;
        }
        private static String id(String tenant, ExecutionReuseKey key) {
            return tenant + "\0" + key.version() + "\0" + key.stableDigest();
        }
    }
}
