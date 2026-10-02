package com.example.platform.workerfabric.reuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersion;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import com.example.platform.execution.domain.provider.ProviderId;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.execution.domain.provider.ProviderVersion;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.namespace.DataClassification;
import com.example.platform.storage.contract.namespace.NamespaceClass;
import com.example.platform.storage.contract.namespace.RegionPolicy;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import com.example.platform.workerfabric.domain.AssignmentGrant;
import com.example.platform.workerfabric.domain.ExecutionAssignment;
import com.example.platform.workerfabric.domain.ExecutionAssignmentId;
import com.example.platform.workerfabric.domain.ExecutionAttempt;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionAttemptState;
import com.example.platform.workerfabric.domain.ExecutionBackend;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import com.example.platform.workerfabric.domain.LeaseFencingToken;
import com.example.platform.workerfabric.domain.LeaseId;
import com.example.platform.workerfabric.domain.LeaseRenewalContract;
import com.example.platform.workerfabric.domain.NativeWorkerBackendExecutionHandle;
import com.example.platform.workerfabric.domain.PhysicalHostId;
import com.example.platform.workerfabric.domain.PhysicalHostIncarnationId;
import com.example.platform.workerfabric.domain.RequestWorkId;
import com.example.platform.workerfabric.domain.Reservation;
import com.example.platform.workerfabric.domain.ReservationId;
import com.example.platform.workerfabric.domain.ReservationKind;
import com.example.platform.workerfabric.domain.ReservationState;
import com.example.platform.workerfabric.domain.ReservedResources;
import com.example.platform.workerfabric.domain.TaskLease;
import com.example.platform.workerfabric.domain.WorkerRuntimeId;
import com.example.platform.workerfabric.domain.WorkerRuntimeIncarnationId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * P2-5b-2a-2b: the runtime execution is built from the durable grant and the caller's publication
 * intent, and refuses a grant that does not bind the exact task.
 */
class TaskRuntimeExecutionFactoryTest {

    private static final ExecutableTaskId TASK_ID = new ExecutableTaskId("a".repeat(64));
    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    @Test
    void buildsTheRuntimeContextFromTheGrantAndPassesThePublicationIntentThrough() {
        ExecutableTask task = task();
        AssignmentGrant grant = grant(TASK_ID, new ExecutionAttemptId("attempt-1"),
                ExecutionOwnershipGeneration.first(), "lease-1");
        DurableOutputTarget target = durableOutputTarget();
        ArtifactCommitMetadata metadata = artifactCommitMetadata();

        TaskRuntimeExecution execution =
                TaskRuntimeExecutionFactory.build(task, grant, target, metadata);

        assertThat(execution.runtimeContext().executableTaskId()).isEqualTo(TASK_ID);
        assertThat(execution.runtimeContext().providerBindingPin()).isEqualTo(task.providerBindingPin());
        assertThat(execution.runtimeContext().platformExecutionAttemptId())
                .isEqualTo(new ExecutionAttemptId("attempt-1"));
        assertThat(execution.runtimeContext().platformOwnershipGeneration())
                .isEqualTo(ExecutionOwnershipGeneration.first());
        assertThat(execution.durableOutputTarget()).isSameAs(target);
        assertThat(execution.artifactCommitMetadata()).isSameAs(metadata);
    }

    @Test
    void completionEvidenceBindsTheExactAttemptGenerationAndLease() {
        AssignmentGrant grant = grant(TASK_ID, new ExecutionAttemptId("attempt-9"),
                new ExecutionOwnershipGeneration(3), "lease-9");

        TaskRuntimeExecution execution = TaskRuntimeExecutionFactory.build(
                task(), grant, durableOutputTarget(), artifactCommitMetadata());

        NativeWorkerBackendExecutionHandle handle =
                (NativeWorkerBackendExecutionHandle) execution.completionEvidence()
                        .backendExecutionHandle();
        assertThat(handle.executionAttemptId()).isEqualTo(new ExecutionAttemptId("attempt-9"));
        assertThat(handle.ownershipGeneration()).isEqualTo(new ExecutionOwnershipGeneration(3));
        assertThat(handle.leaseId()).isEqualTo(LeaseId.of("lease-9"));
        assertThat(execution.completionEvidence().expectedExecutableTaskId()).isEqualTo(TASK_ID);
        assertThat(execution.completionEvidence().expectedOutputValidation().status())
                .isEqualTo(com.example.platform.workerfabric.domain.ExpectedOutputValidation.Status.VALID);
    }

    @Test
    void constructionIsDeterministicForIdenticalInputs() {
        ExecutableTask task = task();
        AssignmentGrant grant = grant(TASK_ID, new ExecutionAttemptId("attempt-1"),
                ExecutionOwnershipGeneration.first(), "lease-1");

        TaskRuntimeExecution first = TaskRuntimeExecutionFactory.build(
                task, grant, durableOutputTarget(), artifactCommitMetadata());
        TaskRuntimeExecution second = TaskRuntimeExecutionFactory.build(
                task, grant, durableOutputTarget(), artifactCommitMetadata());

        assertThat(first).isEqualTo(second);
        assertThat(first.completionEvidence().completionEventId())
                .isEqualTo(second.completionEvidence().completionEventId());
    }

