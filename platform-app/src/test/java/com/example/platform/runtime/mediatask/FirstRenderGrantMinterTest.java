package com.example.platform.runtime.mediatask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import com.example.platform.providerplugin.ProviderPluginContribution;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.workerfabric.domain.AssignmentGrant;
import com.example.platform.workerfabric.domain.AtomicAssignmentGrantBoundary;
import com.example.platform.workerfabric.domain.AtomicAssignmentGrantCommand;
import com.example.platform.workerfabric.domain.CpuArchitecture;
import com.example.platform.workerfabric.domain.ExecutionAssignment;
import com.example.platform.workerfabric.domain.ExecutionAssignmentId;
import com.example.platform.workerfabric.domain.ExecutionAttempt;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionAttemptState;
import com.example.platform.workerfabric.domain.ExecutionBackend;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import com.example.platform.workerfabric.domain.HostLocation;
import com.example.platform.workerfabric.domain.HostResourceSnapshotGeneration;
import com.example.platform.workerfabric.domain.LeaseFencingToken;
import com.example.platform.workerfabric.domain.LeaseId;
import com.example.platform.workerfabric.domain.LeaseRenewalContract;
import com.example.platform.workerfabric.domain.NativePullAdmissionPort;
import com.example.platform.workerfabric.domain.PhysicalHostId;
import com.example.platform.workerfabric.domain.PhysicalHostIncarnationId;
import com.example.platform.workerfabric.domain.ProviderBackendExecutionSupport;
import com.example.platform.workerfabric.domain.ProviderHardwareRequirement;
import com.example.platform.workerfabric.domain.ProviderResourceProfile;
import com.example.platform.workerfabric.domain.RequestWork;
import com.example.platform.workerfabric.domain.RequestWorkFailureReason;
import com.example.platform.workerfabric.domain.RequestWorkId;
import com.example.platform.workerfabric.domain.RequestWorkResult;
import com.example.platform.workerfabric.domain.Reservation;
import com.example.platform.workerfabric.domain.ReservationFeasibility;
import com.example.platform.workerfabric.domain.ReservationId;
import com.example.platform.workerfabric.domain.ReservationKind;
import com.example.platform.workerfabric.domain.ReservationState;
import com.example.platform.workerfabric.domain.ReservedResources;
import com.example.platform.workerfabric.domain.RuntimeEnvironmentAvailability;
import com.example.platform.workerfabric.domain.RuntimeLifecycleKind;
import com.example.platform.workerfabric.domain.RuntimeSupportIdentifier;
import com.example.platform.workerfabric.domain.SandboxRuntimeAvailability;
import com.example.platform.workerfabric.domain.SandboxRuntimeRequirement;
import com.example.platform.workerfabric.domain.TaskLease;
import com.example.platform.workerfabric.domain.TrustZoneId;
import com.example.platform.workerfabric.domain.WorkerFabricRegistrationBoundary;
import com.example.platform.workerfabric.domain.WorkerReadinessEvidence;
import com.example.platform.workerfabric.domain.WorkerRuntimeId;
import com.example.platform.workerfabric.domain.WorkerRuntimeIncarnationId;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportRequirement;
import com.example.platform.workerfabric.domain.CpuArchitecture;
import com.example.platform.workerfabric.domain.ProviderHardwareAvailableEvidence;
import com.example.platform.workerfabric.domain.ProviderHardwareObservation;
import com.example.platform.workerfabric.domain.RuntimeDependencyObservation;
import com.example.platform.workerfabric.domain.RuntimeDependencyProbeSchemaVersion;
import com.example.platform.workerfabric.domain.RuntimeLifecycleKind;
import com.example.platform.workerfabric.domain.RuntimeSupportEvidence;
import com.example.platform.workerfabric.domain.RuntimeSupportIdentifier;
import com.example.platform.workerfabric.domain.WorkerRuntimeSupportAdvertisement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import com.example.platform.workerfabric.domain.PendingNativeWorkCandidate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * P1-1A.4 Gate B: the First Render grant minter consumes the authoritative persisted registration,
 * snapshot and WORKER_READINESS_EVIDENCE_V1 authority, drives the existing Native Pull admission path
 * with the task's real bound graph and exact provider contribution, and only reports a mint after
 * admission granted AND the durable grant was re-read. Every failure path stays fail-closed. No
 * testcontainers are started.
 */
class FirstRenderGrantMinterTest {

