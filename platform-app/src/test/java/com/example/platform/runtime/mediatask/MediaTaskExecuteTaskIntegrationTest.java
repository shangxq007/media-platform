package com.example.platform.runtime.mediatask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.execution.binding.BoundGraphReference;
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
import com.example.platform.persistence.binding.JooqBoundGraphInputStore;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.render.domain.renderplan.LogicalArtifactId;
import com.example.platform.render.domain.renderplan.RenderArtifactReference.FinalArtifactExpectation;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderExecutionCoverage;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.storage.contract.StorageProviderId;
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
import com.example.platform.workerfabric.reuse.RuntimeClosedLoopOrchestrator;
import com.example.platform.workerfabric.reuse.RuntimeClosedLoopRequest;
import com.example.platform.workerfabric.reuse.RuntimeClosedLoopResult;
import com.example.platform.workflow.temporal.mediatask.MediaTaskWorkflowResult;
import com.example.platform.workflow.temporal.mediatask.PreparedTaskRef;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * P2-5b-2b-2b-1: DB-backed {@code executeTask} coverage over the real canonical bound-graph store
 * (V1 + V2 migrations on PostgreSQL 16).
 *
 * <p>Chain exercised: {@code JooqBoundGraphInputStore.save} → {@code executeTask} → load/re-derive/
 * digest verification → per-task grant resolution, with the fail-closed paths asserted (missing
 * grant, unconfigured publication scope, tenant mismatch) and the orchestrator never touched.
 *
 * <p><b>Not yet covered here</b> (2b-2b-2): the successful whole-graph path. A graph derived through
 * {@code ProviderBindingEntryService.bind} carries no {@code POST_EXECUTION} boundary action, so its
 * task has no authoritative output and the publication planner correctly fails closed with
 * {@code TASK_OUTPUT_ABSENT}; covering the success path needs a plan/fixture whose binding produces
 * that boundary declaration (or a graph built through {@code ExecutableTask.create} +
 * {@code ProviderBoundExecutableTaskGraph.derive} with equivalent durable inputs).
 *
 * <p><b>TEST-ONLY doubles (explicitly labelled, not production stubs):</b> the grant boundary and
 * the orchestrator. The Postgres grant transaction itself is covered by the worker-fabric authority
 * test ({@code AtomicAssignmentGrantPostgresTest}); the closed loop's execution mechanics are covered
 * by {@code RuntimeClosedLoopConformanceTest} and {@code FfmpegClosedLoopIntegrationTest}. Loading a
 * real provider plugin JAR is 2b-2b-2.
 */
@Testcontainers
class MediaTaskExecuteTaskIntegrationTest {

    private static final String TENANT = "tenant-exec";
    private static final String JOB = "job-exec";
    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");
    private static final MediaTime ZERO = MediaTime.ofRational(0, 1);
    private static final MediaTime TWO = MediaTime.ofRational(2, 1);
    private static final FrameRate FPS = FrameRate.of(30, 1);

    @Container
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JooqBoundGraphInputStore store;

