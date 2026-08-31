package com.example.platform.workerfabric.infrastructure;

import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.workerfabric.application.AssignmentReadSnapshot;
import com.example.platform.workerfabric.application.DeviceReadSnapshot;
import com.example.platform.workerfabric.application.ExecutionObservationReadSnapshot;
import com.example.platform.workerfabric.application.ExecutionReadSnapshot;
import com.example.platform.workerfabric.application.WorkerFabricReadPort;
import com.example.platform.workerfabric.application.WorkerRuntimeReadSnapshot;
import com.example.platform.workerfabric.domain.AvailabilityState;
import com.example.platform.workerfabric.domain.BackendExecutionHandle;
import com.example.platform.workerfabric.domain.CueJobId;
import com.example.platform.workerfabric.domain.DeviceId;
import com.example.platform.workerfabric.domain.DeviceResourceCapacity;
import com.example.platform.workerfabric.domain.DeviceResourceReservation;
import com.example.platform.workerfabric.domain.ExecutionAssignment;
import com.example.platform.workerfabric.domain.ExecutionAssignmentId;
import com.example.platform.workerfabric.domain.ExecutionAttempt;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.ExecutionAttemptState;
import com.example.platform.workerfabric.domain.ExecutionBackend;
import com.example.platform.workerfabric.domain.ExecutionOwnershipGeneration;
import com.example.platform.workerfabric.domain.HostResourceSnapshotGeneration;
import com.example.platform.workerfabric.domain.HostResourceSnapshotSchemaVersion;
import com.example.platform.workerfabric.domain.LeaseId;
import com.example.platform.workerfabric.domain.NativeWorkerBackendExecutionHandle;
import com.example.platform.workerfabric.domain.ObservationId;
import com.example.platform.workerfabric.domain.ObservedExecutionState;
import com.example.platform.workerfabric.domain.OpenCueBackendExecutionHandle;
import com.example.platform.workerfabric.domain.PhysicalHostId;
import com.example.platform.workerfabric.domain.PhysicalHostIncarnationId;
import com.example.platform.workerfabric.domain.RemoteExecutionId;
import com.example.platform.workerfabric.domain.RemoteProviderExecutionHandle;
import com.example.platform.workerfabric.domain.Reservation;
import com.example.platform.workerfabric.domain.ReservationId;
import com.example.platform.workerfabric.domain.ReservationKind;
import com.example.platform.workerfabric.domain.ReservationState;
import com.example.platform.workerfabric.domain.ReservedResources;
import com.example.platform.workerfabric.domain.WorkerRuntimeAvailability;
import com.example.platform.workerfabric.domain.WorkerRuntimeId;
import com.example.platform.workerfabric.domain.WorkerRuntimeIncarnationId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/** Read-only jOOQ adapter over the existing worker-fabric PostgreSQL owner authority. */
@Repository
public class JooqWorkerFabricReadAdapter implements
        WorkerFabricReadPort.WorkerRuntime,
        WorkerFabricReadPort.Device,
        WorkerFabricReadPort.Execution {

    private final DSLContext dsl;

    public JooqWorkerFabricReadAdapter(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    @Override
    public Optional<WorkerRuntimeReadSnapshot> findWorkerRuntime(WorkerRuntimeId workerRuntimeId) {
        Objects.requireNonNull(workerRuntimeId, "workerRuntimeId");
        Record row = dsl.fetchOne(
                """
                select c.current_incarnation_id, c.connected, c.updated_at,
                       r.physical_host_id, r.physical_host_incarnation_id,
                       r.registered_at, r.valid_until, g.current_generation
                  from wf_worker_runtime_connection c
                  join wf_runtime_registration r
                    on r.worker_runtime_id = c.worker_runtime_id
                   and r.worker_runtime_incarnation_id = c.current_incarnation_id
                  left join wf_host_snapshot_generation_authority g
                    on g.physical_host_id = r.physical_host_id
                   and g.physical_host_incarnation_id = r.physical_host_incarnation_id
                 where c.worker_runtime_id = ?
                """,
                workerRuntimeId.value());
        if (row == null) {
            return Optional.empty();
        }
        WorkerRuntimeIncarnationId incarnationId = new WorkerRuntimeIncarnationId(
                row.get("current_incarnation_id", String.class));
        PhysicalHostId hostId = new PhysicalHostId(row.get("physical_host_id", String.class));
        PhysicalHostIncarnationId hostIncarnationId = new PhysicalHostIncarnationId(
                row.get("physical_host_incarnation_id", String.class));
        AvailabilityState availabilityState = row.get("connected", Boolean.class)
                ? AvailabilityState.REACHABLE
                : AvailabilityState.UNREACHABLE;
        Optional<HostResourceSnapshotGeneration> generation = Optional.ofNullable(
                        row.get("current_generation", Long.class))
                .map(HostResourceSnapshotGeneration::new);
        return Optional.of(new WorkerRuntimeReadSnapshot(
                workerRuntimeId,
                incarnationId,
                hostId,
                hostIncarnationId,
                instant(row, "registered_at"),
                instant(row, "valid_until"),
                Optional.empty(),
                new WorkerRuntimeAvailability(workerRuntimeId, incarnationId, availabilityState),
                instant(row, "updated_at"),
                Optional.empty(),
                Optional.empty(),
                generation,
                loadAssignmentsForRuntime(workerRuntimeId, incarnationId)));
    }

    @Override
    public List<DeviceReadSnapshot> findCurrentDevices(DeviceId deviceId) {
        Objects.requireNonNull(deviceId, "deviceId");
        List<DeviceReadSnapshot> result = new ArrayList<>();
        for (Record row : dsl.fetch(
                """
                select d.physical_host_id, d.physical_host_incarnation_id,
                       d.snapshot_generation, d.vram_bytes, d.compute_units,
                       d.encoder_engines, d.decoder_engines,
                       s.captured_at, s.schema_version
                  from wf_host_snapshot_generation_authority g
                  join wf_host_registration h
                    on h.physical_host_id = g.physical_host_id
                   and h.physical_host_incarnation_id = g.physical_host_incarnation_id
                   and h.active
                  join wf_host_resource_snapshot s
                    on s.physical_host_id = g.physical_host_id
                   and s.physical_host_incarnation_id = g.physical_host_incarnation_id
                   and s.snapshot_generation = g.current_generation
                  join wf_host_resource_snapshot_device d
                    on d.physical_host_id = s.physical_host_id
                   and d.physical_host_incarnation_id = s.physical_host_incarnation_id
                   and d.snapshot_generation = s.snapshot_generation
                 where d.device_id = ?
                 order by d.physical_host_id, d.physical_host_incarnation_id
                """,
                deviceId.value())) {
            PhysicalHostId hostId = new PhysicalHostId(row.get("physical_host_id", String.class));
            PhysicalHostIncarnationId hostIncarnationId = new PhysicalHostIncarnationId(
                    row.get("physical_host_incarnation_id", String.class));
            result.add(new DeviceReadSnapshot(
                    deviceId,
                    hostId,
                    hostIncarnationId,
                    new HostResourceSnapshotGeneration(row.get("snapshot_generation", Long.class)),
                    instant(row, "captured_at"),
                    new HostResourceSnapshotSchemaVersion(row.get("schema_version", Integer.class)),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    new DeviceResourceCapacity(
                            deviceId,
                            row.get("vram_bytes", Long.class),
                            row.get("compute_units", Long.class),
                            row.get("encoder_engines", Long.class),
                            row.get("decoder_engines", Long.class)),
                    Optional.empty(),
                    Optional.empty(),
                    loadAssignmentsForDevice(deviceId, hostId, hostIncarnationId)));
        }
        return List.copyOf(result);
    }

    @Override
    public Optional<ExecutionReadSnapshot> findExecution(ExecutionAttemptId executionAttemptId) {
        Objects.requireNonNull(executionAttemptId, "executionAttemptId");
        Record attemptRow = dsl.fetchOne(
                """
                select attempt_id, task_id, generation, backend, state,
                       backend_local_handle_reference, created_at, updated_at
                  from wf_execution_attempt
                 where attempt_id = ?
                """,
                executionAttemptId.value());
        if (attemptRow == null) {
            return Optional.empty();
        }
        ExecutionAttempt attempt = toAttempt(attemptRow);
        Record assignmentRow = dsl.fetchOne(
                """
                select assignment_id, task_id, attempt_id, generation,
                       worker_runtime_id, worker_runtime_incarnation_id,
                       physical_host_id, physical_host_incarnation_id, created_at
                  from wf_execution_assignment
                 where attempt_id = ?
                """,
                executionAttemptId.value());
        Optional<AssignmentReadSnapshot> assignment = Optional.ofNullable(assignmentRow)
                .map(this::toAssignmentSnapshot);
        List<ExecutionObservationReadSnapshot> observations = dsl.fetch(
                        """
                        select observation_id, attempt_id, generation, observed_state,
                               current_evidence, observed_at
                          from wf_execution_observation
                         where attempt_id = ?
                         order by observed_at, observation_id
                        """,
                        executionAttemptId.value()).stream()
                .map(this::toObservationSnapshot)
                .toList();
        return Optional.of(new ExecutionReadSnapshot(
                attempt,
                instant(attemptRow, "created_at"),
                instant(attemptRow, "updated_at"),
                Optional.empty(),
                assignment,
                observations));
    }

    private List<AssignmentReadSnapshot> loadAssignmentsForRuntime(
            WorkerRuntimeId workerRuntimeId,
            WorkerRuntimeIncarnationId incarnationId) {
        return dsl.fetch(
                        """
                        select assignment_id, task_id, attempt_id, generation,
                               worker_runtime_id, worker_runtime_incarnation_id,
                               physical_host_id, physical_host_incarnation_id, created_at
                          from wf_execution_assignment
                         where worker_runtime_id = ? and worker_runtime_incarnation_id = ?
                         order by created_at, assignment_id
                        """,
                        workerRuntimeId.value(),
                        incarnationId.value()).stream()
                .map(this::toAssignmentSnapshot)
                .toList();
    }

    private List<AssignmentReadSnapshot> loadAssignmentsForDevice(
            DeviceId deviceId,
            PhysicalHostId hostId,
            PhysicalHostIncarnationId hostIncarnationId) {
        return dsl.fetch(
                        """
                        select a.assignment_id, a.task_id, a.attempt_id, a.generation,
                               a.worker_runtime_id, a.worker_runtime_incarnation_id,
                               a.physical_host_id, a.physical_host_incarnation_id, a.created_at
                          from wf_execution_assignment a
                          join wf_execution_assignment_device d
                            on d.assignment_id = a.assignment_id
                         where d.device_id = ?
                           and a.physical_host_id = ?
                           and a.physical_host_incarnation_id = ?
                         order by a.created_at, a.assignment_id
                        """,
                        deviceId.value(),
                        hostId.value(),
                        hostIncarnationId.value()).stream()
                .map(this::toAssignmentSnapshot)
                .toList();
    }

    private AssignmentReadSnapshot toAssignmentSnapshot(Record row) {
        ExecutionAssignmentId assignmentId = new ExecutionAssignmentId(
                row.get("assignment_id", String.class));
        List<Reservation> reservations = loadReservations(assignmentId);
        Set<DeviceId> deviceIds = dsl.fetch(
                        """
                        select device_id from wf_execution_assignment_device
                         where assignment_id = ? order by device_id
                        """,
                        assignmentId.value()).stream()
                .map(value -> new DeviceId(value.get("device_id", String.class)))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<ReservationId> reservationIds = reservations.stream()
                .map(Reservation::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        ExecutionAssignment assignment = new ExecutionAssignment(
                assignmentId,
                new ExecutableTaskId(row.get("task_id", String.class)),
                new ExecutionAttemptId(row.get("attempt_id", String.class)),
                new ExecutionOwnershipGeneration(row.get("generation", Long.class)),
                new WorkerRuntimeId(row.get("worker_runtime_id", String.class)),
                new WorkerRuntimeIncarnationId(
                        row.get("worker_runtime_incarnation_id", String.class)),
                new PhysicalHostId(row.get("physical_host_id", String.class)),
                new PhysicalHostIncarnationId(
                        row.get("physical_host_incarnation_id", String.class)),
                deviceIds,
                reservationIds);
        return new AssignmentReadSnapshot(assignment, instant(row, "created_at"), reservations);
    }

    private List<Reservation> loadReservations(ExecutionAssignmentId assignmentId) {
        List<Reservation> result = new ArrayList<>();
        for (Record row : dsl.fetch(
                """
                select reservation_id, physical_host_id, kind, state,
                       cpu_millicores, memory_bytes, temporary_storage_bytes
                  from wf_reservation
                 where assignment_id = ? order by reservation_id
                """,
                assignmentId.value())) {
            ReservationId reservationId = new ReservationId(
                    row.get("reservation_id", String.class));
            Map<DeviceId, DeviceResourceReservation> deviceResources = new LinkedHashMap<>();
            for (Record device : dsl.fetch(
                    """
                    select device_id, vram_bytes, compute_units,
                           encoder_engines, decoder_engines
                      from wf_reservation_device
                     where reservation_id = ? order by device_id
                    """,
                    reservationId.value())) {
                DeviceId deviceId = new DeviceId(device.get("device_id", String.class));
                deviceResources.put(deviceId, new DeviceResourceReservation(
                        deviceId,
                        device.get("vram_bytes", Long.class),
                        device.get("compute_units", Long.class),
                        device.get("encoder_engines", Long.class),
                        device.get("decoder_engines", Long.class)));
            }
            result.add(new Reservation(
                    reservationId,
                    new PhysicalHostId(row.get("physical_host_id", String.class)),
                    ReservationKind.valueOf(row.get("kind", String.class)),
                    new ReservedResources(
                            row.get("cpu_millicores", Long.class),
                            row.get("memory_bytes", Long.class),
                            row.get("temporary_storage_bytes", Long.class),
                            deviceResources),
                    ReservationState.valueOf(row.get("state", String.class))));
        }
        return List.copyOf(result);
    }

    private ExecutionAttempt toAttempt(Record row) {
        ExecutionAttemptId attemptId = new ExecutionAttemptId(row.get("attempt_id", String.class));
        ExecutionOwnershipGeneration generation = new ExecutionOwnershipGeneration(
                row.get("generation", Long.class));
        ExecutionBackend backend = ExecutionBackend.valueOf(row.get("backend", String.class));
        Optional<BackendExecutionHandle> handle = Optional.ofNullable(
                        row.get("backend_local_handle_reference", String.class))
                .map(reference -> switch (backend) {
                    case NATIVE_PULL_WORKER -> NativeWorkerBackendExecutionHandle.forLease(
                            attemptId, generation, new LeaseId(reference));
                    case OPEN_CUE_FARM -> OpenCueBackendExecutionHandle.forSubmission(
                            attemptId, generation, new CueJobId(reference));
                    case REMOTE_PROVIDER -> RemoteProviderExecutionHandle.forRemoteExecution(
                            attemptId, generation, new RemoteExecutionId(reference));
                });
        return new ExecutionAttempt(
                attemptId,
                new ExecutableTaskId(row.get("task_id", String.class)),
                generation,
                backend,
                ExecutionAttemptState.valueOf(row.get("state", String.class)),
                handle);
    }

    private ExecutionObservationReadSnapshot toObservationSnapshot(Record row) {
        return new ExecutionObservationReadSnapshot(
                new ObservationId(row.get("observation_id", String.class)),
                new ExecutionAttemptId(row.get("attempt_id", String.class)),
                new ExecutionOwnershipGeneration(row.get("generation", Long.class)),
                ObservedExecutionState.valueOf(row.get("observed_state", String.class)),
                row.get("current_evidence", Boolean.class),
                instant(row, "observed_at"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }

    private static Instant instant(Record row, String field) {
        return row.get(field, OffsetDateTime.class).toInstant();
    }
}