    private static final String RENDER_JOB_ID = "rj_0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0";
    private static final Instant NOW = Instant.parse("2026-10-11T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final PhysicalHostId HOST = PhysicalHostId.of("local");
    private static final PhysicalHostIncarnationId HOST_INC =
            PhysicalHostIncarnationId.of("host-inc");
    private static final WorkerRuntimeId RUNTIME = WorkerRuntimeId.of("local-ffmpeg");
    private static final WorkerRuntimeIncarnationId RUNTIME_INC =
            WorkerRuntimeIncarnationId.of("local-ffmpeg-v1");

    // ---------- identity ----------

    @Test
    void evaluationTimestampIsRetakenAfterAdmissionEvidenceIsProduced() {
        // P1-1A.5 evaluation-timestamp-ordering fix: the minter must consult the clock AGAIN after
        // producing the admission evidence, so the eligibility `evaluatedAt` is never earlier than the
        // observations' `observedAt` (which the evidence factory stamps at production time).
        Clock advancing = mock(Clock.class);
        when(advancing.instant()).thenReturn(NOW, NOW.plusMillis(1));
        AdmissionEvidenceProducer evidenceProducer = mock(AdmissionEvidenceProducer.class);
        when(evidenceProducer.produce(any(), any(), any())).thenAnswer(invocation ->
                fullEvidence().produce(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2)));
        FakeGrantBoundary grants = new FakeGrantBoundary();
        FirstRenderGrantMinter minter = new FirstRenderGrantMinter(
                validBoundary(), grants, new NativePullAdmissionPort(grants), evidenceProducer,
                (host, incarnation, generation, demand) -> ReservationFeasibility.FEASIBLE,
                advancing);

        minter.mint(RENDER_JOB_ID, taskId(), graph(), contribution());

        InOrder inOrder = inOrder(evidenceProducer, advancing);
        inOrder.verify(evidenceProducer).produce(any(), any(), any());
        inOrder.verify(advancing, atLeastOnce()).instant();
    }

    @Test
    @SuppressWarnings("unchecked")
    void eachPerTaskMintAdmitsOnlyTheRequestedGraphTask() {
        // P1-1A.7: the second per-task mint must target the requested task, never re-select the
        // already-granted (now non-claimable) first task. Here we capture the candidates the minter
        // hands to the admission port for BOTH graph tasks and assert each mint admits only its own.
        ProviderBoundExecutableTaskGraph graph = twoTaskGraph();
        assertThat(graph.tasks()).hasSize(2);
        ExecutableTaskId firstId = graph.tasks().get(0).id();
        ExecutableTaskId secondId = graph.tasks().get(1).id();

        for (ExecutableTaskId requested : List.of(secondId, firstId)) {
            NativePullAdmissionPort port = mock(NativePullAdmissionPort.class);
            when(port.admit(any(), any(), any())).thenReturn(
                    new RequestWorkResult.NoWork(
                            FirstRenderGrantMinter.requestWorkId(RENDER_JOB_ID, requested)));
            FirstRenderGrantMinter minter = new FirstRenderGrantMinter(
                    validBoundary(), new FakeGrantBoundary(), port, fullEvidence(),
                    (host, incarnation, generation, demand) -> ReservationFeasibility.FEASIBLE,
                    CLOCK);

            minter.mint(RENDER_JOB_ID, requested, graph, contribution());

            ArgumentCaptor<List<PendingNativeWorkCandidate>> candidates =
                    ArgumentCaptor.forClass(List.class);
            verify(port).admit(any(), any(), candidates.capture());
            assertThat(candidates.getValue()).hasSize(1);
            assertThat(candidates.getValue().getFirst().executableTask().id()).isEqualTo(requested);
        }
    }

    @Test
    void mintFailsClosedWhenTheRequestedTaskIsNotInTheBoundGraph() {
        ProviderBoundExecutableTaskGraph graph = graph();
        NativePullAdmissionPort port = mock(NativePullAdmissionPort.class);
        FirstRenderGrantMinter minter = new FirstRenderGrantMinter(
                validBoundary(), new FakeGrantBoundary(), port, fullEvidence(),
                (host, incarnation, generation, demand) -> ReservationFeasibility.FEASIBLE, CLOCK);
        ExecutableTaskId foreign = new ExecutableTaskId("f".repeat(64));

        FirstRenderGrantMinter.Result result =
                minter.mint(RENDER_JOB_ID, foreign, graph, contribution());

        assertThat(result).isInstanceOf(FirstRenderGrantMinter.Result.Unavailable.class);
        assertThat(((FirstRenderGrantMinter.Result.Unavailable) result).missingAuthoritativeInputs())
                .containsExactly("requested-task-not-in-bound-graph");
        verifyNoInteractions(port);
    }

    @Test
    void identityRuleIsStableBoundedAndAcceptedByTheRequestWorkIdContract() {
        var id = FirstRenderGrantMinter.requestWorkId(RENDER_JOB_ID, taskId());
        assertThat(id.value()).isEqualTo("rw:" + RENDER_JOB_ID + ":" + taskId().sha256Hex());
        assertThat(id.value()).hasSizeLessThanOrEqualTo(128);
        assertThat(id).isEqualTo(FirstRenderGrantMinter.requestWorkId(RENDER_JOB_ID, taskId()));
    }

    @Test
    void identityIsUniquePerTaskSoAReplayOfTheSameTaskStaysStable() {
        var other = new ExecutableTaskId("b".repeat(64));
        assertThat(FirstRenderGrantMinter.requestWorkId(RENDER_JOB_ID, taskId()))
                .isNotEqualTo(FirstRenderGrantMinter.requestWorkId(RENDER_JOB_ID, other));
        assertThat(FirstRenderGrantMinter.requestWorkId(RENDER_JOB_ID, other))
                .isEqualTo(FirstRenderGrantMinter.requestWorkId(RENDER_JOB_ID, other));
    }

