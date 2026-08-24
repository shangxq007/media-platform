package com.example.platform.workerfabric.infrastructure;

import com.example.platform.workerfabric.domain.CapacitySnapshot;
import com.example.platform.workerfabric.domain.DeviceAvailability;
import com.example.platform.workerfabric.domain.DeviceId;
import com.example.platform.workerfabric.domain.DeviceResourceCapacity;
import com.example.platform.workerfabric.domain.HostResourceSnapshot;
import com.example.platform.workerfabric.domain.ObservedDeviceUsage;
import com.example.platform.workerfabric.domain.RequestWork;
import com.example.platform.workerfabric.domain.SchedulableCapacity;
import com.example.platform.workerfabric.domain.WorkerRuntimeReporterRef;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.Map;

/** Explicit structural fingerprint for durable RequestWork idempotency context comparison. */
final class RequestWorkContextFingerprint {

    private static final int SCHEMA_VERSION = 1;

    private RequestWorkContextFingerprint() {}

    static String of(RequestWork request) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(SCHEMA_VERSION);
                text(out, request.requestWorkId().value());
                text(out, request.workerRuntimeId().value());
                text(out, request.workerRuntimeIncarnationId().value());
                text(out, request.physicalHostId().value());
                text(out, request.physicalHostIncarnationId().value());
                snapshot(out, request.hostResourceSnapshot());
                text(out, request.workerRuntimeAvailability().workerRuntimeId().value());
                text(out, request.workerRuntimeAvailability().incarnationId().value());
                text(out, request.workerRuntimeAvailability().state().name());
                deviceAvailability(out, request.deviceAvailability());
                text(out, request.runtimeEnvironmentAvailability().name());
                text(out, request.sandboxRuntimeAvailability().name());
                out.writeBoolean(request.workerDerivedSchedulableCapacity().isPresent());
                if (request.workerDerivedSchedulableCapacity().isPresent()) {
                    schedulableCapacity(
                            out, request.workerDerivedSchedulableCapacity().orElseThrow());
                }
            }
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    static String ofSnapshot(HostResourceSnapshot snapshot) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(SCHEMA_VERSION);
                snapshot(out, snapshot);
            }
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private static void snapshot(DataOutputStream out, HostResourceSnapshot snapshot)
            throws IOException {
        text(out, snapshot.physicalHostId().value());
        text(out, snapshot.physicalHostIncarnationId().value());
        out.writeLong(snapshot.snapshotGeneration().value());
        text(out, snapshot.capturedAt().toString());
        out.writeInt(snapshot.schemaVersion().value());
        capacity(out, snapshot.staticCapacity());
        out.writeLong(Double.doubleToLongBits(snapshot.observedUsage().cpu().utilizationRatio()));
        out.writeLong(snapshot.observedUsage().memory().usedBytes());
        out.writeLong(snapshot.observedUsage().temporaryStorage().usedBytes());
        out.writeInt(snapshot.observedUsage().deviceUsage().size());
        for (Map.Entry<DeviceId, ObservedDeviceUsage> entry : snapshot.observedUsage()
                .deviceUsage().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(DeviceId::value)))
                .toList()) {
            text(out, entry.getKey().value());
            ObservedDeviceUsage usage = entry.getValue();
            out.writeLong(Double.doubleToLongBits(usage.computeUtilizationRatio()));
            out.writeLong(usage.vramUsedBytes());
            out.writeLong(Double.doubleToLongBits(usage.encoderUtilizationRatio()));
            out.writeLong(Double.doubleToLongBits(usage.decoderUtilizationRatio()));
        }
        out.writeBoolean(snapshot.reportingRuntime().isPresent());
        if (snapshot.reportingRuntime().isPresent()) {
            WorkerRuntimeReporterRef reporter = snapshot.reportingRuntime().orElseThrow();
            text(out, reporter.workerRuntimeId().value());
            text(out, reporter.workerRuntimeIncarnationId().value());
            text(out, reporter.physicalHostId().value());
            text(out, reporter.physicalHostIncarnationId().value());
        }
    }

    private static void capacity(DataOutputStream out, CapacitySnapshot capacity)
            throws IOException {
        out.writeLong(capacity.cpu().millicores());
        out.writeLong(capacity.memory().bytes());
        out.writeLong(capacity.temporaryStorage().bytes());
        deviceCapacity(out, capacity.deviceResources());
    }

    private static void schedulableCapacity(DataOutputStream out, SchedulableCapacity capacity)
            throws IOException {
        text(out, capacity.physicalHostId().value());
        text(out, capacity.physicalHostIncarnationId().value());
        text(out, capacity.disposition().name());
        out.writeLong(capacity.cpu().millicores());
        out.writeLong(capacity.memory().bytes());
        out.writeLong(capacity.temporaryStorage().bytes());
        deviceCapacity(out, capacity.deviceResources());
    }

    private static void deviceCapacity(
            DataOutputStream out, Map<DeviceId, DeviceResourceCapacity> capacities)
            throws IOException {
        out.writeInt(capacities.size());
        for (Map.Entry<DeviceId, DeviceResourceCapacity> entry : capacities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(DeviceId::value)))
                .toList()) {
            text(out, entry.getKey().value());
            DeviceResourceCapacity capacity = entry.getValue();
            out.writeLong(capacity.vramBytes());
            out.writeLong(capacity.computeUnits());
            out.writeLong(capacity.encoderEngines());
            out.writeLong(capacity.decoderEngines());
        }
    }

    private static void deviceAvailability(
            DataOutputStream out, Map<DeviceId, DeviceAvailability> availabilities)
            throws IOException {
        out.writeInt(availabilities.size());
        for (Map.Entry<DeviceId, DeviceAvailability> entry : availabilities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(DeviceId::value)))
                .toList()) {
            text(out, entry.getKey().value());
            text(out, entry.getValue().state().name());
        }
    }

    private static void text(DataOutputStream out, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(encoded.length);
        out.write(encoded);
    }
}