    @BeforeAll
    static void setup() throws Exception {
        PG.start();
        Path migrationDir = locateMigrationDir();
        PG.copyFileToContainer(
                MountableFile.forHostPath(migrationDir.resolve("V1__initial_schema.sql")),
                "/migrations/V1.sql");
        PG.copyFileToContainer(
                MountableFile.forHostPath(migrationDir.resolve("V2__render_binding_inputs.sql")),
                "/migrations/V2.sql");
        applyMigration("/migrations/V1.sql");
        applyMigration("/migrations/V2.sql");
        DSLContext dsl = DSL.using(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
        store = new JooqBoundGraphInputStore(dsl);
    }

    @Test
    void executesTheStoredGraphAndPlansTheTerminalOutputPublication() throws Exception {
        Fixture fixture = seed();
        RuntimeClosedLoopOrchestrator orchestrator = mock(RuntimeClosedLoopOrchestrator.class);
        RuntimeClosedLoopResult canned = mock(RuntimeClosedLoopResult.class);
        when(orchestrator.execute(any())).thenReturn(canned);
        MediaTaskActivity activity = activity(grantBoundary(fixture.taskId()), orchestrator);

        MediaTaskWorkflowResult result = activity.executeTask(preparedRef(fixture), TENANT);

        assertThat(result.status()).isEqualTo(MediaTaskWorkflowResult.STATUS_EXECUTED);
        assertThat(result.tenantId()).isEqualTo(TENANT);
        assertThat(result.renderJobId()).isEqualTo(JOB);
        assertThat(result.executableTaskIds()).containsExactly(fixture.taskId().sha256Hex());

        RuntimeClosedLoopRequest request = capturedRequest(orchestrator);
        assertThat(request.tenantId()).isEqualTo(TENANT);
        assertThat(request.graph().digest()).isEqualTo(fixture.graph().digest());
        assertThat(request.requestedTasks()).containsExactly(fixture.taskId());
        assertThat(request.taskExecutions()).containsOnlyKeys(fixture.taskId());
        var execution = request.taskExecutions().get(fixture.taskId());
        assertThat(execution.durableOutputTarget().writeSessionId())
                .isEqualTo("task-output-" + fixture.taskId().sha256Hex());
        assertThat(execution.artifactCommitMetadata().artifactId().value())
                .isEqualTo("render-task-" + fixture.taskId().sha256Hex());
        assertThat(execution.artifactCommitMetadata().tenantId()).isEqualTo(TENANT);
        assertThat(execution.artifactCommitMetadata().renderJobId()).isEqualTo(JOB);
        assertThat(execution.runtimeContext().platformExecutionAttemptId())
                .isEqualTo(new ExecutionAttemptId("attempt-exec"));
    }

    @Test
    void failsClosedWhenTheTaskHasNoCurrentGrant() throws Exception {
        Fixture fixture = seed();
        RuntimeClosedLoopOrchestrator orchestrator = mock(RuntimeClosedLoopOrchestrator.class);
        MediaTaskActivity activity = activity(
                mock(AtomicAssignmentGrantBoundary.class), orchestrator);

        assertThatThrownBy(() -> activity.executeTask(
                new PreparedTaskRef(
                        TENANT, JOB, fixture.reference().planRef(), fixture.reference().planDigest(),
                        fixture.reference().expectedExecutableTaskGraphDigest(),
                        List.of(fixture.taskId().sha256Hex())),
                TENANT))
                .isInstanceOf(com.example.platform.workerfabric.reuse
                        .TaskRuntimeExecutionConstructionException.class)
                .hasMessageContaining("GRANT_ABSENT");
        verifyNoInteractions(orchestrator);
    }

    @Test
    void failsClosedWhenThePublicationScopeIsNotConfigured() throws Exception {
        Fixture fixture = seed();
        RuntimeClosedLoopOrchestrator orchestrator = mock(RuntimeClosedLoopOrchestrator.class);
        MediaTaskActivity activity = new MediaTaskActivity(
                store,
                grantBoundary(fixture.taskId()),
                orchestrator,
                new MediaTaskPublicationSettings(
                        "", "provider-1", "local",
                        ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER, Clock.systemUTC()));

        assertThatThrownBy(() -> activity.executeTask(
                new PreparedTaskRef(
                        TENANT, JOB, fixture.reference().planRef(), fixture.reference().planDigest(),
                        fixture.reference().expectedExecutableTaskGraphDigest(),
                        List.of(fixture.taskId().sha256Hex())),
                TENANT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("projectId");
        verifyNoInteractions(orchestrator);
    }

    @Test
    void failsClosedWhenTheTenantDoesNotMatchTheReference() throws Exception {
        Fixture fixture = seed();
        MediaTaskActivity activity = activity(
                grantBoundary(fixture.taskId()), mock(RuntimeClosedLoopOrchestrator.class));

        assertThatThrownBy(() -> activity.executeTask(
                new PreparedTaskRef(
                        TENANT, JOB, fixture.reference().planRef(), fixture.reference().planDigest(),
                        fixture.reference().expectedExecutableTaskGraphDigest(),
                        List.of(fixture.taskId().sha256Hex())),
                "tenant-other"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId must match");
    }

    // ---------- fixtures ----------

    private record Fixture(
            ProviderBoundExecutableTaskGraph graph,
            ExecutableTaskId taskId,
            BoundGraphReference reference) {
    }

    private static Fixture seed() throws Exception {
        PhysicalExecutionPlan plan = plan();
        ProviderCandidate candidate = candidate();
        ProviderBoundExecutableTaskGraph graph = new ProviderBindingEntryService()
                .bind(plan, List.of(candidate), List.of())
                .executableTaskGraph();
        BoundGraphInputs inputs =
                new BoundGraphInputs(plan, List.of(candidate), List.of(), graph.digest());
        BoundGraphReference reference = store.save(inputs, TENANT, JOB);
        return new Fixture(graph, graph.tasks().getFirst().id(), reference);
    }

    private static PreparedTaskRef preparedRef(Fixture fixture) {
        return new PreparedTaskRef(
                TENANT,
                JOB,
                fixture.reference().planRef(),
                fixture.reference().planDigest(),
                fixture.reference().expectedExecutableTaskGraphDigest(),
                List.of(fixture.taskId().sha256Hex()));
    }

    private static RuntimeClosedLoopRequest capturedRequest(
            RuntimeClosedLoopOrchestrator orchestrator) throws Exception {
        ArgumentCaptor<RuntimeClosedLoopRequest> captor =
                ArgumentCaptor.forClass(RuntimeClosedLoopRequest.class);
        org.mockito.Mockito.verify(orchestrator).execute(captor.capture());
        return captor.getValue();
    }

    private static MediaTaskActivity activity(
            AtomicAssignmentGrantBoundary grantBoundary, RuntimeClosedLoopOrchestrator orchestrator) {
        return new MediaTaskActivity(
                store,
                grantBoundary,
                orchestrator,
                new MediaTaskPublicationSettings(
                        "project-exec", "provider-exec", "local",
                        ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER, Clock.systemUTC()));
    }

    /** TEST-ONLY grant double: a real {@link AssignmentGrant} record, no database transaction. */
    private static AtomicAssignmentGrantBoundary grantBoundary(ExecutableTaskId taskId) {
        AtomicAssignmentGrantBoundary boundary = mock(AtomicAssignmentGrantBoundary.class);
        when(boundary.findCurrentGrant(taskId)).thenReturn(Optional.of(grant(taskId)));
        return boundary;
    }

    private static AssignmentGrant grant(ExecutableTaskId taskId) {
        PhysicalHostId hostId = PhysicalHostId.of("host-exec");
        PhysicalHostIncarnationId hostIncarnation = PhysicalHostIncarnationId.of("host-inc-exec");
        WorkerRuntimeId runtimeId = WorkerRuntimeId.of("runtime-exec");
        WorkerRuntimeIncarnationId runtimeIncarnation =
                WorkerRuntimeIncarnationId.of("runtime-inc-exec");
        ExecutionAssignmentId assignmentId = ExecutionAssignmentId.of("assignment-exec");
        ReservationId reservationId = ReservationId.of("reservation-exec");
        ExecutionAttemptId attemptId = new ExecutionAttemptId("attempt-exec");
        ExecutionOwnershipGeneration generation = ExecutionOwnershipGeneration.first();
        ExecutionAssignment assignment = new ExecutionAssignment(
                assignmentId, taskId, attemptId, generation, runtimeId, runtimeIncarnation,
                hostId, hostIncarnation, Set.of(), Set.of(reservationId));
        Reservation reservation = new Reservation(
                reservationId, hostId, ReservationKind.TASK, ReservedResources.none(),
                ReservationState.ACTIVE);
        TaskLease lease = new TaskLease(
                LeaseId.of("lease-exec"), taskId, assignmentId, attemptId, generation,
                runtimeId, runtimeIncarnation, Set.of(reservationId),
                NOW.plusSeconds(60), NOW, LeaseRenewalContract.NATIVE_PULL_V1,
                new LeaseFencingToken("fence-exec"));
        ExecutionAttempt attempt = new ExecutionAttempt(
                attemptId, taskId, generation, ExecutionBackend.NATIVE_PULL_WORKER,
                ExecutionAttemptState.CREATED, Optional.empty());
        return new AssignmentGrant(
                RequestWorkId.of("request-exec"), assignment, List.of(reservation), lease, attempt);
    }

    private static PhysicalExecutionPlan plan() {
        PhysicalPlanUnit unit = new PhysicalPlanUnit(
                new ExecutionStepId("unit-2b2b1"),
                "logical-unit-2b2b1",
                new RenderNodeId("render-unit-2b2b1"),
                new RenderNodeKind.Decode(),
                "decode",
                List.of(),
                List.of(new OutputDeclaration(
                        new ExecutionOutputId("output-1"),
                        "logical-unit-2b2b1",
                        new RenderNodeId("render-unit-2b2b1"),
                        List.of(), List.of(),
                        List.of(),
                        List.of(new FinalArtifactExpectation(RenderOutputRole.RENDER_MASTER)))),
                List.of(),
                new RenderSampleWindow(ZERO, TWO, FPS),
                new RenderExecutionCoverage(ZERO, TWO, FPS),
                List.of(), List.of(),
                new RenderExtent(ZERO, TWO, FPS),
                true);
        return new PhysicalExecutionPlan(
                "1",
                new ExecutionPlanId("plan-2b2b1"),
                ExecutionPlanSchemaVersion.V1,
                new RenderPlanFingerprint("fingerprint-2b2b1"),
                List.of(unit),
                new RenderExtent(ZERO, TWO, FPS),
                new PhysicalExecutionPlanDigest("digest-2b2b1"));
    }

    private static ProviderCandidate candidate() {
        ProviderId providerId = ProviderId.of("provider-2b2b1");
        ProviderImplementationId implementationId =
                ProviderImplementationId.of("provider-2b2b1.native");
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
                        List.of(ProviderStaticCompatibility.ArtifactRequirementKind.FINAL_OUTPUT),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        ProviderStaticCompatibility.LoweringSupport.SUPPORTED));
    }

    private static Path locateMigrationDir() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root not found");
        }
        return current.resolve("platform-app/src/main/resources/db/migration");
    }

    private static void applyMigration(String path) throws Exception {
        var result = PG.execInContainer("psql", "-U", PG.getUsername(), "-d", PG.getDatabaseName(),
                "-v", "ON_ERROR_STOP=1", "-f", path);
        assertEquals(0, result.getExitCode(), "migration " + path + " failed: " + result.getStderr());
    }
}