    // ---------- registration authority ----------

    @Test
    void missingRegistrationFailsClosedAndNamesTheMissingAuthority() {
        var result = minter(StubBoundary.empty(), new FakeGrantBoundary())
                .mint(RENDER_JOB_ID, taskId());

        assertThat(result).isInstanceOf(FirstRenderGrantMinter.Result.Unavailable.class);
        assertThat(((FirstRenderGrantMinter.Result.Unavailable) result).missingAuthoritativeInputs())
                .containsExactly("active-host-registration", "active-runtime-registration");
    }

    @Test
    void expiredHostRegistrationIsRejectedRatherThanMinted() {
        StubBoundary boundary = validBoundary();
        boundary.host = Optional.of(new WorkerFabricRegistrationBoundary.PersistedHostRegistration(
                HOST, HOST_INC, NOW.minus(Duration.ofMinutes(10)), NOW.minus(Duration.ofMinutes(1))));

        var result = minter(boundary, new FakeGrantBoundary()).mint(RENDER_JOB_ID, taskId());

        assertThat(result).isInstanceOf(FirstRenderGrantMinter.Result.Rejected.class);
        assertThat(((FirstRenderGrantMinter.Result.Rejected) result).reason())
                .isEqualTo("HOST_REGISTRATION_EXPIRED");
    }

    @Test
    void runtimeRegisteredAgainstADifferentHostIncarnationIsRejected() {
        StubBoundary boundary = validBoundary();
        boundary.runtime = Optional.of(new WorkerFabricRegistrationBoundary.PersistedRuntimeRegistration(
                RUNTIME, RUNTIME_INC, HOST, PhysicalHostIncarnationId.of("other-inc"),
                NOW.minus(Duration.ofMinutes(1)), NOW.plus(Duration.ofMinutes(10))));

        var result = minter(boundary, new FakeGrantBoundary()).mint(RENDER_JOB_ID, taskId());

        assertThat(((FirstRenderGrantMinter.Result.Rejected) result).reason())
                .isEqualTo("RUNTIME_HOST_INCARNATION_MISMATCH");
    }

    // ---------- readiness evidence ----------

    @Test
    void missingReadinessEvidenceFailsClosed() {
        StubBoundary boundary = validBoundary();
        boundary.readiness = Optional.empty();

        var result = minter(boundary, new FakeGrantBoundary())
                .mint(RENDER_JOB_ID, taskId(), graph(), contribution());

        assertThat(result).isInstanceOf(FirstRenderGrantMinter.Result.Unavailable.class);
        assertThat(((FirstRenderGrantMinter.Result.Unavailable) result).missingAuthoritativeInputs())
                .containsExactly("worker-readiness-evidence");
    }

    @Test
    void expiredReadinessEvidenceIsRejected() {
        StubBoundary boundary = validBoundary();
        boundary.readiness = Optional.of(readiness(
                RuntimeEnvironmentAvailability.AVAILABLE, SandboxRuntimeAvailability.AVAILABLE,
                HostResourceSnapshotGeneration.first(),
                NOW.minus(Duration.ofMinutes(1))));

        var result = minter(boundary, new FakeGrantBoundary())
                .mint(RENDER_JOB_ID, taskId(), graph(), contribution());

        assertThat(((FirstRenderGrantMinter.Result.Rejected) result).reason())
                .isEqualTo("READINESS_EVIDENCE_EXPIRED");
    }

    @Test
    void readinessRuntimeIncarnationMismatchIsRejected() {
        StubBoundary boundary = validBoundary();
        boundary.readiness = Optional.of(new WorkerReadinessEvidence(
                HOST, HOST_INC, RUNTIME, WorkerRuntimeIncarnationId.of("stale-runtime-inc"),
                RuntimeEnvironmentAvailability.AVAILABLE, SandboxRuntimeAvailability.AVAILABLE,
                HostResourceSnapshotGeneration.first(), NOW, NOW.plus(Duration.ofMinutes(1))));

        var result = minter(boundary, new FakeGrantBoundary())
                .mint(RENDER_JOB_ID, taskId(), graph(), contribution());

        assertThat(((FirstRenderGrantMinter.Result.Rejected) result).reason())
                .isEqualTo("READINESS_RUNTIME_INCARNATION_MISMATCH");
    }

    @Test
    void readinessGenerationMismatchIsRejected() {
        StubBoundary boundary = validBoundary();
        boundary.readiness = Optional.of(readiness(
                RuntimeEnvironmentAvailability.AVAILABLE, SandboxRuntimeAvailability.AVAILABLE,
                new HostResourceSnapshotGeneration(2L), NOW)); // generation disagrees with authority

        var result = minter(boundary, new FakeGrantBoundary())
                .mint(RENDER_JOB_ID, taskId(), graph(), contribution());

        assertThat(((FirstRenderGrantMinter.Result.Rejected) result).reason())
                .isEqualTo("READINESS_GENERATION_MISMATCH");
    }

