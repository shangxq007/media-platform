package com.example.platform.workerfabric.domain;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Task C fixtures stay on the immutable media-execution boundary exposed to worker-fabric. */
final class TaskCTestFixture {

    static final Instant NOW = Instant.parse("2026-08-24T12:00:00Z");
    static final DeviceId DEVICE_ID = DeviceId.of("gpu-0");

    private TaskCTestFixture() {}

    static RuntimeFixture runtime(String suffix) {
        return runtime(suffix, NOW, "runtime-inc-" + suffix, "runtime-inc-" + suffix);
    }

    static RuntimeFixture staleRuntime(String suffix) {
        return runtime(
                suffix,
                NOW.minus(Duration.ofMinutes(6)),
                "runtime-inc-" + suffix,
                "runtime-inc-" + suffix);
    }

    static RuntimeFixture runtimeWithIncarnationMismatch(String suffix) {
        return runtime(suffix, NOW, "runtime-inc-old-" + suffix, "runtime-inc-current-" + suffix);
    }

    private static RuntimeFixture runtime(
            String suffix,
            Instant capturedAt,
            String requestRuntimeIncarnation,
            String bindingRuntimeIncarnation) {
        PhysicalHostId hostId = PhysicalHostId.of("host-" + suffix);
        PhysicalHostIncarnationId hostIncarnation =
                PhysicalHostIncarnationId.of("host-inc-" + suffix);
        WorkerRuntimeId runtimeId = WorkerRuntimeId.of("runtime-" + suffix);
        WorkerRuntimeIncarnationId requestIncarnation =
                WorkerRuntimeIncarnationId.of(requestRuntimeIncarnation);
        WorkerRuntimeIncarnationId bindingIncarnation =
                WorkerRuntimeIncarnationId.of(bindingRuntimeIncarnation);
        DeviceDescriptor device = new DeviceDescriptor(
                DEVICE_ID,
                DeviceKind.GPU,
                DeviceVendor.of("vendor"),
                DeviceModel.of("model"));
        PhysicalHostDescriptor host = new PhysicalHostDescriptor(
                hostId,
                HostLocation.of("region-a"),
                TrustZoneId.of("trusted"),
                List.of(device));
        CapacitySnapshot capacity = new CapacitySnapshot(
                CpuCapacity.ofMillicores(8_000),
                MemoryCapacity.ofBytes(64_000),
                TemporaryStorageCapacity.ofBytes(100_000),
                Map.of(DEVICE_ID, new DeviceResourceCapacity(DEVICE_ID, 16_000, 100, 4, 4)));
        HostResourceSnapshot snapshot = new HostResourceSnapshot(
                hostId,
                hostIncarnation,
                HostResourceSnapshotGeneration.first(),
                capturedAt,
                HostResourceSnapshotSchemaVersion.CURRENT,
                capacity,
                new ObservedUsage(
                        new ObservedCpuUsage(0.1),
                        new ObservedMemoryUsage(1_000),
                        new ObservedTemporaryStorageUsage(1_000),
                        Map.of(DEVICE_ID, new ObservedDeviceUsage(
                                DEVICE_ID, 0.1, 1_000, 0.1, 0.1))),
                Optional.of(new WorkerRuntimeReporterRef(
                        runtimeId, requestIncarnation, hostId, hostIncarnation)));
        SchedulableCapacity schedulableCapacity = new SchedulableCapacity(
                hostId,
                hostIncarnation,
                SchedulableCapacityDisposition.AVAILABLE,
                capacity.cpu(),
                capacity.memory(),
                capacity.temporaryStorage(),
                capacity.deviceResources());
        WorkerRuntimeAvailability runtimeAvailability = new WorkerRuntimeAvailability(
                runtimeId, requestIncarnation, AvailabilityState.REACHABLE);
        RequestWork requestWork = new RequestWork(
                RequestWorkId.of("request-" + suffix),
                runtimeId,
                requestIncarnation,
                hostId,
                hostIncarnation,
                snapshot,
                runtimeAvailability,
                Map.of(DEVICE_ID, new DeviceAvailability(DEVICE_ID, AvailabilityState.REACHABLE)),
                RuntimeEnvironmentAvailability.AVAILABLE,
                SandboxRuntimeAvailability.AVAILABLE,
                Optional.empty(),
                Optional.empty());
        RequestWorkValidationContext context = new RequestWorkValidationContext(
                WorkerRuntimeDescriptor.local(
                        runtimeId, RuntimeLifecycleKind.EPHEMERAL_TASK, hostId),
                new WorkerRuntimeAvailability(
                        runtimeId, bindingIncarnation, AvailabilityState.REACHABLE),
                new LocalWorkerRuntimeIncarnationBinding(
                        runtimeId, bindingIncarnation, hostId, hostIncarnation),
                host,
                new PhysicalHostAvailability(
                        hostId, hostIncarnation, AvailabilityState.REACHABLE),
                snapshot,
                new HostResourceSnapshotFreshnessPolicy(
                        Duration.ofMinutes(5), HostResourceSnapshotSchemaVersion.CURRENT),
                NOW,
                schedulableCapacity);
        return new RuntimeFixture(requestWork, context);
    }

    static CandidateFixture candidate(long identity) {
        return candidate(
                identity,
                PendingNativeWorkCandidate.ClaimState.PENDING,
                ReservationFeasibility.FEASIBLE,
                Set.of(ExecutionBackend.NATIVE_PULL_WORKER));
    }

    static CandidateFixture candidate(long identity, RuntimeResourceDemand resourceDemand) {
        return candidate(
                identity,
                PendingNativeWorkCandidate.ClaimState.PENDING,
                ReservationFeasibility.FEASIBLE,
                Set.of(ExecutionBackend.NATIVE_PULL_WORKER),
                resourceDemand);
    }

