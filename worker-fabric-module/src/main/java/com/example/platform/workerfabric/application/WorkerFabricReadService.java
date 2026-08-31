package com.example.platform.workerfabric.application;

import com.example.platform.workerfabric.domain.DeviceId;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.WorkerRuntimeId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Owner-side application facade; every operation delegates one exact identity read. */
@Service
public class WorkerFabricReadService {

    private final WorkerFabricReadPort.WorkerRuntime workerRuntimeReadPort;
    private final WorkerFabricReadPort.Device deviceReadPort;
    private final WorkerFabricReadPort.Execution executionReadPort;

    public WorkerFabricReadService(
            WorkerFabricReadPort.WorkerRuntime workerRuntimeReadPort,
            WorkerFabricReadPort.Device deviceReadPort,
            WorkerFabricReadPort.Execution executionReadPort) {
        this.workerRuntimeReadPort = Objects.requireNonNull(
                workerRuntimeReadPort, "workerRuntimeReadPort");
        this.deviceReadPort = Objects.requireNonNull(deviceReadPort, "deviceReadPort");
        this.executionReadPort = Objects.requireNonNull(executionReadPort, "executionReadPort");
    }

    public Optional<WorkerRuntimeReadSnapshot> findWorkerRuntime(WorkerRuntimeId workerRuntimeId) {
        return workerRuntimeReadPort.findWorkerRuntime(
                Objects.requireNonNull(workerRuntimeId, "workerRuntimeId"));
    }

    public List<DeviceReadSnapshot> findCurrentDevices(DeviceId deviceId) {
        return List.copyOf(deviceReadPort.findCurrentDevices(
                Objects.requireNonNull(deviceId, "deviceId")));
    }

    public Optional<ExecutionReadSnapshot> findExecution(ExecutionAttemptId executionAttemptId) {
        return executionReadPort.findExecution(
                Objects.requireNonNull(executionAttemptId, "executionAttemptId"));
    }
}