    @Test
    void coarseAvailabilityIsDerivedFromTheReadinessEvidenceNeverPromoted() {
        StubBoundary boundary = validBoundary();
        boundary.readiness = Optional.of(readiness(
                RuntimeEnvironmentAvailability.UNKNOWN, SandboxRuntimeAvailability.AVAILABLE,
                HostResourceSnapshotGeneration.first(), NOW));
        FakeGrantBoundary grants = new FakeGrantBoundary();

        var result = minter(boundary, grants)
                .mint(RENDER_JOB_ID, taskId(), graph(), contribution());

        // The exact evidence values reach admission; UNKNOWN is neither dropped nor promoted.
        assertThat(grants.lastRequest.runtimeEnvironmentAvailability())
                .isEqualTo(RuntimeEnvironmentAvailability.UNKNOWN);
        assertThat(grants.lastRequest.sandboxRuntimeAvailability())
                .isEqualTo(SandboxRuntimeAvailability.AVAILABLE);
        // UNKNOWN cannot mint: admission produced no legal candidate under the existing evaluator.
        assertThat(result).isNotInstanceOf(FirstRenderGrantMinter.Result.Minted.class);
    }

    // ---------- descriptor / snapshot authority ----------

    @Test
    void missingHostDescriptorFailsClosed() {
        StubBoundary boundary = validBoundary();
        boundary.descriptor = Optional.empty();

        var result = minter(boundary, new FakeGrantBoundary())
                .mint(RENDER_JOB_ID, taskId(), graph(), contribution());

        assertThat(((FirstRenderGrantMinter.Result.Unavailable) result).missingAuthoritativeInputs())
                .containsExactly("physical-host-descriptor");
    }

    @Test
    void missingObservedUsageFailsClosed() {
        StubBoundary boundary = validBoundary();
        boundary.snapshot = Optional.of(snapshot(Optional.empty()));

        var result = minter(boundary, new FakeGrantBoundary())
                .mint(RENDER_JOB_ID, taskId(), graph(), contribution());

        assertThat(((FirstRenderGrantMinter.Result.Unavailable) result).missingAuthoritativeInputs())
                .containsExactly("host-resource-snapshot.observed-usage");
    }

    @Test
    void missingBoundGraphOrContributionFailsClosed() {
        var result = minter(validBoundary(), new FakeGrantBoundary())
                .mint(RENDER_JOB_ID, taskId());

        assertThat(((FirstRenderGrantMinter.Result.Unavailable) result).missingAuthoritativeInputs())
                .containsExactly("bound-graph-and-provider-contribution");
    }

    // ---------- admission consumption ----------

    @Test
    void admissionWithNoFeasibleCandidateIsNotMinted() {
        var result = minter(validBoundary(), new FakeGrantBoundary())
                .mint(RENDER_JOB_ID, taskId(), graph(), contribution());

        assertThat(result).isInstanceOf(FirstRenderGrantMinter.Result.Rejected.class);
        assertThat(((FirstRenderGrantMinter.Result.Rejected) result).reason())
                .isEqualTo("ADMISSION_NO_WORK");
    }

    @Test
    void admissionRejectionIsSurfacedAndNeverMinted() {
        FakeGrantBoundary grants = new FakeGrantBoundary();
        grants.registrationFailure =
                Optional.of(RequestWorkFailureReason.WORKER_RUNTIME_NOT_REGISTERED);

        var result = minter(validBoundary(), grants)
                .mint(RENDER_JOB_ID, taskId(), graph(), contribution());

        assertThat(((FirstRenderGrantMinter.Result.Rejected) result).reason())
                .isEqualTo("ADMISSION_REJECTED: WORKER_RUNTIME_NOT_REGISTERED");
    }

    @Test
    void admissionGrantFollowedByDurableReReadYieldsMinted() {
        FakeGrantBoundary grants = new FakeGrantBoundary();
        AssignmentGrant durable = grant(taskId(), FirstRenderGrantMinter.requestWorkId(RENDER_JOB_ID, taskId()));
        grants.resolution = Optional.of(new RequestWorkResult.Granted(durable.requestWorkId(), durable));
        grants.currentGrant = Optional.of(durable);

        var result = minter(validBoundary(), grants)
                .mint(RENDER_JOB_ID, taskId(), graph(), contribution());

        assertThat(result).isInstanceOf(FirstRenderGrantMinter.Result.Minted.class);
        assertThat(((FirstRenderGrantMinter.Result.Minted) result).grant()).isSameAs(durable);
    }

    @Test
    void admissionGrantWithoutADurableReReadIsNotMinted() {
        FakeGrantBoundary grants = new FakeGrantBoundary();
        AssignmentGrant transientGrant = grant(taskId(),
                FirstRenderGrantMinter.requestWorkId(RENDER_JOB_ID, taskId()));
        grants.resolution = Optional.of(
                new RequestWorkResult.Granted(transientGrant.requestWorkId(), transientGrant));
        grants.currentGrant = Optional.empty();

        var result = minter(validBoundary(), grants)
                .mint(RENDER_JOB_ID, taskId(), graph(), contribution());

        assertThat(result).isInstanceOf(FirstRenderGrantMinter.Result.Unavailable.class);
        assertThat(((FirstRenderGrantMinter.Result.Unavailable) result).missingAuthoritativeInputs())
                .containsExactly("persisted-assignment-grant");
    }