    static CandidateFixture candidate(
            long identity,
            PendingNativeWorkCandidate.ClaimState claimState,
            ReservationFeasibility reservationFeasibility,
            Set<ExecutionBackend> supportedBackends) {
        return candidate(
                identity,
                claimState,
                reservationFeasibility,
                supportedBackends,
                new RuntimeResourceDemand(1_000, 1_000, 1_000, Map.of()));
    }

    private static CandidateFixture candidate(
            long identity,
            PendingNativeWorkCandidate.ClaimState claimState,
            ReservationFeasibility reservationFeasibility,
            Set<ExecutionBackend> supportedBackends,
            RuntimeResourceDemand resourceDemand) {
        ProviderBindingPin binding = mock(ProviderBindingPin.class, "binding-" + identity);
        ProviderCandidate provider = mock(ProviderCandidate.class, "provider-" + identity);
        when(provider.bindingPin()).thenReturn(binding);
        ExecutableTask task = mock(ExecutableTask.class, "task-" + identity);
        when(task.id()).thenReturn(new ExecutableTaskId("%064x".formatted(identity)));
        when(task.providerBindingPin()).thenReturn(binding);
        when(task.memberships()).thenReturn(List.of());
        ProviderBoundExecutableTaskGraph graph = mock(
                ProviderBoundExecutableTaskGraph.class, "graph-" + identity);
        when(graph.tasks()).thenReturn(List.of(task));

        PendingNativeWorkCandidate candidate = new PendingNativeWorkCandidate(
                graph,
                task,
                provider,
                ProviderBackendExecutionSupport.declared(binding, supportedBackends),
                claimState,
                resourceDemand,
                reservationFeasibility,
                SandboxRuntimeRequirement.NOT_REQUIRED,
                Optional.empty(),
                ProviderProbeRequirement.NOT_REQUIRED,
                Optional.empty());
        return new CandidateFixture(candidate, task, graph, provider);
    }

    record RuntimeFixture(RequestWork requestWork, RequestWorkValidationContext context) {

        RequestWork requestWithId(String value) {
            return new RequestWork(
                    RequestWorkId.of(value),
                    requestWork.workerRuntimeId(),
                    requestWork.workerRuntimeIncarnationId(),
                    requestWork.physicalHostId(),
                    requestWork.physicalHostIncarnationId(),
                    requestWork.hostResourceSnapshot(),
                    requestWork.workerRuntimeAvailability(),
                    requestWork.deviceAvailability(),
                    requestWork.runtimeEnvironmentAvailability(),
                    requestWork.sandboxRuntimeAvailability(),
                    requestWork.runtimeSupportAdvertisement(),
                    requestWork.workerDerivedSchedulableCapacity());
        }
    }

    record CandidateFixture(
            PendingNativeWorkCandidate candidate,
            ExecutableTask task,
            ProviderBoundExecutableTaskGraph graph,
            ProviderCandidate provider) {}

    static final class RecordingGrantBoundary implements AtomicAssignmentGrantBoundary {

        private final Map<RequestWorkId, StoredResolution> resolutions = new HashMap<>();
        private final Set<ExecutableTaskId> claimedTasks = new HashSet<>();
        private int grantCalls;
        private AtomicAssignmentGrantCommand lastCommand;

        @Override
        public Optional<RequestWorkFailureReason> validateRegistration(RequestWork requestWork) {
            return Optional.empty();
        }

        @Override
        public synchronized Optional<RequestWorkResult> findResolution(RequestWork requestWork) {
            StoredResolution stored = resolutions.get(requestWork.requestWorkId());
            if (stored == null) {
                return Optional.empty();
            }
            if (!stored.requestWork().equals(requestWork)) {
                return Optional.of(new RequestWorkResult.Rejected(
                        requestWork.requestWorkId(),
                        RequestWorkFailureReason.REQUEST_ID_REUSED_WITH_DIFFERENT_CONTEXT));
            }
            return Optional.of(stored.result());
        }

        @Override
        public synchronized RequestWorkResult resolveTerminal(
                RequestWork requestWork,
                RequestWorkResult terminalResult) {
            Optional<RequestWorkResult> prior = findResolution(requestWork);
            if (prior.isPresent()) {
                return prior.orElseThrow();
            }
            resolutions.put(
                    requestWork.requestWorkId(),
                    new StoredResolution(requestWork, terminalResult));
            return terminalResult;
        }

        @Override
        public synchronized RequestWorkResult tryGrant(AtomicAssignmentGrantCommand command) {
            Optional<RequestWorkResult> prior = findResolution(command.requestWork());
            if (prior.isPresent()) {
                return prior.orElseThrow();
            }
            grantCalls++;
            lastCommand = command;
            RequestWorkResult result;
            if (!claimedTasks.add(command.executableTask().id())) {
                result = new RequestWorkResult.NoWork(
                        command.requestWork().requestWorkId());
            } else {
                result = new RequestWorkResult.Granted(
                        command.requestWork().requestWorkId(),
                        new TestGrant(
                                command.requestWork().requestWorkId(),
                                command.executableTask().id()));
            }
            resolutions.put(
                    command.requestWork().requestWorkId(),
                    new StoredResolution(command.requestWork(), result));
            return result;
        }

        synchronized int grantCalls() {
            return grantCalls;
        }

        synchronized AtomicAssignmentGrantCommand lastCommand() {
            return lastCommand;
        }

        private record StoredResolution(RequestWork requestWork, RequestWorkResult result) {}

        private record TestGrant(
                RequestWorkId requestWorkId,
                ExecutableTaskId executableTaskId) implements AssignmentGrantReference {}
    }
}
