package com.example.platform.workerfabric.domain;

import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.taskgraph.ExecutableTaskId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Typed Stage-2 runtime eligibility result; UNKNOWN always fails closed. */
public final class RuntimeEligibilityDecision {

    private final Status status;
    private final ExecutableTaskId executableTaskId;
    private final ProviderBindingPin providerBindingPin;
    private final List<RuntimeEligibilityReason> reasons;

    RuntimeEligibilityDecision(
            Status status,
            ExecutableTaskId executableTaskId,
            ProviderBindingPin providerBindingPin,
            List<RuntimeEligibilityReason> reasons) {
        this.status = Objects.requireNonNull(status, "status");
        this.executableTaskId = Objects.requireNonNull(executableTaskId, "executableTaskId");
        this.providerBindingPin = Objects.requireNonNull(providerBindingPin, "providerBindingPin");
        Objects.requireNonNull(reasons, "reasons");
        ArrayList<RuntimeEligibilityReason> canonical = new ArrayList<>(reasons);
        canonical.forEach(reason -> Objects.requireNonNull(reason, "reasons element"));
        canonical.sort(Comparator.naturalOrder());
        for (int index = 1; index < canonical.size(); index++) {
            if (canonical.get(index - 1) == canonical.get(index)) {
                throw new IllegalArgumentException("duplicate runtime eligibility reason");
            }
        }
        this.reasons = List.copyOf(canonical);

        boolean unknown = this.reasons.stream().anyMatch(RuntimeEligibilityReason::unknownEvidence);
        if (status == Status.ELIGIBLE && !this.reasons.isEmpty()) {
            throw new IllegalArgumentException("eligible runtime decision cannot contain reasons");
        }
        if (status == Status.INELIGIBLE && (this.reasons.isEmpty() || unknown)) {
            throw new IllegalArgumentException("ineligible runtime decision requires known reasons only");
        }
        if (status == Status.UNKNOWN_FAIL_CLOSED && !unknown) {
            throw new IllegalArgumentException("unknown runtime decision must contain an unknown reason");
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

    public List<RuntimeEligibilityReason> reasons() {
        return reasons;
    }

    public boolean eligible() {
        return status == Status.ELIGIBLE;
    }

    public enum Status {
        ELIGIBLE,
        INELIGIBLE,
        UNKNOWN_FAIL_CLOSED
    }
}
