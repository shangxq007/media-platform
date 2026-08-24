package com.example.platform.workerfabric.infrastructure;

import com.example.platform.workerfabric.domain.DeviceId;
import com.example.platform.workerfabric.domain.DeviceResourceCapacity;
import com.example.platform.workerfabric.domain.DeviceResourceReservation;
import com.example.platform.workerfabric.domain.HostResourceSnapshot;
import com.example.platform.workerfabric.domain.HostResourceSnapshotGeneration;
import com.example.platform.workerfabric.domain.PhysicalHostId;
import com.example.platform.workerfabric.domain.PhysicalHostIncarnationId;
import com.example.platform.workerfabric.domain.ReservedResources;
import com.example.platform.workerfabric.domain.WorkerFabricRegistrationBoundary;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** PostgreSQL registration and current host-snapshot authority for Native Pull workers. */
@Repository
public class JooqWorkerFabricRegistrationBoundary
        implements WorkerFabricRegistrationBoundary {

    private final DSLContext dsl;

    public JooqWorkerFabricRegistrationBoundary(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    @Override
    public void registerHost(HostRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        dsl.transaction(configuration -> {
            DSLContext tx = DSL.using(configuration);
            HostResourceSnapshot snapshot = registration.hostResourceSnapshot();
            String hostId = registration.physicalHostId().value();
            String hostIncarnationId = registration.physicalHostIncarnationId().value();

            Record currentHost = tx.fetchOne(
                    """
                    select physical_host_incarnation_id from wf_host_registration
                     where physical_host_id = ? and active
                     for update
                    """,
                    hostId);
            if (currentHost != null) {
                String currentIncarnationId =
                        currentHost.get("physical_host_incarnation_id", String.class);
                if (!hostIncarnationId.equals(currentIncarnationId)) {
                    JooqOwnershipFencing.fenceHostIncarnation(
                            tx,
                            hostId,
                            currentIncarnationId,
                            registration.registeredAt());
                    tx.execute(
                            """
                            update wf_runtime_registration set active = false
                             where physical_host_id = ? and physical_host_incarnation_id = ? and active
                            """,
                            hostId,
                            currentIncarnationId);
                }
            }

            tx.execute(
                    "update wf_host_registration set active = false where physical_host_id = ? and active",
                    hostId);
            tx.execute(
                    """
                    insert into wf_host_registration (
                        physical_host_id, physical_host_incarnation_id,
                        registered_at, valid_until, active)
                    values (?, ?, cast(? as timestamptz), cast(? as timestamptz), true)
                    on conflict (physical_host_id, physical_host_incarnation_id) do update
                       set registered_at = excluded.registered_at,
                           valid_until = excluded.valid_until,
                           active = true
                    """,
                    hostId,
                    hostIncarnationId,
                    databaseTime(registration.registeredAt()),
                    databaseTime(registration.validUntil()));
            int generationAdvanced = tx.execute(
                    """
                    insert into wf_host_snapshot_generation_authority (
                        physical_host_id, physical_host_incarnation_id, current_generation)
                    values (?, ?, ?)
                    on conflict (physical_host_id, physical_host_incarnation_id) do update
                       set current_generation = excluded.current_generation
                     where wf_host_snapshot_generation_authority.current_generation
                           < excluded.current_generation
                    """,
                    hostId,
                    hostIncarnationId,
                    snapshot.snapshotGeneration().value());
            if (generationAdvanced != 1) {
                throw new IllegalArgumentException(
                        "host resource snapshot generation must be strictly greater than durable authority");
            }
            tx.execute(
                    """
                    insert into wf_host_resource_snapshot (
                        physical_host_id, physical_host_incarnation_id, snapshot_generation,
                        snapshot_fingerprint, captured_at, schema_version,
                        cpu_millicores, memory_bytes, temporary_storage_bytes,
                        safety_headroom_cpu_millicores, safety_headroom_memory_bytes,
                        safety_headroom_temporary_storage_bytes)
                    values (?, ?, ?, ?, cast(? as timestamptz), ?, ?, ?, ?, ?, ?, ?)
                    """,
                    hostId,
                    hostIncarnationId,
                    snapshot.snapshotGeneration().value(),
                    RequestWorkContextFingerprint.ofSnapshot(snapshot),
                    databaseTime(snapshot.capturedAt()),
                    snapshot.schemaVersion().value(),
                    snapshot.staticCapacity().cpu().millicores(),
                    snapshot.staticCapacity().memory().bytes(),
                    snapshot.staticCapacity().temporaryStorage().bytes(),
                    registration.safetyHeadroom().resources().cpuMillicores(),
                    registration.safetyHeadroom().resources().memoryBytes(),
                    registration.safetyHeadroom().resources().temporaryStorageBytes());
            snapshot.staticCapacity().deviceResources().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.comparing(DeviceId::value)))
                    .forEach(entry -> insertDevice(
                            tx,
                            snapshot,
                            entry.getValue(),
                            registration.safetyHeadroom().resources()));
            tx.execute(
                    """
                    insert into wf_physical_host_connection (
                        physical_host_id, current_incarnation_id, connected, updated_at)
                    values (?, ?, true, cast(? as timestamptz))
                    on conflict (physical_host_id) do update
                       set current_incarnation_id = excluded.current_incarnation_id,
                           connected = true,
                           updated_at = excluded.updated_at
                    """,
                    hostId,
                    hostIncarnationId,
                    databaseTime(registration.registeredAt()));
        });
    }

    @Override
    public void registerRuntime(RuntimeRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        dsl.transaction(configuration -> {
            DSLContext tx = DSL.using(configuration);
            Record host = tx.fetchOne(
                    """
                    select valid_until from wf_host_registration
                     where physical_host_id = ? and physical_host_incarnation_id = ? and active
                     for update
                    """,
                    registration.physicalHostId().value(),
                    registration.physicalHostIncarnationId().value());
            if (host == null
                    || !instant(host, "valid_until").isAfter(registration.registeredAt())
                    || instant(host, "valid_until").isBefore(registration.validUntil())) {
                throw new IllegalStateException(
                        "runtime registration requires a current host registration covering its validity");
            }
            Record currentRuntime = tx.fetchOne(
                    """
                    select worker_runtime_incarnation_id from wf_runtime_registration
                     where worker_runtime_id = ? and active
                     for update
                    """,
                    registration.workerRuntimeId().value());
            if (currentRuntime != null) {
                String currentIncarnationId =
                        currentRuntime.get("worker_runtime_incarnation_id", String.class);
                if (!registration.workerRuntimeIncarnationId().value().equals(currentIncarnationId)) {
                    JooqOwnershipFencing.fenceRuntimeIncarnation(
                            tx,
                            registration.workerRuntimeId().value(),
                            currentIncarnationId,
                            registration.registeredAt());
                }
            }
            tx.execute(
                    "update wf_runtime_registration set active = false where worker_runtime_id = ? and active",
                    registration.workerRuntimeId().value());
            tx.execute(
                    """
                    insert into wf_runtime_registration (
                        worker_runtime_id, worker_runtime_incarnation_id,
                        physical_host_id, physical_host_incarnation_id,
                        registered_at, valid_until, active)
                    values (?, ?, ?, ?, cast(? as timestamptz), cast(? as timestamptz), true)
                    on conflict (worker_runtime_id, worker_runtime_incarnation_id) do update
                       set physical_host_id = excluded.physical_host_id,
                           physical_host_incarnation_id = excluded.physical_host_incarnation_id,
                           registered_at = excluded.registered_at,
                           valid_until = excluded.valid_until,
                           active = true
                    """,
                    registration.workerRuntimeId().value(),
                    registration.workerRuntimeIncarnationId().value(),
                    registration.physicalHostId().value(),
                    registration.physicalHostIncarnationId().value(),
                    databaseTime(registration.registeredAt()),
                    databaseTime(registration.validUntil()));
            tx.execute(
                    """
                    insert into wf_worker_runtime_connection (
                        worker_runtime_id, current_incarnation_id, connected, updated_at)
                    values (?, ?, true, cast(? as timestamptz))
                    on conflict (worker_runtime_id) do update
                       set current_incarnation_id = excluded.current_incarnation_id,
                           connected = true,
                           updated_at = excluded.updated_at
                    """,
                    registration.workerRuntimeId().value(),
                    registration.workerRuntimeIncarnationId().value(),
                    databaseTime(registration.registeredAt()));
        });
    }

    @Override
    public Optional<HostResourceSnapshotGeneration> currentSnapshotGeneration(
            PhysicalHostId physicalHostId,
            PhysicalHostIncarnationId physicalHostIncarnationId) {
        Objects.requireNonNull(physicalHostId, "physicalHostId");
        Objects.requireNonNull(physicalHostIncarnationId, "physicalHostIncarnationId");
        Record row = dsl.fetchOne(
                """
                select current_generation from wf_host_snapshot_generation_authority
                 where physical_host_id = ? and physical_host_incarnation_id = ?
                """,
                physicalHostId.value(),
                physicalHostIncarnationId.value());
        return row == null
                ? Optional.empty()
                : Optional.of(new HostResourceSnapshotGeneration(
                        row.get("current_generation", Long.class)));
    }

    private static void insertDevice(
            DSLContext tx,
            HostResourceSnapshot snapshot,
            DeviceResourceCapacity device,
            ReservedResources headroom) {
        DeviceResourceReservation deviceHeadroom = headroom.deviceResources().getOrDefault(
                device.deviceId(),
                new DeviceResourceReservation(device.deviceId(), 0, 0, 0, 0));
        tx.execute(
                """
                insert into wf_host_resource_snapshot_device (
                    physical_host_id, physical_host_incarnation_id, snapshot_generation,
                    device_id, vram_bytes, compute_units, encoder_engines, decoder_engines,
                    safety_headroom_vram_bytes, safety_headroom_compute_units,
                    safety_headroom_encoder_engines, safety_headroom_decoder_engines)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                snapshot.physicalHostId().value(),
                snapshot.physicalHostIncarnationId().value(),
                snapshot.snapshotGeneration().value(),
                device.deviceId().value(),
                device.vramBytes(),
                device.computeUnits(),
                device.encoderEngines(),
                device.decoderEngines(),
                deviceHeadroom.vramBytes(),
                deviceHeadroom.computeUnits(),
                deviceHeadroom.encoderEngines(),
                deviceHeadroom.decoderEngines());
    }

    private static Instant instant(Record record, String field) {
        return record.get(field, OffsetDateTime.class).toInstant();
    }

    private static OffsetDateTime databaseTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
