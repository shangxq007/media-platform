package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.shared.identity.ArtifactId;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Mechanical zero guards for Roadmap #22 Epoch 3 Task F. */
class TaskFArchitectureGuardTest {

    private static final Set<String> TASK_F_MAIN_TYPES = Set.of(
            "BackendExecutionHandle",
            "NativeWorkerBackendExecutionHandle",
            "CueJobId",
            "OpenCueBackendExecutionHandle",
            "RemoteExecutionId",
            "RemoteProviderExecutionHandle",
            "CompletionEventId",
            "ExpectedOutputValidation",
            "ArtifactCommitEvidence",
            "CompletionEvidence",
            "ArtifactCommitEvidencePort",
            "CompletionDecision",
            "CompletionAuthorityPort",
            "CompletionFence",
            "ObservationId",
            "ObservedExecutionState",
            "ProviderDiagnosticReference",
            "ExecutionObservation",
            "ExecutionObservationIngestionPort",
            "ExecutionAttempt",
            "DeliveryIntentId",
            "OutboxDeliveryIntent",
            "DeliveryFlowStage",
            "IntegrationPlanePort",
            "ObserverPlanePort",
            "WebhookIngressNormalizationPort",
            "RemotePollingObserverPort",
            "DurableDeliveryPort",
            "IdempotentObservationConsumerPort",
            "OpenCueFarmBackend",
            "OpenCueBackendEligibilityContract",
            "OpenCueSubmissionLedgerPort",
            "OpenCueObservationMappingContract",
            "OpenCueCompletionMappingContract",
            "RemoteProviderInteractionMode",
            "RemoteProviderBackend",
            "RemoteProviderBackendEligibilityContract",
            "RemoteProviderObservationMappingContract",
            "RemoteProviderTrustBoundary");

    @Test
    void backendSuccessEqualsTaskCompletedCountIsZero() throws Exception {
        String source = source("CompletionFence");
        assertThat(source)
                .contains("backendReportedState() != ObservedExecutionState.SUCCEEDED")
                .contains("expectedOutputValidation().isValid()")
                .contains("committedEvidenceFor(evidence)")
                .contains("completeIfCurrent(evidence, commitEvidence.orElseThrow())");
        assertThat(source.indexOf("committedEvidenceFor(evidence)"))
                .isLessThan(source.indexOf("completeIfCurrent(evidence"));
    }

    @Test
    void executionObservationDirectDbAuthorityCountIsZero() throws Exception {
        String sources = taskFSources();
        assertThat(sources)
                .doesNotContain("org.jooq")
                .doesNotContain("java.sql")
                .doesNotContain("DSLContext")
                .doesNotContain("JdbcTemplate");
        assertThat(Arrays.stream(ExecutionObservationIngestionPort.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .containsExactly("ingest");
    }

    @Test
    void messageQueueExecutionStateAuthorityCountIsZero() {
        assertThat(Arrays.stream(DurableDeliveryPort.DeliveryReceipt.class.getRecordComponents())
                .map(RecordComponent::getType))
                .doesNotContain(
                        ExecutionAttemptState.class,
                        CompletionDecision.class,
                        ExecutableTaskId.class);
        assertThat(Arrays.stream(OutboxDeliveryIntent.class.getRecordComponents())
                .map(RecordComponent::getType))
                .doesNotContain(
                        ExecutionAttemptState.class,
                        CompletionDecision.class,
                        ExecutableTaskId.class);
    }

    @Test
    void serverlessAndCamelExecutionStateAuthorityCountsAreZero() throws Exception {
        assertThat(taskFSources())
                .doesNotContain("Cloudflare")
                .doesNotContain("Lambda")
                .doesNotContain("Camel")
                .doesNotContain("Kafka")
                .doesNotContain("NATS")
                .doesNotContain("RabbitMQ")
                .doesNotContain("Redis")
                .doesNotContain("Temporal");
    }

    @Test
    void openCueAutomaticHostAndRuntimeMappingCountsAreZero() {
        assertRecordComponentsExclude(
                OpenCueFarmBackend.class,
                PhysicalHostId.class,
                PhysicalHostIncarnationId.class,
                WorkerRuntimeId.class,
                WorkerRuntimeIncarnationId.class);
        assertRecordComponentsExclude(
                OpenCueBackendExecutionHandle.class,
                PhysicalHostId.class,
                PhysicalHostIncarnationId.class,
                WorkerRuntimeId.class,
                WorkerRuntimeIncarnationId.class);
    }

    @Test
    void remoteProviderFakeRuntimeAndNativeLeaseCountsAreZero() {
        List<Class<?>> remoteRecords = List.of(
                RemoteProviderBackend.class,
                RemoteProviderExecutionHandle.class,
                RemoteProviderTrustBoundary.IngressContext.class);
        remoteRecords.forEach(type -> assertRecordComponentsExclude(
                type,
                PhysicalHostId.class,
                PhysicalHostIncarnationId.class,
                WorkerRuntimeId.class,
                WorkerRuntimeIncarnationId.class,
                DeviceId.class,
                ReservationId.class,
                LeaseId.class,
                TaskLease.class));
    }

    @Test
    void staleGenerationAuthoritativeCompletionCountIsZero() {
        assertThat(Arrays.stream(CompletionEvidence.class.getRecordComponents())
                .map(RecordComponent::getType))
                .contains(BackendExecutionHandle.class);
        List<Class<?>> fenceTypes = new java.util.ArrayList<>();
        Arrays.stream(BackendExecutionHandle.class.getDeclaredMethods())
                .forEach(method -> fenceTypes.add(method.getReturnType()));
        assertThat(fenceTypes)
                .contains(
                        ExecutionAttemptId.class,
                        ExecutionOwnershipGeneration.class,
                        ExecutionBackend.class);
    }

    @Test
    void backendHandleTaskIdentityAndFakeArtifactIdCountsAreZero() {
        List<Class<?>> handles = List.of(
                NativeWorkerBackendExecutionHandle.class,
                OpenCueBackendExecutionHandle.class,
                RemoteProviderExecutionHandle.class);
        handles.forEach(type -> assertRecordComponentsExclude(type, ExecutableTaskId.class));
        assertRecordComponentsExclude(ArtifactCommitEvidence.class, ArtifactId.class);
        assertThat(ArtifactCommitEvidence.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("authorityEvidenceReference", "committedAt");
    }

    @Test
    void legacyPlatformTaskExecutionAuthorityCountIsZero() throws Exception {
        assertThat(taskFSources()).doesNotContain("PlatformTask");
    }

    private static void assertRecordComponentsExclude(Class<?> type, Class<?>... excluded) {
        assertThat(Arrays.stream(type.getRecordComponents()).map(RecordComponent::getType))
                .doesNotContain(excluded);
    }

    private static String taskFSources() throws Exception {
        StringBuilder sources = new StringBuilder();
        for (String type : TASK_F_MAIN_TYPES.stream().sorted().toList()) {
            sources.append(source(type));
        }
        return sources.toString();
    }

    private static String source(String type) throws Exception {
        return Files.readString(repoRoot().resolve(
                "worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/"
                        + type + ".java"));
    }

    private static Path repoRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root not found");
        }
        return current;
    }
}