    // ---------- P1-1A.6 evidence closure ----------

    @Test
    void missingRuntimeSupportAdvertisementFailsClosed() {
        var result = minter(validBoundary(), new FakeGrantBoundary(),
                missingEvidence("runtime-support-advertisement"))
                .mint(RENDER_JOB_ID, taskId(), graph(), contribution());

        assertThat(((FirstRenderGrantMinter.Result.Unavailable) result).missingAuthoritativeInputs())
                .containsExactly("runtime-support-advertisement");
    }

    @Test
    void missingProviderHardwareObservationFailsClosed() {
        var result = minter(validBoundary(), new FakeGrantBoundary(),
                missingEvidence("provider-hardware-observation"))
                .mint(RENDER_JOB_ID, taskId(), graph(), contribution());

        assertThat(((FirstRenderGrantMinter.Result.Unavailable) result).missingAuthoritativeInputs())
                .containsExactly("provider-hardware-observation");
    }

    @Test
    void missingRuntimeDependencyObservationFailsClosed() {
        var result = minter(validBoundary(), new FakeGrantBoundary(),
                missingEvidence("runtime-dependency-observation"))
                .mint(RENDER_JOB_ID, taskId(), graph(), contribution());

        assertThat(((FirstRenderGrantMinter.Result.Unavailable) result).missingAuthoritativeInputs())
                .containsExactly("runtime-dependency-observation");
    }

    @Test
    void admissionRequestCarriesAllThreeAuthoritativeEvidenceInputs() {
        FakeGrantBoundary grants = new FakeGrantBoundary();

        var result = minter(validBoundary(), grants)
                .mint(RENDER_JOB_ID, taskId(), graph(), contribution());

        assertThat(result).isNotInstanceOf(FirstRenderGrantMinter.Result.Minted.class);
        RequestWork captured = grants.lastRequest;
        assertThat(captured.runtimeSupportAdvertisement()).isPresent();
        assertThat(captured.runtimeSupportAdvertisement().orElseThrow().runtimeKind())
                .isEqualTo(RuntimeLifecycleKind.EPHEMERAL_TASK);
        assertThat(captured.providerHardwareObservation()).isPresent();
        assertThat(captured.providerHardwareObservation().orElseThrow().evidence())
                .isInstanceOf(ProviderHardwareAvailableEvidence.class);
        assertThat(captured.runtimeDependencyObservation()).isPresent();
        assertThat(captured.runtimeDependencyObservation().orElseThrow().dependencies()).isEmpty();
    }

    @Test
    void anAdvertisementMissingTheRequiredSupportIdentifierIsNotMinted() {
        AdmissionEvidenceProducer wrongIdentifier = (runtimeId, hostId, implementationId) -> {
            AdmissionEvidenceProducer.Evidence base =
                    fullEvidence().produce(runtimeId, hostId, implementationId);
            return new AdmissionEvidenceProducer.Evidence(
                    Optional.of(new WorkerRuntimeSupportAdvertisement(
                            runtimeId,
                            RuntimeLifecycleKind.EPHEMERAL_TASK,
                            Map.of(
                                    RuntimeSupportIdentifier.of("other.provider.v9"),
                                    new RuntimeSupportEvidence("provider-plugin", "other@1")))),
                    base.providerHardwareObservation(),
                    base.runtimeDependencyObservation());
        };

        var result = minter(validBoundary(), new FakeGrantBoundary(), wrongIdentifier)
                .mint(RENDER_JOB_ID, taskId(), graph(), contribution());

        assertThat(result).isInstanceOf(FirstRenderGrantMinter.Result.Rejected.class);
        assertThat(((FirstRenderGrantMinter.Result.Rejected) result).reason())
                .isEqualTo("ADMISSION_NO_WORK");
    }

    @Test
    void anAdvertisementWithTheWrongRuntimeKindIsNotMinted() {
        AdmissionEvidenceProducer wrongKind = (runtimeId, hostId, implementationId) -> {
            AdmissionEvidenceProducer.Evidence base =
                    fullEvidence().produce(runtimeId, hostId, implementationId);
            return new AdmissionEvidenceProducer.Evidence(
                    Optional.of(new WorkerRuntimeSupportAdvertisement(
                            runtimeId,
                            RuntimeLifecycleKind.RESIDENT_RUNTIME,
                            Map.of(
                                    RuntimeSupportIdentifier.of("ffmpeg.cpu.v1"),
                                    new RuntimeSupportEvidence(
                                            "provider-plugin", "media.transcode.ffmpeg@1.0.0")))),
                    base.providerHardwareObservation(),
                    base.runtimeDependencyObservation());
        };

        var result = minter(validBoundary(), new FakeGrantBoundary(), wrongKind)
                .mint(RENDER_JOB_ID, taskId(), graph(), contribution());

        assertThat(result).isInstanceOf(FirstRenderGrantMinter.Result.Rejected.class);
        assertThat(((FirstRenderGrantMinter.Result.Rejected) result).reason())
                .isEqualTo("ADMISSION_NO_WORK");
    }

