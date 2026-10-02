package com.example.platform.runtime.mediatask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.execution.binding.ProviderBindingEntryService;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.domain.ExecutionPlanSchemaVersion;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersion;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.execution.domain.provider.ProviderExecutionContractSchemaVersion;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import com.example.platform.execution.domain.provider.ProviderId;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.execution.domain.provider.ProviderVersion;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.planning.PhysicalExecutionPlanDigest;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.namespace.DataClassification;
import com.example.platform.storage.contract.namespace.NamespaceClass;
import com.example.platform.storage.contract.namespace.RegionPolicy;
import com.example.platform.storage.contract.namespace.StorageNamespace;
import com.example.platform.workerfabric.domain.AssignmentGrant;
import com.example.platform.workerfabric.domain.AtomicAssignmentGrantBoundary;
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
import com.example.platform.workerfabric.reuse.ArtifactCommitMetadata;
import com.example.platform.workerfabric.reuse.DurableOutputTarget;
import com.example.platform.workerfabric.reuse.RuntimeClosedLoopOrchestrator;
import com.example.platform.workerfabric.reuse.RuntimeClosedLoopRequest;
import com.example.platform.workerfabric.reuse.RuntimeClosedLoopResult;
import com.example.platform.workerfabric.reuse.TaskRuntimeExecutionConstructionException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * P2-5b-2a-2b: {@link MediaTaskActivity#executePreparedGraph} builds one runtime execution per task
 * from the current grant plus the caller's publication intent, derives cacheability, and drives one
 * whole-graph orchestrator call. Failures propagate and are never swallowed.
 */
class MediaTaskActivityExecutionTest {

    private static final String TENANT = "tenant-2a2b";
    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    @Test
    void buildsOneRuntimeExecutionPerTaskAndDrivesTheOrchestrator() throws Exception {
        PreparedTask prepared = preparedTask();
        ExecutableTask task = prepared.executableTaskGraph().tasks().getFirst();
        RuntimeClosedLoopResult canned = mock(RuntimeClosedLoopResult.class);
        RuntimeClosedLoopOrchestrator orchestrator = mock(RuntimeClosedLoopOrchestrator.class);
        when(orchestrator.execute(any())).thenReturn(canned);

        RuntimeClosedLoopResult result = activity().executePreparedGraph(
                TENANT,
                prepared,
                Map.of(task.id(), grant(task.id())),
                Map.of(task.id(), durableOutputTarget()),
                Map.of(task.id(), artifactCommitMetadata()),
                orchestrator);

        assertThat(result).isSameAs(canned);
        RuntimeClosedLoopRequest request = capturedRequest(orchestrator);
        assertThat(request.tenantId()).isEqualTo(TENANT);
        assertThat(request.graph()).isSameAs(prepared.executableTaskGraph());
        assertThat(request.requestedTasks()).containsExactly(task.id());
        assertThat(request.cacheability()).containsOnlyKeys(task.id());
        assertThat(request.taskExecutions()).containsOnlyKeys(task.id());
        var execution = request.taskExecutions().get(task.id());
        assertThat(execution.runtimeContext().executableTaskId()).isEqualTo(task.id());
        assertThat(execution.runtimeContext().providerBindingPin())
                .isEqualTo(task.providerBindingPin());
        assertThat(execution.durableOutputTarget()).isNotNull();
        assertThat(execution.artifactCommitMetadata().artifactId())
                .isEqualTo(new ArtifactId("artifact-output"));
    }

    @Test
    void failsClosedWhenATaskHasNoCurrentGrant() throws Exception {
        PreparedTask prepared = preparedTask();
        ExecutableTask task = prepared.executableTaskGraph().tasks().getFirst();
        RuntimeClosedLoopOrchestrator orchestrator = mock(RuntimeClosedLoopOrchestrator.class);

        assertThatThrownBy(() -> activity().executePreparedGraph(
                TENANT,
                prepared,
                Map.of(),
                Map.of(task.id(), durableOutputTarget()),
                Map.of(task.id(), artifactCommitMetadata()),
                orchestrator))
                .isInstanceOf(TaskRuntimeExecutionConstructionException.class)
                .hasMessageContaining("GRANT_ABSENT");
        verifyNoInteractions(orchestrator);
    }

    @Test
    void failsClosedWhenThePublicationIntentIsIncomplete() throws Exception {
        PreparedTask prepared = preparedTask();
        ExecutableTask task = prepared.executableTaskGraph().tasks().getFirst();
        RuntimeClosedLoopOrchestrator orchestrator = mock(RuntimeClosedLoopOrchestrator.class);
        Map<ExecutableTaskId, AssignmentGrant> grants = Map.of(task.id(), grant(task.id()));

        assertThatThrownBy(() -> activity().executePreparedGraph(
                TENANT, prepared, grants, Map.of(), Map.of(task.id(), artifactCommitMetadata()),
                orchestrator))
                .isInstanceOf(TaskRuntimeExecutionConstructionException.class)
                .hasMessageContaining("DURABLE_OUTPUT_TARGET_ABSENT");
        assertThatThrownBy(() -> activity().executePreparedGraph(
                TENANT, prepared, grants, Map.of(task.id(), durableOutputTarget()), Map.of(),
                orchestrator))
                .isInstanceOf(TaskRuntimeExecutionConstructionException.class)
                .hasMessageContaining("ARTIFACT_COMMIT_METADATA_ABSENT");
        verifyNoInteractions(orchestrator);
    }

    @Test
    void propagatesOrchestratorFailureUntouched() throws Exception {
        PreparedTask prepared = preparedTask();
        ExecutableTask task = prepared.executableTaskGraph().tasks().getFirst();
        RuntimeClosedLoopOrchestrator orchestrator = mock(RuntimeClosedLoopOrchestrator.class);
        java.io.IOException failure = new java.io.IOException("provider output unavailable");
        when(orchestrator.execute(any())).thenThrow(failure);

        assertThatThrownBy(() -> activity().executePreparedGraph(
                TENANT,
                prepared,
                Map.of(task.id(), grant(task.id())),
                Map.of(task.id(), durableOutputTarget()),
                Map.of(task.id(), artifactCommitMetadata()),
                orchestrator))
                .isSameAs(failure);
    }

    @Test
    void rejectsABlankTenant() throws Exception {
        PreparedTask prepared = preparedTask();
        ExecutableTask task = prepared.executableTaskGraph().tasks().getFirst();
        RuntimeClosedLoopOrchestrator orchestrator = mock(RuntimeClosedLoopOrchestrator.class);

        assertThatThrownBy(() -> activity().executePreparedGraph(
                " ",
                prepared,
                Map.of(task.id(), grant(task.id())),
                Map.of(task.id(), durableOutputTarget()),
                Map.of(task.id(), artifactCommitMetadata()),
                orchestrator))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(orchestrator);
    }

    private static MediaTaskActivity activity() {
        return new MediaTaskActivity(
                new UnusedBoundGraphInputStore(),
                mock(AtomicAssignmentGrantBoundary.class),
                mock(RuntimeClosedLoopOrchestrator.class),
                new MediaTaskPublicationSettings(
                        "project-1", "provider-1", "local",
                        ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER,
                        java.time.Clock.systemUTC()));
    }

    /** TEST-ONLY store double: the execution half is always called with an already-prepared graph. */
    private static final class UnusedBoundGraphInputStore
            implements com.example.platform.execution.binding.BoundGraphInputStore {

        @Override
        public com.example.platform.execution.binding.BoundGraphReference save(
                BoundGraphInputs inputs, String tenantId, String renderJobId) {
            throw new UnsupportedOperationException("read-only test double");
        }

        @Override
        public BoundGraphInputs load(
                com.example.platform.execution.binding.BoundGraphReference reference) {
            throw new UnsupportedOperationException("the execution half never loads the store");
        }
    }

    private static RuntimeClosedLoopRequest capturedRequest(
            RuntimeClosedLoopOrchestrator orchestrator) throws Exception {
        ArgumentCaptor<RuntimeClosedLoopRequest> captor =
                ArgumentCaptor.forClass(RuntimeClosedLoopRequest.class);
        org.mockito.Mockito.verify(orchestrator).execute(captor.capture());
        return captor.getValue();
    }

    // ---------- TEST-ONLY fixtures ----------

    private static PreparedTask preparedTask() {
        PhysicalExecutionPlan plan = plan();
        ProviderCandidate candidate = candidate();
        ProviderBoundExecutableTaskGraph graph = new ProviderBindingEntryService()
                .bind(plan, List.of(candidate), List.of())
                .executableTaskGraph();
        return new PreparedTask(
                graph,
                new BoundGraphInputs(plan, List.of(candidate), List.of(), graph.digest()),
                graph.digest());
    }

    private static PhysicalExecutionPlan plan() {
        PhysicalPlanUnit unit = new PhysicalPlanUnit(
                new ExecutionStepId("unit-2a2b"),
                "logical-unit-2a2b",
                new RenderNodeId("render-unit-2a2b"),
                new RenderNodeKind.Decode(),
                "decode",
                List.of(),
                List.of(new OutputDeclaration(
                        new ExecutionOutputId("output-1"),
                        "logical-unit-2a2b",
                        new RenderNodeId("render-unit-2a2b"),
                        List.of(), List.of(), List.of(), List.of())),
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                null,
                true);
        return new PhysicalExecutionPlan(
                "1",
                new ExecutionPlanId("plan-2a2b"),
                ExecutionPlanSchemaVersion.V1,
                new RenderPlanFingerprint("fingerprint-2a2b"),
                List.of(unit),
                null,
                new PhysicalExecutionPlanDigest("digest-2a2b"));
    }

    private static ProviderCandidate candidate() {
        ProviderId providerId = ProviderId.of("provider-2a2b");
        ProviderImplementationId implementationId = ProviderImplementationId.of("provider-2a2b.native");
        ProviderVersion version = ProviderVersion.of("1.0.0");
        ProviderExecutionContractVersion contractVersion = ProviderExecutionContractVersion.of(1, 0);
        ProviderCapabilityProfileVersionOrDigest profileReference =
                ProviderCapabilityProfileVersionOrDigest.version(
                        ProviderCapabilityProfileVersion.of(1, 0));
        ProviderBindingPin binding = new ProviderBindingPin(
                providerId, implementationId, version, contractVersion, profileReference, List.of());
        return new ProviderCandidate(
                binding,
                new ProviderDescriptor(
                        providerId, implementationId, version, contractVersion, profileReference),
                new ProviderExecutionContract(
                        ProviderExecutionContractSchemaVersion.of(1), contractVersion, List.of()),
                new ProviderCapabilityProfile(profileReference, List.of()),
                new ProviderStaticCompatibility(
                        ProviderStaticCompatibility.Knowledge.DECLARED,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        ProviderStaticCompatibility.LoweringSupport.SUPPORTED));
    }

    private static AssignmentGrant grant(ExecutableTaskId taskId) {
        PhysicalHostId hostId = PhysicalHostId.of("host-2a2b");
        PhysicalHostIncarnationId hostIncarnation = PhysicalHostIncarnationId.of("host-inc-2a2b");
        WorkerRuntimeId runtimeId = WorkerRuntimeId.of("runtime-2a2b");
        WorkerRuntimeIncarnationId runtimeIncarnation =
                WorkerRuntimeIncarnationId.of("runtime-inc-2a2b");
        ExecutionAssignmentId assignmentId = ExecutionAssignmentId.of("assignment-2a2b");
        ReservationId reservationId = ReservationId.of("reservation-2a2b");
        ExecutionAttemptId attemptId = new ExecutionAttemptId("attempt-2a2b");
        ExecutionOwnershipGeneration generation = ExecutionOwnershipGeneration.first();
        ExecutionAssignment assignment = new ExecutionAssignment(
                assignmentId, taskId, attemptId, generation, runtimeId, runtimeIncarnation,
                hostId, hostIncarnation, Set.of(), Set.of(reservationId));
        Reservation reservation = new Reservation(
                reservationId, hostId, ReservationKind.TASK, ReservedResources.none(),
                ReservationState.ACTIVE);
        TaskLease lease = new TaskLease(
                LeaseId.of("lease-2a2b"), taskId, assignmentId, attemptId, generation,
                runtimeId, runtimeIncarnation, Set.of(reservationId),
                NOW.plusSeconds(60), NOW, LeaseRenewalContract.NATIVE_PULL_V1,
                new LeaseFencingToken("fence-2a2b"));
        ExecutionAttempt attempt = new ExecutionAttempt(
                attemptId, taskId, generation, ExecutionBackend.NATIVE_PULL_WORKER,
                ExecutionAttemptState.CREATED, Optional.empty());
        return new AssignmentGrant(
                RequestWorkId.of("request-2a2b"), assignment, List.of(reservation), lease, attempt);
    }

    private static DurableOutputTarget durableOutputTarget() {
        return new DurableOutputTarget(
                new StorageProviderId("phase2a2b-storage"),
                new StorageNamespace(
                        TENANT, "project-2a2b", NamespaceClass.DERIVED,
                        RegionPolicy.SINGLE_REGION, DataClassification.INTERNAL),
                "write-session-2a2b");
    }

    private static ArtifactCommitMetadata artifactCommitMetadata() {
        return new ArtifactCommitMetadata(
                new ArtifactId("artifact-output"), TENANT,
                ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER, 1,
                ReplicaRole.PRIMARY, "local", List.of(), NOW, NOW, "render-job-2a2b",
                "project-2a2b");
    }
}
