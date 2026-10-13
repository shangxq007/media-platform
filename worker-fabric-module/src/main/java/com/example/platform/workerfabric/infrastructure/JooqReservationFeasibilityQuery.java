package com.example.platform.workerfabric.infrastructure;

import com.example.platform.workerfabric.domain.DeviceId;
import com.example.platform.workerfabric.domain.HostResourceSnapshotGeneration;
import com.example.platform.workerfabric.domain.PhysicalHostId;
import com.example.platform.workerfabric.domain.PhysicalHostIncarnationId;
import com.example.platform.workerfabric.domain.ReservationFeasibility;
import com.example.platform.workerfabric.domain.ReservationFeasibilityQuery;
import com.example.platform.workerfabric.domain.RuntimeResourceDemand;
import java.util.Objects;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/**
 * P1-1A.5: read-only Jooq implementation of {@link ReservationFeasibilityQuery}.
 *
 * <p>It reads exactly the durable evidence the final in-transaction check
 * ({@code JooqAtomicAssignmentGrantBoundary.reservationFeasibleInsideTransaction}) reads — the current
 * host resource snapshot + safety headroom (authoritative for the requested snapshot generation), the
 * summed {@code wf_reservation} rows in state {@code ACTIVE|RECOVERY_HOLD}, and, per required device,
 * the snapshot device capacity + headroom and the summed device reservations — but with <b>no
 * {@code FOR UPDATE} locks and no writes</b>. The pure resource comparison mirrors the final check's
 * {@code fits(...)} arithmetic so a {@code FEASIBLE} result never under-reports a resource constraint
 * (it is a necessary condition of a successful Grant).
 *
 * <p><b>Fail closed.</b> Any missing/incomplete/unreadable required input returns
 * {@link ReservationFeasibility#UNKNOWN}; nothing is defaulted.
 */
@Repository
public class JooqReservationFeasibilityQuery implements ReservationFeasibilityQuery {

    private final DSLContext dsl;

    public JooqReservationFeasibilityQuery(DSLContext dsl) {
        this.dsl = Objects.requireNonNull(dsl, "dsl");
    }