    // ---------- fixtures ----------

    private static FirstRenderGrantMinter minter(
            WorkerFabricRegistrationBoundary boundary, FakeGrantBoundary grants) {
        return minter(boundary, grants, fullEvidence());
    }

    private static FirstRenderGrantMinter minter(
            WorkerFabricRegistrationBoundary boundary,
            FakeGrantBoundary grants,
            AdmissionEvidenceProducer evidenceProducer) {
        return new FirstRenderGrantMinter(
                boundary, grants, new NativePullAdmissionPort(grants), evidenceProducer,
                // This test double exercises grant minting, not the reservation read model; the
                // pre-change projection always supplied UNKNOWN, so returning UNKNOWN preserves the
                // existing test semantics (candidate ineligibility -> ADMISSION_NO_WORK).
                (host, incarnation, generation, demand) -> ReservationFeasibility.UNKNOWN,
                CLOCK);
    }

    /** Full P1-1A.6 evidence triple, bound to whatever identity the minter asks for. */
    private static AdmissionEvidenceProducer fullEvidence() {
        return (runtimeId, hostId, implementationId) -> new AdmissionEvidenceProducer.Evidence(
                Optional.of(new WorkerRuntimeSupportAdvertisement(
                        runtimeId,
                        RuntimeLifecycleKind.EPHEMERAL_TASK,
                        Map.of(
                                RuntimeSupportIdentifier.of("ffmpeg.cpu.v1"),
                                new RuntimeSupportEvidence(
                                        "provider-plugin", "media.transcode.ffmpeg@1.0.0")))),
                Optional.of(new ProviderHardwareObservation(
                        implementationId,
                        runtimeId,
                        hostId,
                        Optional.empty(),
                        NOW,
                        NOW.plus(Duration.ofSeconds(60)),
                        new ProviderHardwareAvailableEvidence(
                                CpuArchitecture.X86_64, List.of(), List.of(), List.of(),
                                Optional.empty()))),
                Optional.of(new RuntimeDependencyObservation(
                        implementationId,
                        runtimeId,
                        Optional.empty(),
                        RuntimeDependencyProbeSchemaVersion.CURRENT,
                        NOW,
                        NOW.plus(Duration.ofSeconds(60)),
                        List.of())));
    }

    private static AdmissionEvidenceProducer missingEvidence(String missing) {
        AdmissionEvidenceProducer full = fullEvidence();
        return (runtimeId, hostId, implementationId) -> {
            AdmissionEvidenceProducer.Evidence evidence =
                    full.produce(runtimeId, hostId, implementationId);
            return new AdmissionEvidenceProducer.Evidence(
                    "runtime-support-advertisement".equals(missing)
                            ? Optional.empty()
                            : evidence.runtimeSupportAdvertisement(),
                    "provider-hardware-observation".equals(missing)
                            ? Optional.empty()
                            : evidence.providerHardwareObservation(),
                    "runtime-dependency-observation".equals(missing)
                            ? Optional.empty()
                            : evidence.runtimeDependencyObservation());
        };
    }

    private static StubBoundary validBoundary() {
        return StubBoundary.valid();
    }

    private static WorkerReadinessEvidence readiness(
            RuntimeEnvironmentAvailability environment,
            SandboxRuntimeAvailability sandbox,
            HostResourceSnapshotGeneration generation,
            Instant capturedAt) {
        return new WorkerReadinessEvidence(
                HOST, HOST_INC, RUNTIME, RUNTIME_INC, environment, sandbox, generation,
                capturedAt, capturedAt.plus(Duration.ofSeconds(30)));
    }

    private static WorkerFabricRegistrationBoundary.PersistedHostSnapshot snapshot(
            Optional<WorkerFabricRegistrationBoundary.PersistedObservedUsage> observedUsage) {
        return new WorkerFabricRegistrationBoundary.PersistedHostSnapshot(
                HOST, HOST_INC, HostResourceSnapshotGeneration.first(), "fingerprint",
                NOW.minus(Duration.ofSeconds(1)), 1, 8_000L, 64_000L, 100_000L,
                1_000L, 6_400L, 10_000L, List.of(), observedUsage);
    }

    private static WorkerFabricRegistrationBoundary.PersistedObservedUsage observedUsage() {
        return new WorkerFabricRegistrationBoundary.PersistedObservedUsage(
                0.10, 1_000L, 1_000L, List.of());
    }

    private static WorkerFabricRegistrationBoundary.PersistedHostDescriptor descriptor() {
        return new WorkerFabricRegistrationBoundary.PersistedHostDescriptor(
                HOST, HostLocation.of("local"), TrustZoneId.of("local"),
                NOW.minus(Duration.ofSeconds(1)), List.of());
    }

    // ---------- real bound graph + exact provider contribution ----------

    private static ExecutableTaskId taskId() {
        return graph().tasks().getFirst().id();
    }

    private static final class GraphHolder {
        private static final ProviderBoundExecutableTaskGraph GRAPH = build();

