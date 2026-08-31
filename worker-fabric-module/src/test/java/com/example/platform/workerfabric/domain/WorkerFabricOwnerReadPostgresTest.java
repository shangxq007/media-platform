package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.workerfabric.application.DeviceReadSnapshot;
import com.example.platform.workerfabric.application.ExecutionReadSnapshot;
import com.example.platform.workerfabric.application.WorkerFabricReadService;
import com.example.platform.workerfabric.application.WorkerRuntimeReadSnapshot;
import com.example.platform.workerfabric.infrastructure.JooqWorkerFabricReadAdapter;
import java.time.Instant;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Durable owner-read acceptance: exact identities, explicit unknowns, and immutable snapshots. */
class WorkerFabricOwnerReadPostgresTest extends PostgresTestContainerSupport {

    private static final Instant CAPTURED_AT = Instant.parse("2026-08-31T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-31T10:01:00Z");
    private static DataSource dataSource;
    private static DSLContext dsl;
    private WorkerFabricReadService service;

    @BeforeAll
    static void startDatabaseAuthority() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
        AssignmentGrantPostgresFixture.migrate(dataSource);
    }

    @AfterAll
    static void closeDatabaseAuthority() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void resetAuthority() {
        AssignmentGrantPostgresFixture.truncate(dsl);
        seedExactOwnerFacts();
        JooqWorkerFabricReadAdapter adapter = new JooqWorkerFabricReadAdapter(dsl);
        service = new WorkerFabricReadService(adapter, adapter, adapter);
    }

    @Test
    void readsExactWorkerIncarnationAndPreservesUndurableFactsAsUnknown() {
        WorkerRuntimeReadSnapshot snapshot = service.findWorkerRuntime(WorkerRuntimeId.of("runtime-a"))
                .orElseThrow();

        assertThat(snapshot.workerRuntimeIncarnationId())
                .isEqualTo(WorkerRuntimeIncarnationId.of("runtime-inc-a"));
        assertThat(snapshot.physicalHostId()).isEqualTo(PhysicalHostId.of("host-a"));
        assertThat(snapshot.availability().state()).isEqualTo(AvailabilityState.REACHABLE);
        assertThat(snapshot.availabilityObservedAt()).isEqualTo(UPDATED_AT);
        assertThat(snapshot.availabilityFreshUntil()).isEmpty();
        assertThat(snapshot.descriptor()).isEmpty();
        assertThat(snapshot.supportAdvertisement()).isEmpty();
        assertThat(snapshot.hostResourceSnapshotGeneration())
                .contains(new HostResourceSnapshotGeneration(7));
        assertThat(snapshot.assignments()).singleElement().satisfies(assignment -> {
            assertThat(assignment.assignment().id()).isEqualTo(ExecutionAssignmentId.of("assignment-a"));
            assertThat(assignment.reservations()).singleElement().satisfies(reservation -> {
                assertThat(reservation.id()).isEqualTo(ReservationId.of("reservation-a"));
                assertThat(reservation.state()).isEqualTo(ReservationState.RECOVERY_HOLD);
            });
        });
        assertThatThrownBy(() -> snapshot.assignments().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void readsOnlyTheExplicitCurrentDeviceGenerationWithoutInventingDescriptorOrObservation() {
        DeviceReadSnapshot snapshot = service.findCurrentDevices(DeviceId.of("gpu-a"))
                .getFirst();

        assertThat(snapshot.physicalHostId()).isEqualTo(PhysicalHostId.of("host-a"));
        assertThat(snapshot.physicalHostIncarnationId())
                .isEqualTo(PhysicalHostIncarnationId.of("host-inc-a"));
        assertThat(snapshot.hostResourceSnapshotGeneration())
                .isEqualTo(new HostResourceSnapshotGeneration(7));
        assertThat(snapshot.capacity())
                .isEqualTo(new DeviceResourceCapacity(DeviceId.of("gpu-a"), 24_000, 80, 2, 3));
        assertThat(snapshot.descriptor()).isEmpty();
        assertThat(snapshot.availability()).isEmpty();
        assertThat(snapshot.availabilityObservedAt()).isEmpty();
        assertThat(snapshot.observedUsage()).isEmpty();
        assertThat(snapshot.observedAt()).isEmpty();
        assertThat(snapshot.assignments()).singleElement().satisfies(assignment ->
                assertThat(assignment.reservations().getFirst().resources().deviceResources())
                        .containsEntry(
                                DeviceId.of("gpu-a"),
                                new DeviceResourceReservation(
                                        DeviceId.of("gpu-a"), 12_000, 40, 1, 1)));
    }

    @Test
    void readsAttemptAndStoredObservationSubsetByExactAttemptWithoutProviderReconstruction() {
        ExecutionReadSnapshot snapshot = service.findExecution(ExecutionAttemptId.of("attempt-a"))
                .orElseThrow();

        assertThat(snapshot.attempt().state()).isEqualTo(ExecutionAttemptState.FAILED);
        assertThat(snapshot.attempt().ownershipGeneration())
                .isEqualTo(new ExecutionOwnershipGeneration(3));
        assertThat(snapshot.createdAt()).isEqualTo(CAPTURED_AT);
        assertThat(snapshot.updatedAt()).isEqualTo(UPDATED_AT);
        assertThat(snapshot.providerBindingPin()).isEmpty();
        assertThat(snapshot.assignment()).isPresent();
        assertThat(snapshot.observations()).singleElement().satisfies(observation -> {
            assertThat(observation.observationId()).isEqualTo(new ObservationId("observation-a"));
            assertThat(observation.observedExecutionState()).isEqualTo(ObservedExecutionState.FAILED);
            assertThat(observation.currentEvidence()).isTrue();
            assertThat(observation.observedAt()).isEqualTo(UPDATED_AT);
            assertThat(observation.backendExecutionHandle()).isEmpty();
            assertThat(observation.providerBindingPin()).isEmpty();
            assertThat(observation.diagnosticReference()).isEmpty();
        });
        assertThat(service.findExecution(ExecutionAttemptId.of("attempt-missing"))).isEmpty();
    }

    private static void seedExactOwnerFacts() {
        dsl.transaction(configuration -> {
            DSLContext tx = DSL.using(configuration);
            tx.execute("""
                    insert into wf_host_registration (
                        physical_host_id, physical_host_incarnation_id,
                        registered_at, valid_until, active)
                    values ('host-a', 'host-inc-a', ?::timestamptz, ?::timestamptz, true)
                    """, CAPTURED_AT.minusSeconds(60), CAPTURED_AT.plusSeconds(3600));
            tx.execute("""
                    insert into wf_physical_host_connection (
                        physical_host_id, current_incarnation_id, connected, updated_at)
                    values ('host-a', 'host-inc-a', true, ?::timestamptz)
                    """, UPDATED_AT);
            tx.execute("""
                    insert into wf_host_snapshot_generation_authority (
                        physical_host_id, physical_host_incarnation_id, current_generation)
                    values ('host-a', 'host-inc-a', 7)
                    """);
            tx.execute("""
                    insert into wf_host_resource_snapshot (
                        physical_host_id, physical_host_incarnation_id, snapshot_generation,
                        snapshot_fingerprint, captured_at, schema_version,
                        cpu_millicores, memory_bytes, temporary_storage_bytes,
                        safety_headroom_cpu_millicores, safety_headroom_memory_bytes,
                        safety_headroom_temporary_storage_bytes)
                    values ('host-a', 'host-inc-a', 7, ?, ?::timestamptz, 1,
                        32000, 64000, 128000, 1000, 2000, 3000)
                    """, "a".repeat(64), CAPTURED_AT);
            tx.execute("""
                    insert into wf_host_resource_snapshot_device (
                        physical_host_id, physical_host_incarnation_id, snapshot_generation,
                        device_id, vram_bytes, compute_units, encoder_engines, decoder_engines,
                        safety_headroom_vram_bytes, safety_headroom_compute_units,
                        safety_headroom_encoder_engines, safety_headroom_decoder_engines)
                    values ('host-a', 'host-inc-a', 7, 'gpu-a',
                        24000, 80, 2, 3, 1000, 2, 0, 0)
                    """);
            tx.execute("""
                    insert into wf_runtime_registration (
                        worker_runtime_id, worker_runtime_incarnation_id,
                        physical_host_id, physical_host_incarnation_id,
                        registered_at, valid_until, active)
                    values ('runtime-a', 'runtime-inc-a', 'host-a', 'host-inc-a',
                        ?::timestamptz, ?::timestamptz, true)
                    """, CAPTURED_AT.minusSeconds(30), CAPTURED_AT.plusSeconds(1800));
            tx.execute("""
                    insert into wf_worker_runtime_connection (
                        worker_runtime_id, current_incarnation_id, connected, updated_at)
                    values ('runtime-a', 'runtime-inc-a', true, ?::timestamptz)
                    """, UPDATED_AT);
            tx.execute("""
                    insert into wf_execution_backend_selection (
                        selection_id, task_id, backend, placement_authority_scope,
                        active, selected_at, terminal_at)
                    values ('selection-a', ?, 'NATIVE_PULL_WORKER', 'PLATFORM_MANAGED',
                        false, ?::timestamptz, ?::timestamptz)
                    """, "b".repeat(64), CAPTURED_AT, UPDATED_AT);
            tx.execute("""
                    insert into wf_execution_ownership_generation (task_id, generation, created_at)
                    values (?, 3, ?::timestamptz)
                    """, "b".repeat(64), CAPTURED_AT);
            tx.execute("""
                    insert into wf_execution_attempt (
                        attempt_id, task_id, generation, backend, state,
                        backend_selection_id, backend_local_handle_reference,
                        created_at, updated_at)
                    values ('attempt-a', ?, 3, 'NATIVE_PULL_WORKER', 'FAILED',
                        'selection-a', 'lease-a', ?::timestamptz, ?::timestamptz)
                    """, "b".repeat(64), CAPTURED_AT, UPDATED_AT);
            tx.execute("""
                    insert into wf_execution_assignment (
                        assignment_id, task_id, attempt_id, generation,
                        worker_runtime_id, worker_runtime_incarnation_id,
                        physical_host_id, physical_host_incarnation_id, created_at)
                    values ('assignment-a', ?, 'attempt-a', 3,
                        'runtime-a', 'runtime-inc-a', 'host-a', 'host-inc-a', ?::timestamptz)
                    """, "b".repeat(64), CAPTURED_AT);
            tx.execute("""
                    insert into wf_execution_assignment_device (assignment_id, device_id)
                    values ('assignment-a', 'gpu-a')
                    """);
            tx.execute("""
                    insert into wf_reservation (
                        reservation_id, assignment_id, task_id,
                        physical_host_id, physical_host_incarnation_id,
                        kind, state, cpu_millicores, memory_bytes,
                        temporary_storage_bytes, created_at)
                    values ('reservation-a', 'assignment-a', ?, 'host-a', 'host-inc-a',
                        'TASK', 'RECOVERY_HOLD', 8000, 16000, 32000, ?::timestamptz)
                    """, "b".repeat(64), CAPTURED_AT);
            tx.execute("""
                    insert into wf_reservation_device (
                        reservation_id, device_id, vram_bytes, compute_units,
                        encoder_engines, decoder_engines)
                    values ('reservation-a', 'gpu-a', 12000, 40, 1, 1)
                    """);
            tx.execute("""
                    insert into wf_execution_observation (
                        observation_id, attempt_id, generation, observed_state,
                        current_evidence, observed_at)
                    values ('observation-a', 'attempt-a', 3, 'FAILED', true, ?::timestamptz)
                    """, UPDATED_AT);
        });
    }
}