    @Override
    public ReservationFeasibility assess(
            PhysicalHostId physicalHostId,
            PhysicalHostIncarnationId physicalHostIncarnationId,
            HostResourceSnapshotGeneration snapshotGeneration,
            RuntimeResourceDemand demand) {
        Objects.requireNonNull(physicalHostId, "physicalHostId");
        Objects.requireNonNull(physicalHostIncarnationId, "physicalHostIncarnationId");
        Objects.requireNonNull(snapshotGeneration, "snapshotGeneration");
        Objects.requireNonNull(demand, "demand");
        try {
            Record capacity = dsl.fetchOne(
                    """
                    select s.cpu_millicores, s.memory_bytes, s.temporary_storage_bytes,
                           s.safety_headroom_cpu_millicores, s.safety_headroom_memory_bytes,
                           s.safety_headroom_temporary_storage_bytes
                      from wf_host_resource_snapshot s
                      join wf_host_snapshot_generation_authority a
                        on a.physical_host_id = s.physical_host_id
                       and a.physical_host_incarnation_id = s.physical_host_incarnation_id
                       and a.current_generation = s.snapshot_generation
                     where s.physical_host_id = ? and s.physical_host_incarnation_id = ?
                       and s.snapshot_generation = ?
                    """,
                    physicalHostId.value(),
                    physicalHostIncarnationId.value(),
                    snapshotGeneration.value());
            if (capacity == null) {
                return ReservationFeasibility.UNKNOWN;
            }
            Long capacityCpu = capacity.get("cpu_millicores", Long.class);
            Long capacityMemory = capacity.get("memory_bytes", Long.class);
            Long capacityTemporary = capacity.get("temporary_storage_bytes", Long.class);
            Long headroomCpu = capacity.get("safety_headroom_cpu_millicores", Long.class);
            Long headroomMemory = capacity.get("safety_headroom_memory_bytes", Long.class);
            Long headroomTemporary =
                    capacity.get("safety_headroom_temporary_storage_bytes", Long.class);
            if (capacityCpu == null || capacityMemory == null || capacityTemporary == null
                    || headroomCpu == null || headroomMemory == null || headroomTemporary == null) {
                return ReservationFeasibility.UNKNOWN;
            }

            Record reserved = dsl.fetchOne(
                    """
                    select coalesce(sum(cpu_millicores), 0)::bigint as cpu_millicores,
                           coalesce(sum(memory_bytes), 0)::bigint as memory_bytes,
                           coalesce(sum(temporary_storage_bytes), 0)::bigint
                               as temporary_storage_bytes
                      from wf_reservation
                     where physical_host_id = ? and physical_host_incarnation_id = ?
                       and state in ('ACTIVE', 'RECOVERY_HOLD')
                    """,
                    physicalHostId.value(),
                    physicalHostIncarnationId.value());
            if (reserved == null) {
                return ReservationFeasibility.UNKNOWN;
            }
            Long reservedCpu = reserved.get("cpu_millicores", Long.class);
            Long reservedMemory = reserved.get("memory_bytes", Long.class);
            Long reservedTemporary = reserved.get("temporary_storage_bytes", Long.class);
            if (reservedCpu == null || reservedMemory == null || reservedTemporary == null) {
                return ReservationFeasibility.UNKNOWN;
            }

            if (!fits(reservedCpu, demand.cpuMillicores(), capacityCpu, headroomCpu)
                    || !fits(reservedMemory, demand.memoryBytes(), capacityMemory, headroomMemory)
                    || !fits(reservedTemporary, demand.temporaryStorageBytes(),
                            capacityTemporary, headroomTemporary)) {
                return ReservationFeasibility.CONFLICT;
            }

            for (var entry : demand.deviceDemands().entrySet()) {
                DeviceId deviceId = entry.getKey();
                RuntimeResourceDemand.DeviceDemand deviceDemand = entry.getValue();
                Record deviceCapacity = dsl.fetchOne(
                        """
                        select vram_bytes, compute_units, encoder_engines, decoder_engines,
                               safety_headroom_vram_bytes, safety_headroom_compute_units,
                               safety_headroom_encoder_engines, safety_headroom_decoder_engines
                          from wf_host_resource_snapshot_device
                         where physical_host_id = ? and physical_host_incarnation_id = ?
                           and snapshot_generation = ? and device_id = ?
                        """,
                        physicalHostId.value(),
                        physicalHostIncarnationId.value(),
                        snapshotGeneration.value(),
                        deviceId.value());
                if (deviceCapacity == null) {
                    return ReservationFeasibility.UNKNOWN;
                }
                Long vram = deviceCapacity.get("vram_bytes", Long.class);
                Long compute = deviceCapacity.get("compute_units", Long.class);
                Long encoders = deviceCapacity.get("encoder_engines", Long.class);
                Long decoders = deviceCapacity.get("decoder_engines", Long.class);
                Long headroomVram = deviceCapacity.get("safety_headroom_vram_bytes", Long.class);
                Long headroomCompute =
                        deviceCapacity.get("safety_headroom_compute_units", Long.class);
                Long headroomEncoders =
                        deviceCapacity.get("safety_headroom_encoder_engines", Long.class);
                Long headroomDecoders =
                        deviceCapacity.get("safety_headroom_decoder_engines", Long.class);
                if (vram == null || compute == null || encoders == null || decoders == null
                        || headroomVram == null || headroomCompute == null
                        || headroomEncoders == null || headroomDecoders == null) {
                    return ReservationFeasibility.UNKNOWN;
                }

                Record deviceReserved = dsl.fetchOne(
                        """
                        select coalesce(sum(rd.vram_bytes), 0)::bigint as vram_bytes,
                               coalesce(sum(rd.compute_units), 0)::bigint as compute_units,
                               coalesce(sum(rd.encoder_engines), 0)::bigint as encoder_engines,
                               coalesce(sum(rd.decoder_engines), 0)::bigint as decoder_engines
                          from wf_reservation_device rd
                          join wf_reservation r on r.reservation_id = rd.reservation_id
                         where r.physical_host_id = ? and r.physical_host_incarnation_id = ?
                           and r.state in ('ACTIVE', 'RECOVERY_HOLD') and rd.device_id = ?
                        """,
                        physicalHostId.value(),
                        physicalHostIncarnationId.value(),
                        deviceId.value());
                if (deviceReserved == null) {
                    return ReservationFeasibility.UNKNOWN;
                }
                Long reservedVram = deviceReserved.get("vram_bytes", Long.class);
                Long reservedCompute = deviceReserved.get("compute_units", Long.class);
                Long reservedEncoders = deviceReserved.get("encoder_engines", Long.class);
                Long reservedDecoders = deviceReserved.get("decoder_engines", Long.class);
                if (reservedVram == null || reservedCompute == null
                        || reservedEncoders == null || reservedDecoders == null) {
                    return ReservationFeasibility.UNKNOWN;
                }

                if (!fits(reservedVram, deviceDemand.vramBytes(), vram, headroomVram)
                        || !fits(reservedCompute, deviceDemand.computeUnits(),
                                compute, headroomCompute)
                        || !fits(reservedEncoders, deviceDemand.encoderEngines(),
                                encoders, headroomEncoders)
                        || !fits(reservedDecoders, deviceDemand.decoderEngines(),
                                decoders, headroomDecoders)) {
                    return ReservationFeasibility.CONFLICT;
                }
            }
            return ReservationFeasibility.FEASIBLE;
        } catch (RuntimeException readFailure) {
            // Fail closed: a read failure can never be optimistically treated as feasible.
            return ReservationFeasibility.UNKNOWN;
        }
    }

    /**
     * Mirrors {@code JooqAtomicAssignmentGrantBoundary.fits(...)}: the demand must fit within
     * {@code capacity - safetyHeadroom - reserved}. Any negative/overflow-inconsistent input fails
     * closed here (mapped to CONFLICT by the caller is impossible because inputs are validated
     * non-null; a false result is treated as a resource conflict).
     */
    private static boolean fits(
            long reserved,
            long requested,
            long capacity,
            long safetyHeadroom) {
        return reserved >= 0
                && requested >= 0
                && safetyHeadroom >= 0
                && capacity >= safetyHeadroom
                && capacity - safetyHeadroom >= reserved
                && requested <= capacity - safetyHeadroom - reserved;
    }
}