        private static ProviderBoundExecutableTaskGraph build() {
            return new ProviderBindingEntryService()
                    .bind(plan(), List.of(candidate()), List.of())
                    .executableTaskGraph();
        }
    }

    private static ProviderBoundExecutableTaskGraph graph() {
        return GraphHolder.GRAPH;
    }

    private static ProviderPluginContribution contribution() {
        ProviderBindingPin pin = graph().tasks().getFirst().providerBindingPin();
        ProviderPluginContribution contribution = mock(ProviderPluginContribution.class);
        when(contribution.providerBindingPin()).thenReturn(pin);
        when(contribution.providerHardwareRequirement()).thenReturn(Optional.of(
                new ProviderHardwareRequirement(
                        pin.providerImplementationId(), CpuArchitecture.X86_64,
                        Optional.empty(), List.of(), List.of(), List.of())));
        // An empty backend support set keeps the projected candidate statically ineligible, so the
        // existing execution-backend evaluator rejects it without needing provider hardware telemetry.
        when(contribution.providerBackendExecutionSupport()).thenReturn(
                Optional.of(ProviderBackendExecutionSupport.declared(pin, Set.of())));
        when(contribution.resourceProfile()).thenReturn(
                Optional.of(new ProviderResourceProfile(1_000L, 1_000L, 1_000L, Map.of())));
        when(contribution.runtimeDependencyRequirements()).thenReturn(List.of());
        when(contribution.sandboxRequirement()).thenReturn(SandboxRuntimeRequirement.REQUIRED);
        when(contribution.workerRuntimeSupportRequirement()).thenReturn(
                new WorkerRuntimeSupportRequirement(
                        pin, RuntimeLifecycleKind.RESIDENT_RUNTIME,
                        RuntimeSupportIdentifier.of("native.pull.worker")));
        return contribution;
    }

    /** Two-unit bound graph so the per-task admission case can be exercised. */
    private static ProviderBoundExecutableTaskGraph twoTaskGraph() {
        PhysicalExecutionPlan plan = new PhysicalExecutionPlan(
                "1",
                new ExecutionPlanId("plan-2task"),
                ExecutionPlanSchemaVersion.V1,
                new RenderPlanFingerprint("fingerprint-2task"),
                List.of(twoTaskUnit("a2"), twoTaskUnit("b2")),
                null,
                new PhysicalExecutionPlanDigest("digest-2task"));
        return new ProviderBindingEntryService().bind(plan, List.of(candidate()), List.of())
                .executableTaskGraph();
    }

    private static PhysicalPlanUnit twoTaskUnit(String suffix) {
        return new PhysicalPlanUnit(
                new ExecutionStepId("unit-" + suffix),
                "logical-unit-" + suffix,
                new RenderNodeId("render-unit-" + suffix),
                new RenderNodeKind.Decode(),
                "decode",
                List.of(),
                List.of(new OutputDeclaration(
                        new ExecutionOutputId("output-" + suffix),
                        "logical-unit-" + suffix,
                        new RenderNodeId("render-unit-" + suffix),
                        List.of(), List.of(), List.of(), List.of())),
                List.of(), null, null, List.of(), List.of(), null, true);
    }

    private static PhysicalExecutionPlan plan() {
        PhysicalPlanUnit unit = new PhysicalPlanUnit(
                new ExecutionStepId("unit-a2"),
                "logical-unit-a2",
                new RenderNodeId("render-unit-a2"),
                new RenderNodeKind.Decode(),
                "decode",
                List.of(),
                List.of(new OutputDeclaration(
                        new ExecutionOutputId("output-a2"),
                        "logical-unit-a2",
                        new RenderNodeId("render-unit-a2"),
                        List.of(), List.of(), List.of(), List.of())),
                List.of(), null, null, List.of(), List.of(), null, true);
        return new PhysicalExecutionPlan(
                "1",
                new ExecutionPlanId("plan-a2"),
                ExecutionPlanSchemaVersion.V1,
                new RenderPlanFingerprint("fingerprint-a2"),
                List.of(unit),
                null,
                new PhysicalExecutionPlanDigest("digest-a2"));
    }

