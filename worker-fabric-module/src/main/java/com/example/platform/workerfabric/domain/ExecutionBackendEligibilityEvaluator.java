package com.example.platform.workerfabric.domain;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import java.util.List;
import java.util.Objects;

/** Pure bounded backend-mechanics legality evaluation downstream of Stage-1 provider legality. */
public final class ExecutionBackendEligibilityEvaluator {

    private ExecutionBackendEligibilityEvaluator() {}

    public static ExecutionBackendEligibilityDecision evaluate(
            ExecutableTask task,
            ProviderBackendExecutionSupport support,
            ExecutionBackend backend) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(support, "support");
        Objects.requireNonNull(backend, "backend");
        if (!task.providerBindingPin().equals(support.providerBindingPin())) {
            throw new IllegalArgumentException(
                    "backend support projection cannot rebind the task ProviderBindingPin");
        }

        if (support.knowledge() == ProviderBackendExecutionSupport.Knowledge.UNKNOWN) {
            return decision(
                    ExecutionBackendEligibilityDecision.Status.UNKNOWN_FAIL_CLOSED,
                    task,
                    backend,
                    List.of(ExecutionBackendEligibilityReason.UNKNOWN_BACKEND_EXECUTION_SUPPORT),
                    null);
        }
        if (!support.supportedBackends().contains(backend)) {
            return decision(
                    ExecutionBackendEligibilityDecision.Status.INELIGIBLE,
                    task,
                    backend,
                    List.of(ExecutionBackendEligibilityReason.BACKEND_EXECUTION_MECHANICS_UNSUPPORTED),
                    null);
        }

        EvaluatorProof proof = new EvaluatorProof(task.id(), task.providerBindingPin(), backend);
        return decision(
                ExecutionBackendEligibilityDecision.Status.ELIGIBLE,
                task,
                backend,
                List.of(),
                proof);
    }

    private static ExecutionBackendEligibilityDecision decision(
            ExecutionBackendEligibilityDecision.Status status,
            ExecutableTask task,
            ExecutionBackend backend,
            List<ExecutionBackendEligibilityReason> reasons,
            ExecutionBackendEligibilityDecision.ExecutionBackendEligibilityProof proof) {
        return new ExecutionBackendEligibilityDecision(
                status, task.id(), task.providerBindingPin(), backend, reasons, proof);
    }

    static final class EvaluatorProof
            implements ExecutionBackendEligibilityDecision.ExecutionBackendEligibilityProof {

        private final ExecutableTaskId executableTaskId;
        private final ProviderBindingPin providerBindingPin;
        private final ExecutionBackend backend;

        private EvaluatorProof(
                ExecutableTaskId executableTaskId,
                ProviderBindingPin providerBindingPin,
                ExecutionBackend backend) {
            this.executableTaskId = executableTaskId;
            this.providerBindingPin = providerBindingPin;
            this.backend = backend;
        }

        @Override
        public ExecutableTaskId executableTaskId() {
            return executableTaskId;
        }

        @Override
        public ProviderBindingPin providerBindingPin() {
            return providerBindingPin;
        }

        @Override
        public ExecutionBackend backend() {
            return backend;
        }
    }
}
