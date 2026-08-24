package com.example.platform.workerfabric.domain;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Typed, fail-closed answer to whether one provider-bound task may use one backend. */
public final class ExecutionBackendEligibilityDecision {

    private final Status status;
    private final ExecutableTaskId executableTaskId;
    private final ProviderBindingPin providerBindingPin;
    private final ExecutionBackend backend;
    private final List<ExecutionBackendEligibilityReason> reasons;
    private final ExecutionBackendEligibilityProof eligibilityProof;

    ExecutionBackendEligibilityDecision(
            Status status,
            ExecutableTaskId executableTaskId,
            ProviderBindingPin providerBindingPin,
            ExecutionBackend backend,
            List<ExecutionBackendEligibilityReason> reasons,
            ExecutionBackendEligibilityProof eligibilityProof) {
        this.status = Objects.requireNonNull(status, "status");
        this.executableTaskId = Objects.requireNonNull(executableTaskId, "executableTaskId");
        this.providerBindingPin = Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        this.backend = Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(reasons, "reasons");
        ArrayList<ExecutionBackendEligibilityReason> canonical = new ArrayList<>(reasons);
        canonical.forEach(reason -> Objects.requireNonNull(reason, "reasons element"));
        canonical.sort(Comparator.naturalOrder());
        for (int index = 1; index < canonical.size(); index++) {
            if (canonical.get(index - 1) == canonical.get(index)) {
                throw new IllegalArgumentException("duplicate backend eligibility reason");
            }
        }
        this.reasons = List.copyOf(canonical);
        this.eligibilityProof = eligibilityProof;

        boolean unknown = this.reasons.contains(
                ExecutionBackendEligibilityReason.UNKNOWN_BACKEND_EXECUTION_SUPPORT);
        if (status == Status.ELIGIBLE && (!this.reasons.isEmpty() || eligibilityProof == null)) {
            throw new IllegalArgumentException("eligible backend decision requires proof and no reasons");
        }
        if (status == Status.INELIGIBLE && (this.reasons.isEmpty() || unknown || eligibilityProof != null)) {
            throw new IllegalArgumentException("ineligible backend decision requires known reasons only");
        }
        if (status == Status.UNKNOWN_FAIL_CLOSED && (!unknown || eligibilityProof != null)) {
            throw new IllegalArgumentException("unknown backend decision must fail closed");
        }
        if (eligibilityProof != null && !eligibilityProof.proves(
                executableTaskId, providerBindingPin, backend)) {
            throw new IllegalArgumentException("backend eligibility proof does not bind exact inputs");
        }
    }

    public Status status() {
        return status;
    }

    public ExecutableTaskId executableTaskId() {
        return executableTaskId;
    }

    public ProviderBindingPin providerBindingPin() {
        return providerBindingPin;
    }

    public ExecutionBackend backend() {
        return backend;
    }

    public List<ExecutionBackendEligibilityReason> reasons() {
        return reasons;
    }

    public boolean eligible() {
        return status == Status.ELIGIBLE;
    }

    ExecutionBackendEligibilityProof requireProof() {
        if (!eligible() || eligibilityProof == null) {
            throw new IllegalStateException("backend selection requires proven eligibility");
        }
        return eligibilityProof;
    }

    public enum Status {
        ELIGIBLE,
        INELIGIBLE,
        UNKNOWN_FAIL_CLOSED
    }

    sealed interface ExecutionBackendEligibilityProof
            permits ExecutionBackendEligibilityEvaluator.EvaluatorProof {

        ExecutableTaskId executableTaskId();

        ProviderBindingPin providerBindingPin();

        ExecutionBackend backend();

        default boolean proves(
                ExecutableTaskId taskId,
                ProviderBindingPin bindingPin,
                ExecutionBackend selectedBackend) {
            return executableTaskId().equals(taskId)
                    && providerBindingPin().equals(bindingPin)
                    && backend() == selectedBackend;
        }
    }
}