    private static ProviderCandidate candidate() {
        ProviderId providerId = ProviderId.of("provider-a2");
        ProviderImplementationId implementationId = ProviderImplementationId.of("provider-a2.native");
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

    private static AssignmentGrant grant(ExecutableTaskId taskId, RequestWorkId requestWorkId) {
        ExecutionAssignmentId assignmentId = ExecutionAssignmentId.of("assignment-a2");
        ReservationId reservationId = ReservationId.of("reservation-a2");
        ExecutionAttemptId attemptId = new ExecutionAttemptId("attempt-a2");
        ExecutionOwnershipGeneration generation = ExecutionOwnershipGeneration.first();
        ExecutionAssignment assignment = new ExecutionAssignment(
                assignmentId, taskId, attemptId, generation, RUNTIME, RUNTIME_INC, HOST, HOST_INC,
                Set.of(), Set.of(reservationId));
        Reservation reservation = new Reservation(
                reservationId, HOST, ReservationKind.TASK, ReservedResources.none(),
                ReservationState.ACTIVE);
        TaskLease lease = new TaskLease(
                LeaseId.of("lease-a2"), taskId, assignmentId, attemptId, generation,
                RUNTIME, RUNTIME_INC, Set.of(reservationId),
                NOW.plusSeconds(60), NOW, LeaseRenewalContract.NATIVE_PULL_V1,
                new LeaseFencingToken("fence-a2"));
        ExecutionAttempt attempt = new ExecutionAttempt(
                attemptId, taskId, generation, ExecutionBackend.NATIVE_PULL_WORKER,
                ExecutionAttemptState.CREATED, Optional.empty());
        return new AssignmentGrant(requestWorkId, assignment, List.of(reservation), lease, attempt);
    }

    // ---------- test doubles ----------

    /** In-memory registration authority exposing only the read model the minter consumes. */
    private static final class StubBoundary implements WorkerFabricRegistrationBoundary {

        private Optional<PersistedHostRegistration> host = Optional.empty();
        private Optional<PersistedRuntimeRegistration> runtime = Optional.empty();
        private Optional<PersistedHostSnapshot> snapshot = Optional.empty();
        private Optional<PersistedHostDescriptor> descriptor = Optional.empty();
        private Optional<WorkerReadinessEvidence> readiness = Optional.empty();

        static StubBoundary valid() {
            StubBoundary boundary = new StubBoundary();
            boundary.host = Optional.of(new PersistedHostRegistration(
                    HOST, HOST_INC, NOW.minus(Duration.ofMinutes(1)), NOW.plus(Duration.ofMinutes(10))));
            boundary.runtime = Optional.of(new PersistedRuntimeRegistration(
                    RUNTIME, RUNTIME_INC, HOST, HOST_INC,
                    NOW.minus(Duration.ofMinutes(1)), NOW.plus(Duration.ofMinutes(10))));
            boundary.snapshot = Optional.of(snapshot(Optional.of(observedUsage())));
            boundary.descriptor = Optional.of(descriptor());
            boundary.readiness = Optional.of(readiness(
                    RuntimeEnvironmentAvailability.AVAILABLE, SandboxRuntimeAvailability.AVAILABLE,
                    HostResourceSnapshotGeneration.first(), NOW));
            return boundary;
        }

        static StubBoundary empty() {
            return new StubBoundary();
        }

        @Override
        public void registerHost(HostRegistration registration) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerRuntime(RuntimeRegistration registration) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<HostResourceSnapshotGeneration> currentSnapshotGeneration(
                PhysicalHostId physicalHostId, PhysicalHostIncarnationId physicalHostIncarnationId) {
            return snapshot.map(PersistedHostSnapshot::snapshotGeneration);
        }

        @Override
        public Optional<PersistedHostRegistration> findActiveHostRegistration(
                PhysicalHostId physicalHostId) {
            return host;
        }

        @Override
        public Optional<PersistedHostRegistration> findActiveHostRegistration() {
            return host;
        }

        @Override
        public Optional<PersistedRuntimeRegistration> findActiveRuntimeRegistration(
                WorkerRuntimeId workerRuntimeId) {
            return runtime;
        }

        @Override
        public Optional<PersistedRuntimeRegistration> findActiveRuntimeRegistration() {
            return runtime;
        }

        @Override
        public Optional<PersistedHostSnapshot> findHostSnapshot(
                PhysicalHostId physicalHostId,
                PhysicalHostIncarnationId physicalHostIncarnationId,
                HostResourceSnapshotGeneration snapshotGeneration) {
            return snapshot;
        }

        @Override
        public Optional<PersistedHostDescriptor> findHostDescriptor(PhysicalHostId physicalHostId) {
            return descriptor;
        }

        @Override
        public Optional<WorkerReadinessEvidence> findReadiness(
                PhysicalHostId physicalHostId, PhysicalHostIncarnationId physicalHostIncarnationId) {
            return readiness;
        }
    }

    /** Configurable grant boundary so admission terminal outcomes and the reread are exercised. */
    private static final class FakeGrantBoundary implements AtomicAssignmentGrantBoundary {

        private Optional<RequestWorkResult> resolution = Optional.empty();
        private Optional<RequestWorkFailureReason> registrationFailure = Optional.empty();
        private Optional<AssignmentGrant> currentGrant = Optional.empty();
        private RequestWork lastRequest;

        @Override
        public Optional<RequestWorkResult> findResolution(RequestWork requestWork) {
            lastRequest = requestWork;
            return resolution;
        }

        @Override
        public Optional<RequestWorkFailureReason> validateRegistration(RequestWork requestWork) {
            lastRequest = requestWork;
            return registrationFailure;
        }

        @Override
        public RequestWorkResult resolveTerminal(
                RequestWork requestWork, RequestWorkResult terminalResult) {
            return terminalResult;
        }

        @Override
        public RequestWorkResult tryGrant(AtomicAssignmentGrantCommand command) {
            throw new UnsupportedOperationException("no real grant transaction in this test double");
        }

        @Override
        public Optional<AssignmentGrant> findCurrentGrant(ExecutableTaskId taskId) {
            return currentGrant;
        }
    }
}
