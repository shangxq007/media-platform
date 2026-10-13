package com.example.platform.workerfabric.infrastructure;

import com.example.platform.workerfabric.domain.DeviceId;
import com.example.platform.workerfabric.domain.DeviceKind;
import com.example.platform.workerfabric.domain.DeviceResourceCapacity;
import com.example.platform.workerfabric.domain.DeviceResourceReservation;
import com.example.platform.workerfabric.domain.HostResourceSnapshot;
import com.example.platform.workerfabric.domain.HostLocation;
import com.example.platform.workerfabric.domain.HostResourceSnapshotGeneration;
import com.example.platform.workerfabric.domain.ObservedUsage;
import com.example.platform.workerfabric.domain.ObservedDeviceUsage;
import com.example.platform.workerfabric.domain.RuntimeEnvironmentAvailability;
import com.example.platform.workerfabric.domain.SandboxRuntimeAvailability;
import com.example.platform.workerfabric.domain.WorkerReadinessEvidence;
import com.example.platform.workerfabric.domain.PhysicalHostId;
import com.example.platform.workerfabric.domain.PhysicalHostIncarnationId;
import com.example.platform.workerfabric.domain.ReservedResources;
import com.example.platform.workerfabric.domain.PhysicalHostDescriptor;
import com.example.platform.workerfabric.domain.TrustZoneId;
import com.example.platform.workerfabric.domain.WorkerFabricRegistrationBoundary;
import com.example.platform.workerfabric.domain.WorkerRuntimeId;
import com.example.platform.workerfabric.domain.WorkerRuntimeIncarnationId;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
                        safety_headroom_temporary_storage_bytes,
                        observed_cpu_utilization_ratio, observed_memory_used_bytes,
                        observed_temporary_storage_used_bytes)
                    values (?, ?, ?, ?, cast(? as timestamptz), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                    registration.safetyHeadroom().resources().temporaryStorageBytes(),
                    // A-2: observed usage is part of the published snapshot and must be written at
                    // insert time (the published row is immutable by DB trigger).
                    snapshot.observedUsage().cpu().utilizationRatio(),
                    snapshot.observedUsage().memory().usedBytes(),
                    snapshot.observedUsage().temporaryStorage().usedBytes());
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

    /** A-2: durable static host descriptor (location, trust zone, device inventory). */
    @Override
    public void registerHostDescriptor(PhysicalHostDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        dsl.transaction(configuration -> {
            DSLContext tx = DSL.using(configuration);
            String hostId = descriptor.id().value();
            tx.execute(
                    """
                    insert into wf_physical_host_descriptor (
                        physical_host_id, host_location, trust_zone_id, captured_at)
                    values (?, ?, ?, now())
                    on conflict (physical_host_id) do update
                       set host_location = excluded.host_location,
                           trust_zone_id = excluded.trust_zone_id,
                           captured_at = excluded.captured_at
                    """,
                    hostId,
                    descriptor.location().value(),
                    descriptor.trustZoneId().value());
            tx.execute(
                    "delete from wf_physical_host_descriptor_device where physical_host_id = ?",
                    hostId);
            descriptor.devices().stream()
                    .sorted(Comparator.comparing(device -> device.id().value()))
                    .forEach(device -> tx.execute(
                            """
                            insert into wf_physical_host_descriptor_device (
                                physical_host_id, device_id, device_kind, device_vendor, device_model)
                            values (?, ?, ?, ?, ?)
                            """,
                            hostId,
                            device.id().value(),
                            device.kind().name(),
                            device.vendor().value(),
                            device.model().value()));
        });
    }

    @Override
    public Optional<PersistedHostDescriptor> findHostDescriptor(PhysicalHostId physicalHostId) {
        Objects.requireNonNull(physicalHostId, "physicalHostId");
        Record row = dsl.fetchOne(
                """
                select host_location, trust_zone_id, captured_at
                  from wf_physical_host_descriptor
                 where physical_host_id = ?
                """,
                physicalHostId.value());
        if (row == null) {
            return Optional.empty();
        }
        List<PersistedDescriptorDevice> devices = new ArrayList<>();
        dsl.fetch(
                """
                select device_id, device_kind, device_vendor, device_model
                  from wf_physical_host_descriptor_device
                 where physical_host_id = ?
                 order by device_id
                """,
                physicalHostId.value()).forEach(device -> devices.add(
                        new PersistedDescriptorDevice(
                                device.get("device_id", String.class),
                                DeviceKind.valueOf(device.get("device_kind", String.class)),
                                device.get("device_vendor", String.class),
                                device.get("device_model", String.class))));
        return Optional.of(new PersistedHostDescriptor(
                physicalHostId,
                HostLocation.of(row.get("host_location", String.class)),
                TrustZoneId.of(row.get("trust_zone_id", String.class)),
                instant(row, "captured_at"),
                List.copyOf(devices)));
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
        ObservedDeviceUsage observed = snapshot.observedUsage().deviceUsage().get(device.deviceId());
        tx.execute(
                """
                insert into wf_host_resource_snapshot_device (
                    physical_host_id, physical_host_incarnation_id, snapshot_generation,
                    device_id, vram_bytes, compute_units, encoder_engines, decoder_engines,
                    safety_headroom_vram_bytes, safety_headroom_compute_units,
                    safety_headroom_encoder_engines, safety_headroom_decoder_engines,
                    observed_compute_utilization_ratio, observed_vram_used_bytes,
                    observed_encoder_utilization_ratio, observed_decoder_utilization_ratio)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                deviceHeadroom.decoderEngines(),
                // A-2: per-device observed usage is written with the row; a device without an
                // observation persists NULLs (never zero usage).
                observed == null ? null : observed.computeUtilizationRatio(),
                observed == null ? null : observed.vramUsedBytes(),
                observed == null ? null : observed.encoderUtilizationRatio(),
                observed == null ? null : observed.decoderUtilizationRatio());
    }

    @Override
    public Optional<PersistedHostRegistration> findActiveHostRegistration(
            PhysicalHostId physicalHostId) {
        Objects.requireNonNull(physicalHostId, "physicalHostId");
        Record row = dsl.fetchOne(
                """
                select physical_host_incarnation_id, registered_at, valid_until
                  from wf_host_registration
                 where physical_host_id = ? and active
                """,
                physicalHostId.value());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new PersistedHostRegistration(
                physicalHostId,
                new PhysicalHostIncarnationId(
                        row.get("physical_host_incarnation_id", String.class)),
                instant(row, "registered_at"),
                instant(row, "valid_until")));
    }

    @Override
    public Optional<PersistedRuntimeRegistration> findActiveRuntimeRegistration(
            WorkerRuntimeId workerRuntimeId) {
        Objects.requireNonNull(workerRuntimeId, "workerRuntimeId");
        Record row = dsl.fetchOne(
                """
                select worker_runtime_incarnation_id, physical_host_id,
                       physical_host_incarnation_id, registered_at, valid_until
                  from wf_runtime_registration
                 where worker_runtime_id = ? and active
                """,
                workerRuntimeId.value());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new PersistedRuntimeRegistration(
                workerRuntimeId,
                new WorkerRuntimeIncarnationId(
                        row.get("worker_runtime_incarnation_id", String.class)),
                new PhysicalHostId(row.get("physical_host_id", String.class)),
                new PhysicalHostIncarnationId(
                        row.get("physical_host_incarnation_id", String.class)),
                instant(row, "registered_at"),
                instant(row, "valid_until")));
    }

    @Override
    public Optional<PersistedHostRegistration> findActiveHostRegistration() {
        Record row = dsl.fetchOne(
                """
                select physical_host_id, physical_host_incarnation_id, registered_at, valid_until
                  from wf_host_registration
                 where active
                 order by registered_at desc
                 limit 1
                """);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new PersistedHostRegistration(
                new PhysicalHostId(row.get("physical_host_id", String.class)),
                new PhysicalHostIncarnationId(
                        row.get("physical_host_incarnation_id", String.class)),
                instant(row, "registered_at"),
                instant(row, "valid_until")));
    }

    @Override
    public Optional<PersistedRuntimeRegistration> findActiveRuntimeRegistration() {
        Record row = dsl.fetchOne(
                """
                select worker_runtime_id, worker_runtime_incarnation_id, physical_host_id,
                       physical_host_incarnation_id, registered_at, valid_until
                  from wf_runtime_registration
                 where active
                 order by registered_at desc
                 limit 1
                """);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new PersistedRuntimeRegistration(
                new WorkerRuntimeId(row.get("worker_runtime_id", String.class)),
                new WorkerRuntimeIncarnationId(
                        row.get("worker_runtime_incarnation_id", String.class)),
                new PhysicalHostId(row.get("physical_host_id", String.class)),
                new PhysicalHostIncarnationId(
                        row.get("physical_host_incarnation_id", String.class)),
                instant(row, "registered_at"),
                instant(row, "valid_until")));
    }

    @Override
    public Optional<PersistedHostSnapshot> findHostSnapshot(
            PhysicalHostId physicalHostId,
            PhysicalHostIncarnationId physicalHostIncarnationId,
            HostResourceSnapshotGeneration snapshotGeneration) {
        Objects.requireNonNull(physicalHostId, "physicalHostId");
        Objects.requireNonNull(physicalHostIncarnationId, "physicalHostIncarnationId");
        Objects.requireNonNull(snapshotGeneration, "snapshotGeneration");
        Record row = dsl.fetchOne(
                """
                select snapshot_fingerprint, captured_at, schema_version,
                       cpu_millicores, memory_bytes, temporary_storage_bytes,
                       safety_headroom_cpu_millicores, safety_headroom_memory_bytes,
                       safety_headroom_temporary_storage_bytes,
                       observed_cpu_utilization_ratio, observed_memory_used_bytes,
                       observed_temporary_storage_used_bytes
                  from wf_host_resource_snapshot
                 where physical_host_id = ? and physical_host_incarnation_id = ?
                   and snapshot_generation = ?
                """,
                physicalHostId.value(),
                physicalHostIncarnationId.value(),
                snapshotGeneration.value());
        if (row == null) {
            return Optional.empty();
        }
        List<PersistedSnapshotDevice> devices = new ArrayList<>();
        dsl.fetch(
                """
                select device_id, vram_bytes, compute_units, encoder_engines, decoder_engines,
                       safety_headroom_vram_bytes, safety_headroom_compute_units,
                       safety_headroom_encoder_engines, safety_headroom_decoder_engines
                  from wf_host_resource_snapshot_device
                 where physical_host_id = ? and physical_host_incarnation_id = ?
                   and snapshot_generation = ?
                 order by device_id
                """,
                physicalHostId.value(),
                physicalHostIncarnationId.value(),
                snapshotGeneration.value()).forEach(device -> devices.add(
                        new PersistedSnapshotDevice(
                                device.get("device_id", String.class),
                                device.get("vram_bytes", Long.class),
                                device.get("compute_units", Long.class),
                                device.get("encoder_engines", Long.class),
                                device.get("decoder_engines", Long.class),
                                device.get("safety_headroom_vram_bytes", Long.class),
                                device.get("safety_headroom_compute_units", Long.class),
                                device.get("safety_headroom_encoder_engines", Long.class),
                                device.get("safety_headroom_decoder_engines", Long.class))));
        return Optional.of(new PersistedHostSnapshot(
                physicalHostId,
                physicalHostIncarnationId,
                snapshotGeneration,
                row.get("snapshot_fingerprint", String.class),
                instant(row, "captured_at"),
                row.get("schema_version", Integer.class),
                row.get("cpu_millicores", Long.class),
                row.get("memory_bytes", Long.class),
                row.get("temporary_storage_bytes", Long.class),
                row.get("safety_headroom_cpu_millicores", Long.class),
                row.get("safety_headroom_memory_bytes", Long.class),
                row.get("safety_headroom_temporary_storage_bytes", Long.class),
                List.copyOf(devices),
                observedUsage(
                        row,
                        physicalHostId,
                        physicalHostIncarnationId,
                        snapshotGeneration)));
    }

    /**
     * A-2: the observed-usage projection is present only when the persisted host row carries the
     * complete host-level observation; a partial/absent observation is {@link Optional#empty()} and
     * callers must treat it as unavailable (never as zero usage).
     */
    private Optional<PersistedObservedUsage> observedUsage(
            Record row,
            PhysicalHostId physicalHostId,
            PhysicalHostIncarnationId physicalHostIncarnationId,
            HostResourceSnapshotGeneration snapshotGeneration) {
        Double cpu = row.get("observed_cpu_utilization_ratio", Double.class);
        Long memory = row.get("observed_memory_used_bytes", Long.class);
        Long temporary = row.get("observed_temporary_storage_used_bytes", Long.class);
        if (cpu == null || memory == null || temporary == null) {
            return Optional.empty();
        }
        List<PersistedObservedDeviceUsage> devices = new ArrayList<>();
        dsl.fetch(
                """
                select device_id, observed_compute_utilization_ratio, observed_vram_used_bytes,
                       observed_encoder_utilization_ratio, observed_decoder_utilization_ratio
                  from wf_host_resource_snapshot_device
                 where physical_host_id = ? and physical_host_incarnation_id = ?
                   and snapshot_generation = ?
                   and observed_compute_utilization_ratio is not null
                 order by device_id
                """,
                physicalHostId.value(),
                physicalHostIncarnationId.value(),
                snapshotGeneration.value()).forEach(device -> devices.add(
                        new PersistedObservedDeviceUsage(
                                device.get("device_id", String.class),
                                device.get("observed_compute_utilization_ratio", Double.class),
                                device.get("observed_vram_used_bytes", Long.class),
                                device.get("observed_encoder_utilization_ratio", Double.class),
                                device.get("observed_decoder_utilization_ratio", Double.class))));
        return Optional.of(new PersistedObservedUsage(cpu, memory, temporary, List.copyOf(devices)));
    }

    private static Instant instant(Record record, String field) {
        return record.get(field, OffsetDateTime.class).toInstant();
    }

    /**
     * WORKER_READINESS_EVIDENCE_V1: upserts the incarnation-bound readiness record. Environment and
     * sandbox readiness are stored as two independent columns; neither endorses the other.
     */
    @Override
    public void saveReadiness(WorkerReadinessEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        dsl.execute(
                """
                insert into wf_host_readiness (
                    physical_host_id, physical_host_incarnation_id, worker_runtime_id,
                    worker_runtime_incarnation_id, environment_readiness, sandbox_readiness,
                    snapshot_generation, captured_at, valid_until)
                values (?, ?, ?, ?, ?, ?, ?, cast(? as timestamptz), cast(? as timestamptz))
                on conflict (physical_host_id, physical_host_incarnation_id) do update
                   set worker_runtime_id = excluded.worker_runtime_id,
                       worker_runtime_incarnation_id = excluded.worker_runtime_incarnation_id,
                       environment_readiness = excluded.environment_readiness,
                       sandbox_readiness = excluded.sandbox_readiness,
                       snapshot_generation = excluded.snapshot_generation,
                       captured_at = excluded.captured_at,
                       valid_until = excluded.valid_until
                """,
                evidence.physicalHostId().value(),
                evidence.physicalHostIncarnationId().value(),
                evidence.workerRuntimeId().value(),
                evidence.workerRuntimeIncarnationId().value(),
                evidence.environmentReadiness().name(),
                evidence.sandboxReadiness().name(),
                evidence.snapshotGeneration().value(),
                databaseTime(evidence.capturedAt()),
                databaseTime(evidence.validUntil()));
    }

    /**
     * Reads non-expired readiness for the exact host incarnation. Expiry is evaluated against database
     * time, so a stale record reads as absent; a missing or expired record is {@link Optional#empty()}
     * and the caller must fail closed.
     */
    @Override
    public Optional<WorkerReadinessEvidence> findReadiness(
            PhysicalHostId physicalHostId, PhysicalHostIncarnationId physicalHostIncarnationId) {
        Objects.requireNonNull(physicalHostId, "physicalHostId");
        Objects.requireNonNull(physicalHostIncarnationId, "physicalHostIncarnationId");
        Record row = dsl.fetchOne(
                """
                select worker_runtime_id, worker_runtime_incarnation_id, environment_readiness,
                       sandbox_readiness, snapshot_generation, captured_at, valid_until
                  from wf_host_readiness
                 where physical_host_id = ? and physical_host_incarnation_id = ?
                   and valid_until > now()
                """,
                physicalHostId.value(),
                physicalHostIncarnationId.value());
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new WorkerReadinessEvidence(
                physicalHostId,
                physicalHostIncarnationId,
                WorkerRuntimeId.of(row.get("worker_runtime_id", String.class)),
                WorkerRuntimeIncarnationId.of(row.get("worker_runtime_incarnation_id", String.class)),
                RuntimeEnvironmentAvailability.valueOf(row.get("environment_readiness", String.class)),
                SandboxRuntimeAvailability.valueOf(row.get("sandbox_readiness", String.class)),
                new HostResourceSnapshotGeneration(row.get("snapshot_generation", Long.class)),
                instant(row, "captured_at"),
                instant(row, "valid_until")));
    }

    private static OffsetDateTime databaseTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
