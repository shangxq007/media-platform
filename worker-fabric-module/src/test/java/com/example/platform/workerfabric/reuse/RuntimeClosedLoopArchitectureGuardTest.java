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
        assertThat(source).contains("List<MaterializedArtifact> runtimeLocalInputs");
        assertThat(source).doesNotContain(
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
        assertThat(source).contains(".tag(tagName, tagValue)");
        assertThat(source).doesNotContain(
                "tenantId",
                "artifactId",
                "executionReuseKey",
                "taskId",
                "attemptId",
                "workerId",
                "path",
                "storageObjectId",
                "storageReplicaId");
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
}
