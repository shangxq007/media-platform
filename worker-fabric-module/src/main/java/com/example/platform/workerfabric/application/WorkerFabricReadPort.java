package com.example.platform.workerfabric.application;

import com.example.platform.workerfabric.domain.DeviceId;
import com.example.platform.workerfabric.domain.ExecutionAttemptId;
import com.example.platform.workerfabric.domain.WorkerRuntimeId;
import java.util.List;
import java.util.Optional;

/** Exact-identity reads of already-durable H1 owner facts; it performs no derivation or mutation. */
public interface WorkerFabricReadPort {

    interface WorkerRuntime {
        Optional<WorkerRuntimeReadSnapshot> findWorkerRuntime(WorkerRuntimeId workerRuntimeId);
    }

    interface Device {
        /**
         * Returns every exact current-generation host scope carrying the DeviceId.
         * A list preserves schema-level identity ambiguity instead of selecting an arbitrary row.
         */
        List<DeviceReadSnapshot> findCurrentDevices(DeviceId deviceId);
    }

    interface Execution {
        Optional<ExecutionReadSnapshot> findExecution(ExecutionAttemptId executionAttemptId);
    }
}
