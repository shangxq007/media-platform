package com.example.platform.workerfabric.reuse;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.storage.contract.write.WriteSessionResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RuntimeClosedLoopArchitectureGuardTest {

    private static final Path ROOT = repoRoot();
    private static final Path REUSE = ROOT.resolve(
            "worker-fabric-module/src/main/java/com/example/platform/workerfabric/reuse");
    private static final Path PROVIDER_NATIVE = ROOT.resolve(
            "worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/providernative");
    private static final Path WORKER_FABRIC = ROOT.resolve(
            "worker-fabric-module/src/main/java/com/example/platform/workerfabric");
    private static final Path EXECUTION = ROOT.resolve(
            "media-execution-plan-module/src/main/java/com/example/platform/execution");

    @Test
    void stagedOutputCannotBeCommittedWithoutAuthoritativeDurableBinding() {
        String source = source(REUSE.resolve("ArtifactOutputCommitOrchestrator.java"));
        assertThat(source).contains(
                "provider.beginWrite(",
                "provider.write(",
                "provider.completeWrite(",
                "ArtifactCommitRequest commitRequest = bindRequest(metadata, publication)");
        assertThat(source).doesNotContain(
                "commit(StagedExecutionOutput stagedOutput, ArtifactCommitRequest",
                "commit(stagedOutput, commitRequest)");
        assertThat(Arrays.stream(WriteSessionResult.class.getRecordComponents())
                .map(component -> component.getName()).toList())
                .containsExactly("objectId", "replicaId", "alreadyCommitted", "idempotencyKey");
    }

    @Test
    void providerNativeRuntimeHasOnlyLocalMaterializedInputAndNoStorageOrArtifactAuthority() {
        String source = javaSources(PROVIDER_NATIVE);
        assertThat(source).contains("List<MaterializedExecutionInput> runtimeLocalInputs");
        assertThat(source).doesNotContain(
                "List<MaterializedArtifact> runtimeLocalInputs",
                "StorageProvider",
                "StorageObjectId",
                "StorageReplicaId",
                "ArtifactCommitService",
                "ArtifactReuseIndexPort",
                "org.apache.opendal",
                "software.amazon.awssdk",
                "s3://",
                "r2://");
    }

    @Test
    void typedRuntimeInputContractHasNoCompatibilityUntypedPositionalOrPinDedupEscape() {
        String adapter = source(PROVIDER_NATIVE.resolve("RuntimeAdapter.java"));
        String executor = source(PROVIDER_NATIVE.resolve("RuntimeCommandExecutor.java"));
        String binding = source(PROVIDER_NATIVE.resolve("ProviderNativeRuntimeBinding.java"));
        String closedLoop = source(REUSE.resolve("RuntimeClosedLoopOrchestrator.java"));
        String typedInput = source(REUSE.resolve("MaterializedExecutionInput.java"));
        String combined = adapter + binding + closedLoop;

        assertThat(typedInput).contains(
                "ExecutionInputId inputId",
                "ArtifactPin artifactPin",
                "MaterializedArtifact materializedArtifact");
        assertThat(occurrences(adapter, "ProviderExecutionOutput execute(")).isZero();
        assertThat(occurrences(executor, "ProviderExecutionOutput execute(")).isEqualTo(1);
        assertThat(occurrences(binding, "public ProviderExecutionOutput execute(")).isEqualTo(1);
        assertThat(combined).doesNotContain(
                "List<MaterializedArtifact>",
                "List<Object>",
                "List<Map<",
                "List<Path>",
                "runtimeLocalInputs.get(",
                "inputs.get(0)",
                "inputs.getFirst()",
                "Set<ArtifactPin>",
                "LinkedHashSet<ArtifactPin>");
    }

    @Test
    void workerFabricConsumesOnlyNeutralTaskgraphInputsAndExposedCanonicalInputIdentity() {
        String workerFabric = javaSources(WORKER_FABRIC);
        String dependency = source(EXECUTION.resolve("taskgraph/ExecutableTaskDependency.java"));
        String executionDomain = source(EXECUTION.resolve("domain/package-info.java"));

        assertThat(workerFabric).doesNotContain(
                "execution.planning.ExecutionIoProjection.InputBinding",
                "execution.domain.ExecutionStepId");
        assertThat(dependency).contains("ExecutableInputProjection consumerInput");
        assertThat(dependency).doesNotContain("InputBinding consumerInput");
        assertThat(executionDomain).contains(
                "@org.springframework.modulith.NamedInterface(\"domain\")");
    }

    @Test
    void productionPruningInputIsDerivedOnlyFromTypedValidatedHits() {
        String source = source(REUSE.resolve("RuntimeClosedLoopOrchestrator.java"));
        assertThat(source).contains(
                "decision.outcome() == ValidatedReuseDecision.Outcome.VALIDATED_HIT",
                "validatedHitIds.add(task.id())",
                "DependencyPreservingReusePruner.prune(\n"
                        + "                graph, request.requestedTasks(), Set.copyOf(validatedHitIds))");
        assertThat(source).doesNotContain(
                "validatedReusedTasks",
                "request.reusedTasks",
                "request.validatedHitIds");
    }

    @Test
    void closedLoopCannotBypassDurableCommitOrFencedPublicationOrchestrators() {
        String closedLoop = source(REUSE.resolve("RuntimeClosedLoopOrchestrator.java"));
        assertThat(closedLoop).contains(
                "outputCommitOrchestrator.commit(",
                "completionOrchestrator.complete(");
        assertThat(closedLoop).doesNotContain(
                "ArtifactCommitService",
                ".completeIfCurrent(",
                ".activateWinningPublication(",
                ".stageWinningPublication(");
    }

    @Test
    void metricsExposeOnlyBoundedOutcomeLabels() {
        String source = source(REUSE.resolve("Phase16RuntimeMetrics.java"));
        String dispositions = source(REUSE.resolve("MaterializationDisposition.java"));
        assertThat(source).contains(".tag(tagName, tagValue)");
        assertThat(source).doesNotContain(
                "tenantId",
                "artifactId",
                "executionReuseKey",
                "taskId",
                "inputId",
                "attemptId",
                "generation",
                "providerId",
                "digest",
                "workerId",
                "path",
                "storageObjectId",
                "storageReplicaId");
        assertThat(dispositions).contains(
                "LOCAL_CACHE_HIT",
                "STORAGE_MATERIALIZED",
                "CORRUPTION_RECOVERED",
                "FAILURE");
    }

    @Test
    void outputCardinalityFailsBeforeResolutionWithoutSilentOrMultiOutputRuntime() {
        String closedLoop = stripComments(source(
                REUSE.resolve("RuntimeClosedLoopOrchestrator.java")));
        String task = stripComments(source(
                EXECUTION.resolve("taskgraph/ExecutableTask.java")));
        String providerNative = stripComments(javaSources(PROVIDER_NATIVE));

        assertThat(task).contains(
                "List<ExecutionOutputId> authoritativeOutputIds()",
                "action.phase() != BoundaryAction.Phase.POST_EXECUTION");
        assertThat(closedLoop).contains(
                "validateAuthoritativeOutputCardinality(graph);",
                "ProviderNativeFailureCode.UNSUPPORTED_AUTHORITATIVE_OUTPUT_CARDINALITY");
        assertThat(closedLoop.indexOf("validateAuthoritativeOutputCardinality(graph);"))
                .isLessThan(closedLoop.indexOf("ExecutionReuseKeyDeriver.derive(graph)"));
        assertThat(closedLoop).doesNotContain(
                "authoritativeOutputIds().getFirst()",
                "authoritativeOutputIds().get(0)",
                "typedOutputs().getFirst()",
                "outputMapping().getFirst()");
        assertThat(providerNative + closedLoop).doesNotContain(
                "ProviderExecutionOutputs",
                "List<ProviderExecutionOutput>",
                "Collection<ProviderExecutionOutput>");
    }

    @Test
    void postgresFirstPublicationUsesConstraintConflictWithoutGenericCasOrExceptionCatch() {
        String index = stripComments(source(WORKER_FABRIC.resolve(
                "infrastructure/JooqArtifactReuseIndex.java")));

        assertThat(index).contains(
                "on conflict (tenant_id, reuse_key_version, reuse_key_digest) do nothing",
                "Record existing = lockKey(tx, candidate)",
                "ReusePublicationResult.PENDING_IDEMPOTENT",
                "ReusePublicationResult.WINNER_IDEMPOTENT");
        assertThat(index).doesNotContain(
                "catch (RuntimeException",
                "compareAndSet",
                "compareAndSwap",
                "AtomicReference",
                "StampedLock",
                "AdvisoryLock");
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("repository root not found");
        return current;
    }

    private static String javaSources(Path root) {
        try (var stream = Files.walk(root)) {
            return stream.filter(path -> path.toString().endsWith(".java"))
                    .sorted().map(RuntimeClosedLoopArchitectureGuardTest::source)
                    .reduce("", (left, right) -> left + "\n" + right);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String source(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static int occurrences(String source, String value) {
        return (source.length() - source.replace(value, "").length()) / value.length();
    }

    private static String stripComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("(?m)//.*$", " ");
    }
}