    @Test
    void failsClosedWhenTheGrantedAttemptBelongsToAnotherTask() {
        ExecutableTask other = mock(ExecutableTask.class);
        when(other.id()).thenReturn(new ExecutableTaskId("b".repeat(64)));

        assertThatThrownBy(() -> TaskRuntimeExecutionFactory.build(
                other, grant(TASK_ID, new ExecutionAttemptId("attempt-1"),
                        ExecutionOwnershipGeneration.first(), "lease-1"),
                durableOutputTarget(), artifactCommitMetadata()))
                .isInstanceOf(TaskRuntimeExecutionConstructionException.class)
                .hasMessageContaining("GRANT_ATTEMPT_TASK_MISMATCH");
    }

    @Test
    void anInconsistentGrantCannotEvenExistSoTheLeaseCheckStaysDefensive() {
        AssignmentGrant grant = grant(TASK_ID, new ExecutionAttemptId("attempt-1"),
                ExecutionOwnershipGeneration.first(), "lease-1");
        // Re-point the lease at a different attempt while keeping every other authority intact.
        TaskLease foreignLease = new TaskLease(
                LeaseId.of("lease-1"),
                TASK_ID,
                grant.assignment().id(),
                new ExecutionAttemptId("attempt-other"),
                ExecutionOwnershipGeneration.first(),
                grant.assignment().workerRuntimeId(),
                grant.assignment().workerRuntimeIncarnationId(),
                grant.assignment().reservationIds(),
                NOW.plusSeconds(60),
                NOW,
                LeaseRenewalContract.NATIVE_PULL_V1,
                new LeaseFencingToken("fence-1"));

        // The grant record itself refuses the inconsistency, so the factory's lease check can only
        // ever be reached by a hand-crafted grant.
        assertThatThrownBy(() -> new AssignmentGrant(
                grant.requestWorkId(),
                grant.assignment(),
                grant.reservations(),
                foreignLease,
                grant.attempt()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("grant authorities must bind one task");
    }

    @Test
    void rejectsNullInputs() {
        ExecutableTask task = task();
        AssignmentGrant grant = grant(TASK_ID, new ExecutionAttemptId("attempt-1"),
                ExecutionOwnershipGeneration.first(), "lease-1");

        assertThatThrownBy(() -> TaskRuntimeExecutionFactory.build(
                null, grant, durableOutputTarget(), artifactCommitMetadata()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TaskRuntimeExecutionFactory.build(
                task, null, durableOutputTarget(), artifactCommitMetadata()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TaskRuntimeExecutionFactory.build(
                task, grant, null, artifactCommitMetadata()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TaskRuntimeExecutionFactory.build(
                task, grant, durableOutputTarget(), null))
                .isInstanceOf(NullPointerException.class);
    }

    // ---------- TEST-ONLY fixtures ----------

    private static ExecutableTask task() {
        ExecutableTask task = mock(ExecutableTask.class);
        when(task.id()).thenReturn(TASK_ID);
        when(task.providerBindingPin()).thenReturn(bindingPin());
        return task;
    }

    private static ProviderBindingPin bindingPin() {
        return new ProviderBindingPin(
                ProviderId.of("native-test"),
                ProviderImplementationId.of("native-test.native"),
                ProviderVersion.of("1.0.0"),
                ProviderExecutionContractVersion.of(1, 0),
                ProviderCapabilityProfileVersionOrDigest.version(
                        ProviderCapabilityProfileVersion.of(1, 0)),
                List.of());
    }

    private static AssignmentGrant grant(
            ExecutableTaskId taskId,
            ExecutionAttemptId attemptId,
            ExecutionOwnershipGeneration generation,
            String leaseId) {
        PhysicalHostId hostId = PhysicalHostId.of("host-1");
        PhysicalHostIncarnationId hostIncarnation = PhysicalHostIncarnationId.of("host-inc-1");
        WorkerRuntimeId runtimeId = WorkerRuntimeId.of("runtime-1");
        WorkerRuntimeIncarnationId runtimeIncarnation = WorkerRuntimeIncarnationId.of("runtime-inc-1");
        ExecutionAssignmentId assignmentId = ExecutionAssignmentId.of("assignment-1");
        ReservationId reservationId = ReservationId.of("reservation-1");
        ExecutionAssignment assignment = new ExecutionAssignment(
                assignmentId, taskId, attemptId, generation, runtimeId, runtimeIncarnation,
                hostId, hostIncarnation, Set.of(), Set.of(reservationId));
        Reservation reservation = new Reservation(
                reservationId, hostId, ReservationKind.TASK, ReservedResources.none(),
                ReservationState.ACTIVE);
        TaskLease lease = new TaskLease(
                LeaseId.of(leaseId), taskId, assignmentId, attemptId, generation,
                runtimeId, runtimeIncarnation, Set.of(reservationId),
                NOW.plusSeconds(60), NOW, LeaseRenewalContract.NATIVE_PULL_V1,
                new LeaseFencingToken("fence-1"));
        ExecutionAttempt attempt = new ExecutionAttempt(
                attemptId, taskId, generation, ExecutionBackend.NATIVE_PULL_WORKER,
                ExecutionAttemptState.CREATED, Optional.empty());
        return new AssignmentGrant(
                RequestWorkId.of("request-1"), assignment, List.of(reservation), lease, attempt);
    }

    private static DurableOutputTarget durableOutputTarget() {
        return new DurableOutputTarget(
                new StorageProviderId("phase2a2b-storage"),
                new StorageNamespace(
                        "tenant-1", "project-1", NamespaceClass.DERIVED,
                        RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL),
                "write-session-1");
    }

    private static ArtifactCommitMetadata artifactCommitMetadata() {
        return new ArtifactCommitMetadata(
                new ArtifactId("artifact-output"), "tenant-1",
                ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER, 1,
                ReplicaRole.PRIMARY, "local", List.of(), NOW, NOW, "render-job-1", "project-1");
    }
}
